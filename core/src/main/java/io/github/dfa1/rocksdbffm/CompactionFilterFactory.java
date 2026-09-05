package io.github.dfa1.rocksdbffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/// A factory that creates a fresh [CompactionFilter] for every compaction, attached via
/// [Options#setCompactionFilterFactory(CompactionFilterFactory)].
///
/// Unlike a [CompactionFilter] attached directly via [Options#setCompactionFilter], which is
/// shared by every concurrent background compaction and therefore must be thread-safe, RocksDB
/// calls [CreateFilterFn#createCompactionFilter(CompactionFilterContext)] once per table-file
/// creation, on the single thread performing that compaction, and destroys the returned filter
/// once the compaction finishes. Each filter instance is used by exactly one thread — private
/// mutable state needs no synchronization. The creation callback also receives a
/// [CompactionFilterContext] describing why this compaction is running, letting `fn` decide not
/// to filter at all (returning `null`) for compactions where filtering isn't wanted.
///
/// ```
/// try (var factory = CompactionFilterFactory.create("ttl-filter", context ->
///         context.isManualCompaction()
///                 ? null // don't filter manual/on-demand compactions
///                 : CompactionFilter.create("ttl-filter", dropExpired));
///      var opts = Options.newOptions().setCreateIfMissing(true).setCompactionFilterFactory(factory);
///      var db = RocksDB.openReadWrite(opts, dbPath)) {
///     db.compactRange();
/// }
/// ```
public final class CompactionFilterFactory extends NativeObject {

	/// `rocksdb_compactionfilterfactory_t* rocksdb_compactionfilterfactory_create(void* state, void (*destructor)(void*), rocksdb_compactionfilter_t* (*create_compaction_filter)(void*, rocksdb_compactionfiltercontext_t* context), const char* (*name)(void*));`
	private static final MethodHandle MH_COMPACTIONFILTERFACTORY_CREATE = NativeLibrary.lookup(
			"rocksdb_compactionfilterfactory_create",
			FunctionDescriptor.of(ValueLayout.ADDRESS,
					ValueLayout.ADDRESS,  // state
					ValueLayout.ADDRESS,  // destructor
					ValueLayout.ADDRESS,  // create_compaction_filter
					ValueLayout.ADDRESS)); // name

	/// `void rocksdb_compactionfilterfactory_destroy(rocksdb_compactionfilterfactory_t*);`
	private static final MethodHandle MH_COMPACTIONFILTERFACTORY_DESTROY = NativeLibrary.lookup(
			"rocksdb_compactionfilterfactory_destroy",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

	/// `void rocksdb_options_set_compaction_filter_factory(rocksdb_options_t*, rocksdb_compactionfilterfactory_t*);`
	private static final MethodHandle MH_SET_COMPACTION_FILTER_FACTORY = NativeLibrary.lookup(
			"rocksdb_options_set_compaction_filter_factory",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

	private static final FunctionDescriptor CREATE_FILTER_DESC = FunctionDescriptor.of(ValueLayout.ADDRESS,
			ValueLayout.ADDRESS,  // state
			ValueLayout.ADDRESS); // context

	private static final FunctionDescriptor DESTRUCTOR_DESC = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

	private static final FunctionDescriptor NAME_DESC = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);

	// One global upcall stub per callback shape, shared by every instance. Lives for the JVM
	// lifetime so the function pointers are always valid; dispatch is keyed off the `state`
	// pointer, which carries a registry ID rather than real memory (same trick as CompactionFilter).
	private static final MemorySegment CREATE_FILTER_STUB;
	private static final MemorySegment DESTRUCTOR_STUB;
	private static final MemorySegment NAME_STUB;

	static {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			CREATE_FILTER_STUB = Linker.nativeLinker().upcallStub(
					lookup.findStatic(CompactionFilterFactory.class, "createCompactionFilterDispatch",
							MethodType.methodType(MemorySegment.class, MemorySegment.class, MemorySegment.class)),
					CREATE_FILTER_DESC, Arena.global());
			DESTRUCTOR_STUB = Linker.nativeLinker().upcallStub(
					lookup.findStatic(CompactionFilterFactory.class, "destructorDispatch",
							MethodType.methodType(void.class, MemorySegment.class)),
					DESTRUCTOR_DESC, Arena.global());
			NAME_STUB = Linker.nativeLinker().upcallStub(
					lookup.findStatic(CompactionFilterFactory.class, "nameDispatch",
							MethodType.methodType(MemorySegment.class, MemorySegment.class)),
					NAME_DESC, Arena.global());
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private record State(CreateFilterFn fn, MemorySegment nameSeg) {
	}

	// Registry: id (smuggled through the `state` pointer) -> Java-side factory function.
	// Unregistered from destructorDispatch, not from tryClose: once ownership transfers to
	// Options via applyTo, the native shared_ptr controls this object's real lifetime, which can
	// outlive this Java wrapper (same rationale as CompactionFilter/MergeOperator.Custom).
	private static final UpcallRegistry<State> REGISTRY = new UpcallRegistry<>();
	private static final System.Logger LOG = System.getLogger(CompactionFilterFactory.class.getName());

	private CompactionFilterFactory(MemorySegment ptr) {
		super(ptr);
	}

	/// Wraps a Java-implemented compaction filter factory via RocksDB's general callback-based
	/// `rocksdb_compactionfilterfactory_create`.
	///
	/// @param name stable identifier for this factory; RocksDB persists and checks it against the
	///             column family's stored options on every open, so it must not change across runs
	/// @param fn   creates a fresh filter for each compaction; see [CreateFilterFn] for the
	///             threading and lifetime contract
	/// @return a new compaction filter factory; caller must pass it to
	/// [Options#setCompactionFilterFactory(CompactionFilterFactory)] or close it
	public static CompactionFilterFactory create(String name, CreateFilterFn fn) {
		BackgroundUpcallThreads.installShutdownDrain();
		MemorySegment nameSeg = Arena.global().allocateFrom(name);
		MemorySegment statePtr = REGISTRY.register(new State(fn, nameSeg));
		try {
			MemorySegment ptr = (MemorySegment) MH_COMPACTIONFILTERFACTORY_CREATE.invokeExact(
					statePtr, DESTRUCTOR_STUB, CREATE_FILTER_STUB, NAME_STUB);
			return new CompactionFilterFactory(ptr);
		} catch (Throwable t) {
			REGISTRY.unregister(statePtr);
			throw RocksDB.wrapInvokeFailure("CompactionFilterFactory.create failed", t);
		}
	}

	void applyTo(MemorySegment optionsPtr) {
		try {
			MH_SET_COMPACTION_FILTER_FACTORY.invokeExact(optionsPtr, ptr());
			transferOwnership();
		} catch (Throwable t) {
			throw RocksDB.wrapInvokeFailure("CompactionFilterFactory.applyTo failed", t);
		}
	}

	@Override
	protected void tryClose(MemorySegment ptr) throws Throwable {
		MH_COMPACTIONFILTERFACTORY_DESTROY.invokeExact(ptr);
	}

	/// Creates a [CompactionFilter] for one compaction, given a [CompactionFilterContext]
	/// describing why that compaction is running.
	///
	/// Invoked once per table-file creation, on the single thread performing that compaction —
	/// unlike [CompactionFilter.FilterFn], implementations do not need to be thread-safe, and may
	/// freely close over per-compaction mutable state. Must not throw: an exception here is
	/// caught and logged, and RocksDB proceeds with no filter for that compaction, the same as
	/// returning `null`.
	///
	/// `context` is read-only and bound to the duration of this call; do not retain it, or any
	/// view derived from it, past the call returning.
	@FunctionalInterface
	public interface CreateFilterFn {

		/// Creates a filter for one compaction, or declines to filter it at all.
		///
		/// @param context describes why this compaction is running
		/// @return a new [CompactionFilter] for RocksDB to use and destroy once this compaction
		/// finishes, or `null` to run this compaction with no filter
		CompactionFilter createCompactionFilter(CompactionFilterContext context);
	}

	/// Called from [#CREATE_FILTER_STUB]. Must not throw.
	private static MemorySegment createCompactionFilterDispatch(MemorySegment state, MemorySegment contextPtr) {
		BackgroundUpcallThreads.track();
		try {
			State s = REGISTRY.get(state);
			CompactionFilter filter = s.fn().createCompactionFilter(new CompactionFilterContext(contextPtr));
			if (filter == null) {
				return MemorySegment.NULL;
			}
			MemorySegment filterPtr = filter.ptr();
			filter.transferOwnership();
			return filterPtr;
		} catch (Throwable t) {
			// must not throw across the upcall boundary — an escaping AssertionError here
			// (assertions are on by default under Surefire) would abort the JVM, not just this
			// call, so log and run this compaction with no filter instead of risking a crash.
			LOG.log(System.Logger.Level.ERROR, "createCompactionFilter callback failed", t);
			return MemorySegment.NULL;
		}
	}

	/// Called from [#DESTRUCTOR_STUB] when RocksDB's internal `shared_ptr` refcount hits zero.
	/// This is the only reliable unregistration point: ownership transfer via [#applyTo] means
	/// [#tryClose(MemorySegment)] may never run for this instance. Must not throw.
	private static void destructorDispatch(MemorySegment state) {
		REGISTRY.unregister(state);
	}

	/// Fallback for [#nameDispatch] when the registry entry is missing. Same rationale as
	/// [CompactionFilter]'s equivalent fallback: RocksDB's `Name()` does `std::string(name)` on
	/// whatever this returns with no null check of its own, so a raw null pointer here would
	/// itself be a native crash.
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
			// same "must not throw" contract as createCompactionFilterDispatch.
			LOG.log(System.Logger.Level.ERROR, "nameDispatch failed", t);
			return EMPTY_NAME;
		}
	}
}
