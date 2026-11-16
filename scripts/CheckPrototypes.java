import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Checks that every `MH_` field's `/// \`prototype\`` comment still matches `rocksdb/include/rocksdb/c.h`.
///
/// The comment is the reviewed source of truth for the field's `FunctionDescriptor`. When RocksDB
/// changes a prototype between releases, the binding keeps linking and calls with the old ABI:
/// memory corruption, not an exception. Only the ABI is compared: return and parameter types,
/// ignoring whitespace and parameter names.
///
/// It also checks the `FunctionDescriptor` of each field: a `size_t` parameter or return value passed by value
/// must be declared `NativeCalls.SIZE_T`, the platform's own `size_t`, never a fixed `JAVA_LONG`. (A `size_t*` stays
/// `ADDRESS`; its slot is allocated and read with `NativeCalls.SIZE_T` where it is used.)
///
/// Exits 1 if a comment is missing, names a symbol `c.h` no longer declares, differs from it, or spells a `size_t`
/// descriptor argument with anything but `SIZE_T`, or has a different number of parameters than `c.h`. Only `MH_`
/// fields are covered: the upcall descriptors (`MergeOperator`, `CompactionFilter`, `Logger`, ...) and the `size_t*`
/// slots are not, and stay a review concern.
///
/// Every `MH_` field is also required to use the shape `= NativeLibrary.downcall(` / name and descriptor / `);`.
///
/// Run from the repository root: `java scripts/CheckPrototypes.java`.

static final Path C_H = Path.of("rocksdb/include/rocksdb/c.h");
static final Pattern MH_FIELD = Pattern.compile("\\bMethodHandle\\s+MH_\\w+");
static final Pattern PROTOTYPE = Pattern.compile("^\\s*/// `(.+)`\\s*$");
static final Pattern FUNCTION_NAME = Pattern.compile("(\\w+)\\s*\\(");

static final Pattern SEGMENT = Pattern.compile("[^(),]+");
static final Pattern NAMED = Pattern.compile("^(.*?[\\s*])([A-Za-z_]\\w*)\\s*(\\[\\s*\\])?$", Pattern.DOTALL);
static final java.util.Set<String> TYPE_WORDS = java.util.Set.of(
		"void", "char", "short", "int", "long", "float", "double", "bool", "signed", "unsigned", "const");

/// Drops the trailing identifier of every declarator segment (parameter names, and the function
/// name itself, which is matched separately), so `unsigned char v` and `unsigned char val` agree.
static String abi(String decl) {
	return SEGMENT.matcher(decl).replaceAll(r -> {
		Matcher m = NAMED.matcher(r.group().strip());
		boolean named = m.matches() && !TYPE_WORDS.contains(m.group(2)) && !m.group(2).endsWith("_t");
		String type = named ? m.group(1) + (m.group(3) == null ? "" : "[]") : r.group();
		return Matcher.quoteReplacement(type.replaceAll("\\s+", ""));
	});
}

static final Pattern SIZE_T_BY_VALUE = Pattern.compile("^(?:const\\s+)?size_t(?:\\s+\\w+)?$");

/// Splits `text` at top-level commas, ignoring those inside nested parentheses.
static List<String> topLevelArgs(String text) {
	List<String> args = new ArrayList<>();
	int depth = 0;
	int start = 0;
	for (int i = 0; i < text.length(); i++) {
		char c = text.charAt(i);
		if (c == '(') {
			depth++;
		} else if (c == ')') {
			depth--;
		} else if (c == ',' && depth == 0) {
			args.add(text.substring(start, i).strip());
			start = i + 1;
		}
	}
	args.add(text.substring(start).strip());
	return args;
}

/// The argument list of the literal `FunctionDescriptor` passed to `downcall("symbol", ...)` in `source` (comments
/// already stripped), return layout first for `of(...)` and `null` for `ofVoid(...)`, or `null` if there is none.
static List<String> descriptorArgs(String source, String symbol) {
	Matcher start = Pattern.compile("downcall\\(\\s*\"" + symbol + "\"\\s*,\\s*FunctionDescriptor\\.(of|ofVoid)\\(").matcher(source);
	if (!start.find()) {
		return null;
	}
	int open = start.end() - 1;
	int depth = 0;
	int close = open;
	for (; close < source.length(); close++) {
		char c = source.charAt(close);
		depth += c == '(' ? 1 : c == ')' ? -1 : 0;
		if (depth == 0) {
			break;
		}
	}
	List<String> args = new ArrayList<>(topLevelArgs(source.substring(open + 1, close)));
	if (start.group(1).equals("ofVoid")) {
		args.addFirst(null);
	}
	return args;
}

/// Problems with the `size_t` by-value arguments of `symbol`'s descriptor, given its declaration in `c.h`.
static List<String> sizeTProblems(String source, String symbol, String declared) {
	List<String> args = descriptorArgs(source, symbol);
	List<String> problems = new ArrayList<>();
	if (args == null) {
		problems.add("no literal FunctionDescriptor in downcall(\"" + symbol + "\", ...)");
		return problems;
	}
	int open = declared.indexOf('(');
	String ret = declared.substring(0, open).replaceAll("\\s*\\w+\\s*$", "").replaceAll("\\s+", " ").strip();
	List<String> params = topLevelArgs(declared.substring(open + 1, declared.lastIndexOf(')')));
	if (params.size() == 1 && params.getFirst().equals("void")) {
		params = List.of();
	}
	if (args.size() != params.size() + 1) {
		problems.add("descriptor has " + (args.size() - 1) + " parameters, c.h declares " + params.size());
		return problems;
	}
	if (ret.equals("size_t") && (args.getFirst() == null || !args.getFirst().endsWith("SIZE_T"))) {
		problems.add("return value is size_t but the descriptor says " + args.getFirst());
	}
	for (int p = 0; p < params.size(); p++) {
		String arg = args.get(p + 1);
		if (SIZE_T_BY_VALUE.matcher(params.get(p)).matches() && !arg.endsWith("SIZE_T")) {
			problems.add("parameter " + (p + 1) + " is size_t but the descriptor says " + arg);
		}
	}
	return problems;
}

/// Function name → declaration (whitespace collapsed), for every function `c.h` declares.
static Map<String, String> headerPrototypes() throws Exception {
	String text = Files.readString(C_H)
			.replaceAll("(?s)/\\*.*?\\*/|//[^\n]*", "")
			.replaceAll("(?m)^\\s*#.*$", "")
			.replaceAll("\\bextern\\b|\\bROCKSDB_LIBRARY_API\\b", "")
			.replaceAll("(?s)\\b(?:typedef|struct|enum)\\b[^;{]*\\{.*?\\}[^;]*;", ";");
	Map<String, String> prototypes = new HashMap<>();
	for (String decl : text.split(";")) {
		Matcher m = FUNCTION_NAME.matcher(decl);
		if (!decl.contains("typedef") && m.find()) {
			prototypes.put(m.group(1), decl.strip().replaceAll("\\s+", " ").replace("( ", "(") + ";");
		}
	}
	return prototypes;
}

void main() throws Exception {
	if (!Files.isRegularFile(C_H)) {
		throw new IllegalStateException("run from the repository root, with the rocksdb submodule checked out");
	}
	Map<String, String> header = headerPrototypes();
	// Tracked files only: a walk would also pick up .claude/worktrees/ and other checkouts.
	Process git = new ProcessBuilder("git", "ls-files", "*/src/main/*.java").redirectError(ProcessBuilder.Redirect.INHERIT).start();
	List<Path> sources = new String(git.getInputStream().readAllBytes()).lines().map(Path::of).toList();
	if (git.waitFor() != 0 || sources.isEmpty()) {
		throw new IllegalStateException("git ls-files failed");
	}
	List<String> errors = new ArrayList<>();
	int checked = 0;
	for (Path source : sources) {
		List<String> lines = Files.readAllLines(source);
		String text = String.join("\n", lines).replaceAll("(?s)/\\*.*?\\*/|//[^\n]*", "");
		for (int i = 0; i < lines.size(); i++) {
			if (!MH_FIELD.matcher(lines.get(i)).find()) {
				continue;
			}
			String where = source + ":" + (i + 1);
			if (!lines.get(i).matches("\\t+private static final MethodHandle MH_\\w+ = NativeLibrary\\.downcall\\(")) {
				errors.add(where + ": MH_ field must be initialized inline: `= NativeLibrary.downcall(`, then the symbol name and descriptor, then `);` on its own line");
			} else if (lines.subList(i + 1, lines.size()).stream().dropWhile(l -> !l.matches("\\t+\\);") && !l.contains("MethodHandle MH_")).findFirst().filter(l -> l.matches("\\t+\\);")).isEmpty()) {
				errors.add(where + ": the closing `);` of the MH_ initializer must be on its own line");
			}
			int docStart = i;
			while (docStart > 0 && lines.get(docStart - 1).strip().startsWith("///")) {
				docStart--;
			}
			if (docStart < i && lines.get(docStart).strip().startsWith("/// libc ")) {
				continue; // not a c.h symbol, e.g. MergeOperator's malloc
			}
			Matcher comment = i > 0 ? PROTOTYPE.matcher(lines.get(i - 1)) : null;
			if (comment == null || !comment.matches()) {
				errors.add(where + ": no /// `prototype` comment above the MH_ field");
				continue;
			}
			checked++;
			String prototype = comment.group(1);
			Matcher name = FUNCTION_NAME.matcher(prototype);
			String declared = name.find() ? header.get(name.group(1)) : null;
			if (declared == null) {
				errors.add(where + ": not declared in c.h: " + prototype);
			} else if (!abi(declared).equals(abi(prototype))) {
				errors.add(where + ": prototype changed\n    java: " + prototype + "\n    c.h:  " + declared);
			} else {
				sizeTProblems(text, name.group(1), declared).forEach(p -> errors.add(where + ": " + name.group(1) + ": " + p));
			}
		}
	}
	errors.forEach(System.out::println);
	System.out.printf("%d prototypes checked against %d c.h functions, %d problems%n", checked, header.size(), errors.size());
	if (!errors.isEmpty()) {
		System.exit(1);
	}
}
