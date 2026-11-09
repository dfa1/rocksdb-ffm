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
/// Exits 1 if a comment is missing, names a symbol `c.h` no longer declares, or differs from it.
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
		for (int i = 0; i < lines.size(); i++) {
			if (!MH_FIELD.matcher(lines.get(i)).find()) {
				continue;
			}
			String where = source + ":" + (i + 1);
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
			}
		}
	}
	errors.forEach(System.out::println);
	System.out.printf("%d prototypes checked against %d c.h functions, %d problems%n", checked, header.size(), errors.size());
	if (!errors.isEmpty()) {
		System.exit(1);
	}
}
