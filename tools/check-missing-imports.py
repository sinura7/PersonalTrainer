#!/usr/bin/env python3
"""Find project symbols that are used but never imported.

This is the mirror image of check-internal-imports.py, and it exists because that tool
has a blind spot it cannot close from its own side. It validates the imports a file
*has*; a symbol a file uses but never imports leaves no import line to validate, so it
is invisible there and still fails the build in Android Studio.

That blind spot cost this project twice: `LaunchedEffect` used with no import in
HomeScreen and ScheduleScreen, and `Surface1` used with no import in
ExerciseDetailScreen. Both were caught by a human running a real compiler, which is the
loop this toolchain exists to shorten.

Two passes, because the two real misses were different kinds of symbol:

  project   Names declared in this project. `Surface1` lives in ui/theme and was used in
            ExerciseDetailScreen with no import.
  external  Names the project itself imports from outside it. There is no Android SDK
            here to enumerate the framework, so the project's own import lines are used
            as the dictionary: if twenty files import `LaunchedEffect` from
            androidx.compose.runtime, a twenty-first file using the bare name without
            importing it is the same bug. This is a convention check, not a compiler —
            a symbol the project has never imported anywhere is invisible to it.

  constant  Both passes above share one blind spot: they can only flag a name they have
            seen somewhere. A name that resolves to *nothing* — declared in no file and
            imported by none — is simply absent from both dictionaries and gets a free
            pass. That is precisely what happens when a composable is lifted out of one
            file into another and its `private val` dimensions are left behind: the new
            file references TODAY_MARKER_WIDTH, no file declares it visibly, and the two
            passes above see an unknown name rather than a broken one. This pass closes
            it for SCREAMING_SNAKE_CASE names, which in this project are always top-level
            or companion constants and never a receiver member or a bound parameter — so
            "declared in no visible scope" means "will not compile", with no ambiguity to
            trade off against. It cost a real compile break in WeekStrip.kt.

Restrictions that keep it quiet enough to be worth running:

  private    A private top-level declaration is visible only inside its own file, so it
             can never be the thing another file failed to import.
  source set Main cannot see test. A helper named `session` in a test file must not make
             every `session` lambda parameter in main look like a missing import.
  bindings   Named arguments, lambda parameters, declarations and value parameters are
             all names being *introduced*, not symbols being referenced.
  ambiguous  A few names are sometimes an import and sometimes supplied by a receiver
             scope or the language. See ALWAYS_IN_SCOPE.

Usage:  tools/check-missing-imports.py [main-root [test-root]]
Exit code is the number of missing imports.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files, strip_comments_and_strings  # noqa: E402

MAIN_ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"
TEST_ROOT = sys.argv[2] if len(sys.argv) > 2 else "app/src/test/java"
PACKAGE_PREFIX = "com.sinura.personaltrainer"

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)", re.M)
IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+?)(\.\*)?(?:\s+as\s+(\w+))?\s*$", re.M)

TOP_DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"((?:public\s+|internal\s+|private\s+|expect\s+|actual\s+|open\s+|abstract\s+|sealed\s+"
    r"|data\s+|value\s+|enum\s+|annotation\s+|inline\s+|suspend\s+|external\s+)*)"
    r"(?:class|object|interface|fun|val|var|typealias)\s+"
    r"(?:<[^>]*>\s+)?"
    r"(?:[\w.<>?]+\.)?"          # receiver on an extension
    r"(\w+)",
    re.M,
)

# Names a file introduces rather than references. Any of these shadowing a project
# symbol is normal Kotlin, not a missing import.
ANY_DECL_RE = re.compile(r"\b(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(\w+)")
VALUE_PARAM_RE = re.compile(r"[(,]\s*(?:@\w+\s+)*(?:vararg\s+|noinline\s+|crossinline\s+)?(\w+)\s*:")
# Bounded to one line and to identifier/comma runs: an unbounded [\w\s,]+? between
# "{" and "->" backtracks across whole files and effectively never returns.
LAMBDA_PARAM_RE = re.compile(
    r"\{[^\S\n]*\(?[^\S\n]*"
    r"([A-Za-z_]\w*(?:[^\S\n]*,[^\S\n]*[A-Za-z_]\w*){0,7})"
    r"[^\S\n]*\)?[^\S\n]*->"
)
NAMED_ARG_RE = re.compile(r"\b\w+\s*=(?!=)")
DECL_LINE_RE = re.compile(r"^\s*(?:import|package)\s+.*$", re.M)
# A name preceded by a dot is member access, resolved by its receiver, not by an import.
USE_RE = re.compile(r"(?<![.\w@$])([A-Za-z_]\w*)")

# Names that are legitimately available without an import in some contexts and imported in
# others, which no regex can tell apart. Each is here because the project genuinely imports
# it somewhere *and* genuinely uses it un-imported somewhere else:
#   catch  the try/catch keyword vs kotlinx.coroutines.flow.catch
#   items  LazyListScope.items, in scope inside a LazyColumn block
#   size   Collection.size and Modifier.size, both reached through a receiver
# `map` is the same shape of ambiguity: `kotlinx.coroutines.flow.map` needs an import and
# `List.map` does not, and most files that use one also happen to import the other — so the
# dictionary learns the name and then flags the first file that only ever calls the stdlib one.
ALWAYS_IN_SCOPE = {"catch", "items", "size", "map"}

# A project constant: SCREAMING_SNAKE with at least one underscore.
CONSTANT_RE = re.compile(r"^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$")

# Two shapes of all-caps name are legitimately bare and must be subtracted first, or this
# pass reports a dozen non-bugs for every real one:
#
#   enum entries    An entry is in scope un-qualified inside its own enum's body, which is
#                   exactly where `when (this)` lives. LAST_30_DAYS and ESTIMATED_ONE_REP_MAX
#                   are entries, not constants, and look identical from out here.
#   inherited       A class extending something outside the project inherits that type's
#                   constants — START_STICKY comes from android.app.Service. Nothing here can
#                   enumerate a supertype it cannot read, so a file with a foreign supertype
#                   opts out of this pass rather than guessing.
ENUM_HEAD_RE = re.compile(r"\benum\s+class\s+\w+[^{]*\{")
SUPERTYPE_RE = re.compile(r"^\s*(?:\w+\s+)*class\s+\w+(?:<[^>]*>)?\s*(?:\([^)]*\))?\s*:\s*([\w.]+)", re.M)
ALLCAPS_RE = re.compile(r"\b[A-Z][A-Z0-9_]*\b")


def enum_entry_names(body):
    """All-caps names inside an enum body, which are in scope there without qualification."""
    names = set()
    for head in ENUM_HEAD_RE.finditer(body):
        depth, i = 0, head.end() - 1
        while i < len(body):
            if body[i] == "{":
                depth += 1
            elif body[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        names.update(ALLCAPS_RE.findall(body[head.end():i]))
    return names


def has_foreign_supertype(body, index):
    """True when the file extends a type this project does not declare."""
    return any(
        supertype.rsplit(".", 1)[-1] not in index
        for supertype in SUPERTYPE_RE.findall(body)
    )

# Kotlin keywords the usage regex would otherwise treat as identifiers.
KEYWORDS = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
    "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
    "true", "try", "typealias", "typeof", "val", "var", "when", "while", "by", "catch",
    "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import",
    "init", "param", "property", "receiver", "set", "setparam", "value", "where", "it",
}


def index_declarations(files):
    """symbol name -> set of packages that declare it, public and internal only."""
    index = {}
    for path in files:
        src = open(path, encoding="utf-8").read()
        pkg_match = PACKAGE_RE.search(src)
        if not pkg_match or not pkg_match.group(1).startswith(PACKAGE_PREFIX):
            continue
        body = strip_comments_and_strings(src)
        for modifiers, name in TOP_DECL_RE.findall(body):
            if "private" in modifiers:
                continue
            index.setdefault(name, set()).add(pkg_match.group(1))
    return index


def index_external(files):
    """simple name -> set of fully qualified names the project imports it under."""
    index = {}
    for path in files:
        raw = open(path, encoding="utf-8").read()
        for fqn, star, alias in IMPORT_RE.findall(raw):
            if star or alias or fqn.startswith(PACKAGE_PREFIX):
                continue
            index.setdefault(fqn.rsplit(".", 1)[-1], set()).add(fqn)
    return index


def bound_names(body):
    names = set(ANY_DECL_RE.findall(body)) | set(VALUE_PARAM_RE.findall(body))
    for group in LAMBDA_PARAM_RE.findall(body):
        names.update(part.strip() for part in group.split(",") if part.strip())
    return names


def scan(files, index, external):
    findings = []
    for path in sorted(files):
        raw = open(path, encoding="utf-8").read()
        pkg_match = PACKAGE_RE.search(raw)
        if not pkg_match:
            continue
        own_pkg = pkg_match.group(1)

        imported_names, star_packages = set(), set()
        for fqn, star, alias in IMPORT_RE.findall(raw):
            if star:
                star_packages.add(fqn)
            elif alias:
                imported_names.add(alias)
            else:
                imported_names.add(fqn.rsplit(".", 1)[-1])

        body = strip_comments_and_strings(raw)
        body = DECL_LINE_RE.sub("", body)
        local = bound_names(body) | enum_entry_names(body)
        check_constants = not has_foreign_supertype(body, index)
        body = NAMED_ARG_RE.sub(" ", body)

        seen = set()
        for name in USE_RE.findall(body):
            if name in seen or name in local or name in imported_names:
                continue
            if name in KEYWORDS or name in ALWAYS_IN_SCOPE:
                continue
            packages = index.get(name)
            if packages and not (own_pkg in packages or packages & star_packages):
                seen.add(name)
                findings.append((path, name, sorted(f"{p}.{name}" for p in packages)))
                continue
            if packages:
                continue
            candidates = external.get(name)
            if candidates and not any(c.rsplit(".", 1)[0] in star_packages for c in candidates):
                seen.add(name)
                findings.append((path, name, sorted(candidates)))
                continue
            # Nothing anywhere declares or imports this name. Harmless for most shapes —
            # the dictionaries are incomplete by construction — but a constant that
            # resolves nowhere is a compile error, not a gap in this tool's knowledge.
            if not candidates and check_constants and CONSTANT_RE.match(name) and not star_packages:
                seen.add(name)
                findings.append((path, name, ["declared in no visible scope"]))
    return findings


def main():
    main_files = kotlin_files(MAIN_ROOT) if os.path.isdir(MAIN_ROOT) else []
    test_files = kotlin_files(TEST_ROOT) if os.path.isdir(TEST_ROOT) else []
    if not main_files and not test_files:
        print(f"No Kotlin sources under {MAIN_ROOT} or {TEST_ROOT}", file=sys.stderr)
        return 0

    main_index = index_declarations(main_files)
    # Test code sees both source sets; main code sees only itself.
    test_index = dict(main_index)
    for name, packages in index_declarations(test_files).items():
        test_index.setdefault(name, set()).update(packages)

    external = index_external(main_files + test_files)

    findings = scan(main_files, main_index, external) + scan(test_files, test_index, external)

    for path, name, options in findings:
        print(f"{path}: '{name}' is used but never imported -> {' | '.join(options)}")

    print(f"\n{len(findings)} missing import(s)")
    return len(findings)


if __name__ == "__main__":
    sys.exit(main())
