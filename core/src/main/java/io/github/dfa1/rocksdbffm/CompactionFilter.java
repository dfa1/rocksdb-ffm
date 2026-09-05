package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;

/// A compaction filter, attached to a database or column family via
/// [Options#setCompactionFilter(CompactionFilter)]. During every background or manual
/// compaction, RocksDB calls [FilterFn#filter(int, MemorySegment, MemorySegment)] once per
/// `Put`-originated key-value pair it rewrites, letting Java decide whether to keep it, drop it,
/// or replace its value — the mechanism behind custom retention/TTL/redaction policies that run
/// for free as data is naturally rewritten, with no extra read/write pass of their own.
///
/// Merge operands are never passed to this filter — RocksDB's C++ base class routes them through
/// a separate `FilterMergeOperand` method that `rocksdb_compactionfilter_create` does not expose;
/// filter merge semantics inside the [MergeOperator] instead.
///
/// **Thread safety:** this project has no wrapper for RocksDB's `CompactionFilterFactory` yet
/// (see `docs/c-api-gaps.md`), so a filter attached via [Options#setCompactionFilter] is shared
/// by every concurrent background compaction. `fn` must be thread-safe, exactly like
/// [MergeOperator.FullMergeFn].
///
/// **Snapshot caveat:** RocksDB does not guarantee that an open snapshot keeps seeing data a
/// compaction filter has dropped or rewritten — once a rewritten table file is installed, the
/// old value can disappear even from a snapshot taken before compaction ran. There is no way to
/// opt out of this in the pinned RocksDB version: see [#setIgnoreSnapshots(boolean)] for why
/// passing `false` there does not restore the old snapshot-preserving behavior. If a use case
/// cannot tolerate this, `fn` itself must avoid dropping/changing keys that need to stay stable
/// under an open snapshot.
///
/// ```
/// CompactionFilter.FilterFn dropExpired = (level, key, existingValue) ->
///         isExpired(existingValue) ? CompactionFilter.FilterDecision.remove()
///                                   : CompactionFilter.FilterDecision.keep();
/// try (var filter = CompactionFilter.create("drop-expired", dropExpired);
///      var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilter(filter);
///      var db = RocksDB.openReadWrite(opts, dbPath)) {
///     db.compactRange();
/// }
/// ```
public final class CompactionFilter extends NativeObject {

	/// `rocksdb_compactionfilter_t* rocksdb_compactionfilter_create(void* state, void (*destructor)(void*), unsigned char (*filter)(void*, int level, const char* key, size_t key_length, const char* existing_value, size_t value_length, char** new_value, size_t* new_value_length, unsigned char* value_changed), const char* (*name)(void*));`
	private static final MethodHandle MH_COMPACTIONFILTER_CREATE = NativeLibrary.lookup(
			"rocksdb_compactionfilter_create",
			FunctionDescriptor.of(ValueLayout.ADDRESS,
					ValueLayout.ADDRESS,  // state
					ValueLayout.ADDRESS,  // destructor
					ValueLayout.ADDRESS,  // filter
					ValueLayout.ADDRESS)); // name

	/// `void rocksdb_compactionfilter_set_ignore_snapshots(rocksdb_compactionfilter_t*, unsigned char);`
	private static final MethodHandle MH_SET_IGNORE_SNAPSHOTS = NativeLibrary.lookup(
			"rocksdb_compactionfilter_set_ignore_snapshots",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE));

	/// `void rocksdb_compactionfilter_destroy(rocksdb_compactionfilter_t*);`
	private static final MethodHandle MH_COMPACTIONFILTER_DESTROY = NativeLibrary.lookup(
			"rocksdb_compactionfilter_destroy",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

	/// `void rocksdb_options_set_compaction_filter(rocksdb_options_t*, rocksdb_compactionfilter_t*);`
	private static final MethodHandle MH_SET_COMPACTION_FILTER = NativeLibrary.lookup(
			"rocksdb_options_set_compaction_filter",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

	private static final FunctionDescriptor FILTER_DESC = FunctionDescriptor.of(ValueLayout.JAVA_BYTE,
			ValueLayout.ADDRESS,   // state
			ValueLayout.JAVA_INT,  // level
			ValueLayout.ADDRESS,   // key
			ValueLayout.JAVA_LONG, // key_length
			ValueLayout.ADDRESS,   // existing_value
			ValueLayout.JAVA_LONG, // value_length
			ValueLayout.ADDRESS,   // new_value
			ValueLayout.ADDRESS,   // new_value_length
			ValueLayout.ADDRESS);  // value_changed

	private static final FunctionDescriptor DESTRUCTOR_DESC = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

	private static final FunctionDescriptor NAME_DESC = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

	// One global upcall stub per callback shape, shared by every instance. Lives for the JVM
	// lifetime so the function pointers are always valid; dispatch is keyed off the `state`
	// pointer, which carries a registry ID rather than real memory (same trick as MergeOperator.Custom).
	private static final MemorySegment FILTER_STUB;
	private static final MemorySegment DESTRUCTOR_STUB;
	private static final MemorySegment NAME_STUB;

	static {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			FILTER_STUB = Linker.nativeLinker().upcallStub(
					lookup.findStatic(CompactionFilter.class, "filterDispatch", MethodType.methodType(
							byte.class, MemorySegment.class, int.class, MemorySegment.class, long.class,
							MemorySegment.class, long.class, MemorySegment.class, MemorySegment.class,
							MemorySegment.class)),
					FILTER_DESC, Arena.global());
			DESTRUCTOR_STUB = Linker.nativeLinker().upcallStub(
					lookup.findStatic(CompactionFilter.class, "destructorDispatch",
							MethodType.methodType(void.class, MemorySegment.class)),
					DESTRUCTOR_DESC, Arena.global());
			NAME_STUB = Linker.nativeLinker().upcallStub(
					lookup.findStatic(CompactionFilter.class, "nameDispatch",
							MethodType.methodType(MemorySegment.class, MemorySegment.class)),
					NAME_DESC, Arena.global());
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	/// `scratchBuffers` backs [#scratchBuffer(State, byte[])] — one buffer per compaction thread
	/// that has returned a [FilterDecision.ChangeValue], reused and grown as needed rather than
	/// allocated fresh (and left unfreed) on every call.
	private record State(FilterFn fn, MemorySegment nameSeg, ConcurrentHashMap<Thread, ScratchBuffer> scratchBuffers) {

		State(FilterFn fn, MemorySegment nameSeg) {
			this(fn, nameSeg, new ConcurrentHashMap<>());
		}
	}

	/// A [FilterDecision.ChangeValue] buffer and the [Arena] that owns it. `arena` is an
	/// [Arena#ofShared()], not [Arena#ofConfined()], because it may need closing from a thread
	/// other than the one that allocated it: [#destructorDispatch(MemorySegment)] runs on
	/// whichever thread destroys the native filter (e.g. the thread closing the `Options`/`DB`),
	/// not necessarily one of the compaction threads that ever called
	/// [#scratchBuffer(State, byte[])].
	private record ScratchBuffer(Arena arena, MemorySegment segment) {
	}

	// Registry: id (smuggled through the `state` pointer) -> Java-side filter function.
	// Unregistered from destructorDispatch, not from tryClose: once ownership transfers to
	// Options via applyTo, the native shared_ptr controls this object's real lifetime, which can
	// outlive this Java wrapper (same rationale as MergeOperator.Custom).
	private static final UpcallRegistry<State> REGISTRY = new UpcallRegistry<>();
	private static final System.Logger LOG = System.getLogger(CompactionFilter.class.getName());

	private CompactionFilter(MemorySegment ptr) {
		super(ptr);
	}

	/// Wraps a Java-implemented compaction filter via RocksDB's general callback-based
	/// `rocksdb_compactionfilter_create`.
	///
	/// @param name stable identifier for this filter; RocksDB persists and checks it against the
	///             column family's stored options on every open, so it must not change across runs
	/// @param fn   decides the fate of each key-value pair; see [FilterFn] for the threading and
	///             lifetime contract
	/// @return a new compaction filter; caller must pass it to
	/// [Options#setCompactionFilter(CompactionFilter)] or close it
	public static CompactionFilter create(String name, FilterFn fn) {
		BackgroundUpcallThreads.installShutdownDrain();
		MemorySegment nameSeg = Arena.global().allocateFrom(name);
		MemorySegment statePtr = REGISTRY.register(new State(fn, nameSeg));
		try {
			MemorySegment ptr = (MemorySegment) MH_COMPACTIONFILTER_CREATE.invokeExact(
					statePtr, DESTRUCTOR_STUB, FILTER_STUB, NAME_STUB);
			return new CompactionFilter(ptr);
		} catch (Throwable t) {
			REGISTRY.unregister(statePtr);
			throw RocksDB.wrapInvokeFailure("CompactionFilter.create failed", t);
		}
	}

	/// Controls whether this filter also runs against keys still visible to an open snapshot.
	/// Defaults to `true` (matching RocksDB's own C++ default).
	///
	/// **`false` does not work in the pinned RocksDB version:** `CompactionJob::SetupAndValidateCompactionFilter`
	/// (`db/compaction/compaction_job.cc`) rejects every compaction that uses a filter with
	/// `IgnoreSnapshots() == false`, returning `Status::NotSupported("CompactionFilter::IgnoreSnapshots() = false is
	/// not supported anymore.")` — confirmed by upstream's own `DBTestCompactionFilter.IgnoreSnapshotsFalse` test.
	/// `rocksdb_compact_range` (what `compactRange()` wraps) takes no `errptr` and returns `void`, so this failure
	/// is silent: the compaction simply does nothing, and the caller has no way to detect it. Passing `false` here
	/// is kept only for parity with the C API's setter; leave this at its `true` default.
	///
	/// @param ignoreSnapshots whether to apply this filter regardless of open snapshots
	/// @return `this` for chaining
	public CompactionFilter setIgnoreSnapshots(boolean ignoreSnapshots) {
		try {
			MH_SET_IGNORE_SNAPSHOTS.invokeExact(ptr(), (byte) (ignoreSnapshots ? 1 : 0));
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("CompactionFilter.setIgnoreSnapshots failed", t);
		}
		return this;
	}

	void applyTo(MemorySegment optionsPtr) {
		try {
			MH_SET_COMPACTION_FILTER.invokeExact(optionsPtr, ptr());
			transferOwnership();
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("CompactionFilter.applyTo failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_COMPACTIONFILTER_DESTROY.invokeExact(ptr);
	}

	/// Decides what RocksDB should do with a key-value pair it is about to rewrite during
	/// compaction.
	///
	/// Invoked once per `Put`-originated key-value pair processed by a background or manual
	/// compaction — never for merge operands (see the class documentation). Implementations must
	/// be thread-safe and must not throw: an exception here is caught and logged, and the
	/// key-value pair is left unchanged rather than risk data loss from a filter bug.
	///
	/// `key` and `existingValue` are read-only and bound to an arena that is closed as soon as
	/// [#filter(int, MemorySegment, MemorySegment)] returns; they — and any view derived from
	/// them — must not be retained past the call, per [Mapper]'s contract. Copy into a `byte[]`
	/// via `segment.toArray(ValueLayout.JAVA_BYTE)` if you need the data to outlive the call.
	@FunctionalInterface
	public interface FilterFn {

		/// Decides the fate of one key-value pair.
		///
		/// @param level         the compaction output level
		/// @param key           zero-copy view of the key
		/// @param existingValue zero-copy view of the current value
		/// @return what RocksDB should do with this key-value pair
		FilterDecision filter(int level, MemorySegment key, MemorySegment existingValue);
	}

	/// The outcome of a [FilterFn] call.
	public sealed interface FilterDecision {

		/// Preserves the key-value pair unchanged.
		///
		/// @return the keep decision
		static FilterDecision keep() {
			return Keep.INSTANCE;
		}

		/// Drops the key-value pair — converted to a tombstone (`Delete`), removing any earlier
		/// version of the key as well.
		///
		/// @return the remove decision
		static FilterDecision remove() {
			return Remove.INSTANCE;
		}

		/// Replaces the value while keeping the key and its value type.
		///
		/// `newValue` is copied into a small native scratch buffer private to the calling
		/// compaction thread, reused (and grown as needed) across calls rather than allocated
		/// fresh each time: `rocksdb_compactionfilter_create`'s `filter` callback has no
		/// equivalent of [MergeOperator.Custom]'s `delete_value` hook, so nothing on the native
		/// side would ever free a one-off buffer for us.
		///
		/// @param newValue the replacement value
		/// @return the change-value decision
		static FilterDecision changeValue(byte[] newValue) {
			return new ChangeValue(newValue);
		}

		/// @see FilterDecision#keep()
		record Keep() implements FilterDecision {
			private static final Keep INSTANCE = new Keep();
		}

		/// @see FilterDecision#remove()
		record Remove() implements FilterDecision {
			private static final Remove INSTANCE = new Remove();
		}

		/// @param newValue the replacement value
		/// @see FilterDecision#changeValue(byte[])
		record ChangeValue(byte[] newValue) implements FilterDecision {
		}
	}

	/// Builds a zero-copy, read-only view of a borrowed native buffer, bound to `arena` so use
	/// past the call throws rather than reading freed/reused memory (same pattern as
	/// [MergeOperator.Custom]).
	private static MemorySegment view(MemorySegment ptr, long len, Arena arena) {
		if (MemorySegment.NULL.equals(ptr) || len <= 0) {
			return MemorySegment.ofArray(new byte[0]).asReadOnly();
		}
		return ptr.reinterpret(len, arena, null).asReadOnly();
	}

	/// Size past which [#scratchBuffer(State, byte[])] sweeps dead threads' buffers out of
	/// `scratchBuffers` — same reasoning as `BackgroundUpcallThreads`'s own prune threshold:
	/// compaction runs `filter` on RocksDB's long-lived background pool threads, but also on
	/// short-lived per-compaction subcompaction threads that are joined once the compaction
	/// finishes, so entries must be reclaimed rather than left to accumulate forever.
	private static final int SCRATCH_PRUNE_THRESHOLD = 64;

	/// Returns a native buffer at least `data.length` bytes long, holding a copy of `data`,
	/// scoped to the calling thread and reused across calls from that same thread.
	///
	/// A [FilterDecision.ChangeValue] must hand RocksDB a real native pointer — an [Arena]
	/// allocation freed when this call returns would be read after being freed, since
	/// `rocksdb_compactionfilter_t::Filter` (`db/c.cc`) only copies the bytes out *after*
	/// [#filterDispatch] has already returned to it, and `rocksdb_compactionfilter_create`'s
	/// `filter` callback has no `delete_value`-style hook (unlike [MergeOperator.Custom]) for
	/// RocksDB to release a fresh one-off buffer for us. Reuse sidesteps both problems: a given
	/// native thread can only ever be inside one `Filter()` call at a time, so by the time it
	/// asks for a buffer again, the previous call's copy has already completed and the buffer is
	/// safe to hand back (resized first, if `data` grew) — no per-call leak, and no
	/// use-after-free. Unlike [MergeOperator.Custom]'s buffer, RocksDB never frees this one
	/// itself, so — also unlike there — closing an [Arena] on our own schedule is exactly the
	/// right tool instead of a raw `malloc`/`free` pair.
	private static MemorySegment scratchBuffer(State s, byte[] data) {
		long needed = Math.max(1, data.length);
		ScratchBuffer buf = s.scratchBuffers().compute(Thread.currentThread(), (thread, existing) -> {
			if (existing != null && existing.segment().byteSize() >= needed) {
				return existing;
			}
			if (existing != null) {
				existing.arena().close();
			}
			Arena arena = Arena.ofShared();
			return new ScratchBuffer(arena, arena.allocate(needed));
		});
		MemorySegment.copy(data, 0, buf.segment(), ValueLayout.JAVA_BYTE, 0, data.length);
		if (s.scratchBuffers().size() > SCRATCH_PRUNE_THRESHOLD) {
			s.scratchBuffers().entrySet().removeIf(entry -> {
				if (entry.getKey().isAlive()) {
					return false;
				}
				entry.getValue().arena().close();
				return true;
			});
		}
		return buf.segment();
	}

	/// Called from [#FILTER_STUB]. Must not throw.
	private static byte filterDispatch(MemorySegment state, int level, MemorySegment key, long keyLength,
			MemorySegment existingValue, long valueLength, MemorySegment newValue, MemorySegment newValueLength,
			MemorySegment valueChanged) {
		BackgroundUpcallThreads.track();
		MemorySegment valueChangedPtr = valueChanged.reinterpret(ValueLayout.JAVA_BYTE.byteSize());
		valueChangedPtr.set(ValueLayout.JAVA_BYTE, 0, (byte) 0);
		try (Arena arena = Arena.ofConfined()) {
			State s = REGISTRY.get(state);
			MemorySegment keyView = view(key, keyLength, arena);
			MemorySegment existingView = view(existingValue, valueLength, arena);
			FilterDecision decision = s.fn().filter(level, keyView, existingView);
			return switch (decision) {
				case FilterDecision.Keep ignored -> (byte) 0;
				case FilterDecision.Remove ignored -> (byte) 1;
				case FilterDecision.ChangeValue c -> {
					MemorySegment buf = scratchBuffer(s, c.newValue());
					newValue.reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, buf);
					newValueLength.reinterpret(ValueLayout.JAVA_LONG.byteSize())
							.set(ValueLayout.JAVA_LONG, 0, (long) c.newValue().length);
					valueChangedPtr.set(ValueLayout.JAVA_BYTE, 0, (byte) 1);
					yield (byte) 0;
				}
			};
		} catch (Throwable t) {
			// must not throw across the upcall boundary — an escaping AssertionError here
			// (assertions are on by default under Surefire) would abort the JVM, not just this
			// call, so log and keep the key-value pair unchanged instead of risking data loss.
			// valueChangedPtr is already 0 from the reset above the try: the only place this
			// method ever sets it to 1 is the last statement of the ChangeValue case, so there is
			// no path that reaches this catch with it already flipped.
			LOG.log(System.Logger.Level.ERROR, "filter callback failed", t);
			return (byte) 0;
		}
	}

	/// Called from [#DESTRUCTOR_STUB] when RocksDB's internal `shared_ptr` refcount hits zero.
	/// This is the only reliable unregistration point: ownership transfer via [#applyTo] means
	/// [#tryClose(MemorySegment)] may never run for this instance. Also frees every scratch
	/// buffer [#scratchBuffer(State, byte[])] handed out — safe because no `Filter()` call can
	/// still be in flight once RocksDB itself is destroying this filter. Must not throw.
	private static void destructorDispatch(MemorySegment state) {
		State s = REGISTRY.unregister(state);
		if (s != null) {
			s.scratchBuffers().values().forEach(buf -> buf.arena().close());
		}
	}

	/// Fallback for [#nameDispatch] when the registry entry is missing. RocksDB's `Name()` does
	/// `std::string(name)` on whatever this returns with no null check of its own, so a raw null
	/// pointer here would itself be a native crash; an empty, NUL-terminated C string is the safe
	/// equivalent (same pattern as [MergeOperator.Custom]).
	private static final MemorySegment EMPTY_NAME = Arena.global().allocateFrom("");

	/// Called from [#NAME_STUB]. Must not throw, and must never return a value that isn't a
	/// valid NUL-terminated native string (see [#EMPTY_NAME]).
	private static MemorySegment nameDispatch(MemorySegment state) {
		try {
			State s = REGISTRY.get(state);
			MemorySegment result = s != null ? s.nameSeg() : EMPTY_NAME;
			if (result == null) {
				throw new AssertionError("nameDispatch produced a null upcall return value");
			}
			return result;
		} catch (Throwable t) {
			// same "must not throw" contract as filterDispatch.
			LOG.log(System.Logger.Level.ERROR, "nameDispatch failed", t);
			return EMPTY_NAME;
		}
	}
}
