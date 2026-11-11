import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Checks that int-backed Java enums still match the RocksDB headers they mirror.
///
/// RocksDB renumbers enums between releases (e.g. new tickers inserted mid-list), and nothing
/// fails when the Java side drifts: the wrong counter is silently read. This asks the C++ compiler
/// for the real value of every native enumerator, then diffs them against the Java constants
/// (`NAME(value)` or `case value -> NAME`, else the ordinal).
///
/// Exits 1 on drift: a value mismatch, or a Java constant with no native counterpart.
/// Native-only enumerators are listed as warnings: new upstream values to consider wrapping.
///
/// Run from the repository root: `java scripts/CheckEnums.java` (`CXX` defaults to `zig c++`).

static final Path INC = Path.of("rocksdb/include");
static final Path JAVA = Path.of("core/src/main/java/io/github/dfa1/rocksdbffm");

/// `nativeEnum` starting with `@` names the first enumerator of an anonymous `c.h` enum;
/// `affixes` are stripped from both normalized names; `aliases` maps a Java name to its native one.
/// A `bitmask` enum's zero value (no bits set) is an empty `EnumSet` in Java, not a constant.
record Mapping(String javaFile, String javaEnum, String header, String nativeEnum, String qualifier,
		List<String> affixes, Map<String, String> aliases, boolean bitmask) {
	Mapping(String javaFile, String javaEnum, String header, String nativeEnum, String qualifier) {
		this(javaFile, javaEnum, header, nativeEnum, qualifier, List.of(), Map.of());
	}

	Mapping(String javaFile, String javaEnum, String header, String nativeEnum, String qualifier,
			List<String> affixes, Map<String, String> aliases) {
		this(javaFile, javaEnum, header, nativeEnum, qualifier, affixes, aliases, false);
	}

	String key() {
		return javaFile + "." + javaEnum;
	}
}

static final List<Mapping> ENUMS = List.of(
		new Mapping("TickerType", "TickerType", "statistics.h", "Tickers", "rocksdb::"),
		new Mapping("HistogramType", "HistogramType", "statistics.h", "Histograms", "rocksdb::"),
		new Mapping("StatsLevel", "StatsLevel", "statistics.h", "StatsLevel", "rocksdb::"),
		new Mapping("CompactionReason", "CompactionReason", "listener.h", "CompactionReason", "rocksdb::CompactionReason::"),
		new Mapping("FlushReason", "FlushReason", "listener.h", "FlushReason", "rocksdb::FlushReason::"),
		new Mapping("BackgroundErrorReason", "BackgroundErrorReason", "listener.h", "BackgroundErrorReason", "rocksdb::BackgroundErrorReason::"),
		new Mapping("FileType", "FileType", "types.h", "FileType", "rocksdb::"),
		new Mapping("WriteStallCondition", "WriteStallCondition", "types.h", "WriteStallCondition", "rocksdb::WriteStallCondition::"),
		new Mapping("Temperature", "Temperature", "types.h", "Temperature", "rocksdb::Temperature::"),
		new Mapping("CompressionType", "CompressionType", "compression_type.h", "CompressionType", "rocksdb::",
				List.of("compression"), Map.of("BZLIB2", "kBZip2Compression")),
		new Mapping("Options", "CompactionStyle", "advanced_options.h", "CompactionStyle", "rocksdb::",
				List.of("compactionstyle"), Map.of()),
		new Mapping("Options", "CompactionPriority", "advanced_options.h", "CompactionPri", "rocksdb::"),
		new Mapping("PrepopulateBlobCache", "PrepopulateBlobCache", "advanced_options.h", "PrepopulateBlobCache", "rocksdb::PrepopulateBlobCache::"),
		new Mapping("IOActivity", "IOActivity", "env.h", "IOActivity", "rocksdb::Env::IOActivity::"),
		new Mapping("IOPriority", "IOPriority", "env.h", "IOPriority", "rocksdb::Env::", List.of("io"), Map.of()),
		new Mapping("LogLevel", "LogLevel", "env.h", "InfoLogLevel", "rocksdb::", List.of("level"), Map.of()),
		new Mapping("PerfLevel", "PerfLevel", "perf_level.h", "PerfLevel", "rocksdb::"),
		new Mapping("BlockBasedTableOptions", "ChecksumType", "table.h", "ChecksumType", "rocksdb::"),
		new Mapping("BlockBasedTableOptions", "PinningTier", "table.h", "PinningTier", "rocksdb::PinningTier::"),
		new Mapping("BlockBasedTableOptions", "IndexType", "table.h", "IndexType", "rocksdb::BlockBasedTableOptions::"),
		new Mapping("BlockBasedTableOptions", "IndexSearchType", "table.h", "BlockSearchType", "rocksdb::BlockBasedTableOptions::"),
		new Mapping("BlockBasedTableOptions", "DataBlockIndexType", "table.h", "DataBlockIndexType", "rocksdb::BlockBasedTableOptions::",
				List.of("datablock"), Map.of()),
		new Mapping("BlockBasedTableOptions", "IndexShorteningMode", "table.h", "IndexShorteningMode", "rocksdb::BlockBasedTableOptions::IndexShorteningMode::"),
		new Mapping("BlockBasedTableOptions", "PrepopulateBlockCache", "table.h", "PrepopulateBlockCache", "rocksdb::BlockBasedTableOptions::PrepopulateBlockCache::"),
		new Mapping("BlockBasedTableOptions", "OptimizeKeyCommonPrefix", "table.h", "OptimizeKeyCommonPrefix", "rocksdb::BlockBasedTableOptions::OptimizeKeyCommonPrefix::"),
		new Mapping("PlainTableOptions", "EncodingType", "table.h", "EncodingType", "rocksdb::"),
		new Mapping("UniversalCompactionOptions", "StopStyle", "universal_compaction.h", "CompactionStopStyle", "rocksdb::",
				List.of("compactionstopstyle"), Map.of()),
		new Mapping("RateLimiter", "Mode", "rate_limiter.h", "Mode", "rocksdb::RateLimiter::Mode::"),
		new Mapping("WritePolicy", "WritePolicy", "utilities/transaction_db.h", "TxnDBWritePolicy", "rocksdb::"),
		new Mapping("PerfMetric", "PerfMetric", "c.h", "@rocksdb_user_key_comparison_count", ""),
		new Mapping("WriteBufferManager", "FlushPolicy", "c.h", "@rocksdb_write_buffer_manager_flush_policy_oldest", "",
                List.of("writebuffermanagerflushpolicy"), Map.of()),
		new Mapping("TraceFilter", "TraceFilter", "options.h", "TraceFilterType", "rocksdb::",
				List.of("tracefilter"), Map.of(), true));

/// Count/range markers rather than real values.
static final Pattern SENTINEL = Pattern.compile(
		"_ENUM_MAX$|^NUM_INFO_LOG_LEVELS$|^kNumOf|^kFirst|^kLast|^kCustom|^kOutOfBounds$|_total_metric_count$");

/// An int or long literal, optionally shifted: `3`, `0x0A`, `0x1L << 2`.
static final Pattern LITERAL = Pattern.compile("(-?(?:0x[0-9A-Fa-f]+|\\d+))[lL]?(?:\\s*<<\\s*(\\d+))?");

static String stripComments(String text) {
	return text.replaceAll("(?s)//[^\n]*|/\\*.*?\\*/", "");
}

static String body(String text, int openBrace) {
	int depth = 0;
	for (int i = openBrace; i < text.length(); i++) {
		char c = text.charAt(i);
		depth += c == '{' ? 1 : c == '}' ? -1 : 0;
		if (depth == 0) {
			return text.substring(openBrace + 1, i);
		}
	}
	throw new IllegalStateException("unbalanced braces");
}

static Matcher find(String regex, String text, String what) {
	Matcher m = Pattern.compile(regex).matcher(text);
	if (!m.find()) {
		throw new IllegalStateException("not found: " + what);
	}
	return m;
}

static List<String> nativeNames(Mapping e) throws Exception {
	String text = stripComments(Files.readString(INC.resolve("rocksdb").resolve(e.header())));
	int start = e.nativeEnum().startsWith("@")
			? text.lastIndexOf("enum", text.indexOf(e.nativeEnum().substring(1)))
			: find("\\benum\\s+(?:class\\s+)?" + e.nativeEnum() + "\\b[^;{]*\\{", text, e.nativeEnum()).start();
	String block = body(text, text.indexOf('{', start)).replaceAll("ROCKSDB_DEPRECATED_MSG\\([^)]*\\)", "");
	List<String> names = new ArrayList<>();
	Matcher m = Pattern.compile("(?:^|,)\\s*([A-Za-z_]\\w*)\\s*(?==|,|$)").matcher(block);
	while (m.find()) {
		names.add(m.group(1));
	}
	return names;
}

static Map<String, Long> javaValues(Mapping e) throws Exception {
	String text = stripComments(Files.readString(JAVA.resolve(e.javaFile() + ".java")));
	String block = body(text, find("\\benum\\s+" + e.javaEnum() + "\\s*\\{", text, e.key()).end() - 1);
	Map<String, Long> cases = new LinkedHashMap<>();
	Matcher c = Pattern.compile("case\\s+(-?(?:0x[0-9A-Fa-f]+|\\d+))\\s*->\\s*([A-Z][A-Z0-9_]*)\\s*;").matcher(block);
	while (c.find()) {
		cases.put(c.group(2), Long.decode(c.group(1)));
	}
	Map<String, Long> values = new LinkedHashMap<>();
	Matcher m = Pattern.compile("([A-Z][A-Z0-9_]*)\\s*(?:\\(([^)]*)\\))?\\s*(?:,|$)").matcher(block.split(";")[0].strip());
	for (int ordinal = 0; m.find(); ordinal++) {
		String name = m.group(1);
		String arg = m.group(2) == null ? "" : m.group(2).strip();
		Matcher literal = LITERAL.matcher(arg);
		if (cases.containsKey(name)) {
			values.put(name, cases.get(name));
		} else if (literal.matches()) {
			values.put(name, Long.decode(literal.group(1)) << (literal.group(2) == null ? 0 : Integer.parseInt(literal.group(2))));
		} else if (arg.isEmpty()) {
			values.put(name, (long) ordinal);
		} else {
			throw new IllegalStateException(e.key() + "." + name + "(" + arg + "): not an int literal; add a case -> mapping or extend this script");
		}
	}
	return values;
}

static String normalize(String name, List<String> affixes) {
	String n = name.replaceFirst("^rocksdb_", "")
			.replaceFirst("^k(?=[A-Z0-9]|[a-z]+[A-Z])", "") // kFoo, kxxHash; not key_lock_wait_time
			.replace("_", "")
			.toLowerCase(Locale.ROOT);
	for (String a : affixes) {
		n = n.startsWith(a) ? n.substring(a.length()) : n;
		n = n.endsWith(a) ? n.substring(0, n.length() - a.length()) : n;
	}
	return n;
}

static String run(List<String> command) throws Exception {
	Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
	String out = new String(p.getInputStream().readAllBytes());
	if (p.waitFor() != 0) {
		throw new IllegalStateException("failed: " + String.join(" ", command));
	}
	return out;
}

/// Compiles and runs a C++ program printing `<key> <enumerator> <value>` for every native enumerator.
static Map<String, List<Map.Entry<String, Long>>> nativeValues() throws Exception {
	List<String> src = new ArrayList<>(List.of("#include <cstdio>"));
	new TreeSet<>(ENUMS.stream().map(e -> "#include \"rocksdb/" + e.header() + "\"").toList()).forEach(src::add);
	src.add("int main() {");
	for (Mapping e : ENUMS) {
		for (String n : nativeNames(e)) {
			src.add("\tstd::printf(\"%s %s %%lld\\n\", (long long) %s%s);".formatted(e.key(), n, e.qualifier(), n));
		}
	}
	src.add("}");
	Path tmp = Files.createTempDirectory("check-enums");
	Path cc = Files.write(tmp.resolve("enums.cc"), src);
	Path exe = tmp.resolve(System.getProperty("os.name").startsWith("Windows") ? "enums.exe" : "enums");
	List<String> compile = new ArrayList<>(List.of(System.getenv().getOrDefault("CXX", "zig c++").trim().split("\\s+")));
	compile.addAll(List.of("-std=c++20", "-w", "-I", INC.toString(), cc.toString(), "-o", exe.toString()));
	run(compile);
	Map<String, List<Map.Entry<String, Long>>> result = new LinkedHashMap<>();
	for (String line : run(List.of(exe.toAbsolutePath().toString())).split("\n")) {
		String[] f = line.trim().split(" ");
		result.computeIfAbsent(f[0], _ -> new ArrayList<>()).add(Map.entry(f[1], Long.parseLong(f[2])));
	}
	return result;
}

void main() throws Exception {
	if (!Files.isDirectory(INC) || !Files.isDirectory(JAVA)) {
		throw new IllegalStateException("run from the repository root, with the rocksdb submodule checked out");
	}
	Map<String, List<Map.Entry<String, Long>>> natives = nativeValues();
	boolean failed = false;
	for (Mapping e : ENUMS) {
		Map<String, Map.Entry<String, Long>> byName = new LinkedHashMap<>();
		for (Map.Entry<String, Long> n : natives.getOrDefault(e.key(), List.of())) {
			if (!SENTINEL.matcher(n.getKey()).find() && !(e.bitmask() && n.getValue() == 0)) {
				byName.put(normalize(n.getKey(), e.affixes()), n);
			}
		}
		List<String> errors = new ArrayList<>();
		List<String> matched = new ArrayList<>();
		javaValues(e).forEach((name, value) -> {
			String k = normalize(e.aliases().getOrDefault(name, name), e.affixes());
			Map.Entry<String, Long> n = byName.get(k);
			if (n == null) {
				errors.add("  ERROR java-only %s = %d".formatted(name, value));
				return;
			}
			matched.add(k);
			if (n.getValue() != value.longValue()) {
				errors.add("  ERROR %s = %d, native %s = %d".formatted(name, value, n.getKey(), n.getValue()));
			}
		});
		System.out.println(e.key() + ": " + (errors.isEmpty() ? "ok" : "FAIL"));
		errors.forEach(System.out::println);
		byName.forEach((k, n) -> {
			if (!matched.contains(k)) {
				System.out.printf("  warn  native-only %s = %d%n", n.getKey(), n.getValue());
			}
		});
		failed |= !errors.isEmpty();
	}
	if (failed) {
		System.out.println("\nJava enums drifted from the RocksDB headers. Renumber them from the native values above.");
		System.exit(1);
	}
}
