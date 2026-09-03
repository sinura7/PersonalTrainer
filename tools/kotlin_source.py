"""Shared Kotlin source handling for the static checks in this directory."""
import os


def strip_comments_and_strings(src: str) -> str:
    """Blank comments and string literal *text*, preserving offsets and line numbers.

    String templates are the subtlety. `"${weight.toWeightLabel(unit)}"` contains real code —
    a call, with real arguments, referring to real imports — so blanking the whole literal
    hides it from every check built on this. An earlier version did exactly that and reported
    a dozen in-use imports as unused. Interpolations are therefore kept; only the literal text
    around them is blanked.
    """
    out = list(src)
    i, n = 0, len(src)

    def blank(a: int, b: int) -> None:
        for k in range(a, min(b, n)):
            if out[k] != "\n":
                out[k] = " "

    def scan_string(start: int, quote: str) -> int:
        """Blank literal text from `start`, stepping over `${...}`; return the index after it."""
        triple = quote == '"""'
        i = start + len(quote)
        while i < n:
            if not triple and src[i] == "\\":
                blank(i, i + 2)
                i += 2
                continue
            if src.startswith(quote, i):
                blank(i, i + len(quote))
                return i + len(quote)
            if src.startswith("${", i):
                # Keep the interpolation verbatim; find its matching brace.
                depth, j = 0, i + 1
                while j < n:
                    if src[j] == "{":
                        depth += 1
                    elif src[j] == "}":
                        depth -= 1
                        if depth == 0:
                            break
                    j += 1
                blank(i, i + 2)          # the `${`
                if j < n:
                    blank(j, j + 1)      # the `}`
                i = j + 1
                continue
            if src[i] == "$" and i + 1 < n and (src[i + 1].isalpha() or src[i + 1] == "_"):
                # Simple `$name` interpolation: keep the name, blank the sigil.
                blank(i, i + 1)
                i += 1
                while i < n and (src[i].isalnum() or src[i] == "_"):
                    i += 1
                continue
            blank(i, i + 1)
            i += 1
        return i

    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            blank(i, j)
            i = j
            continue
        if src.startswith('"""', i):
            blank(i, i + 3)
            i = scan_string(i, '"""')
            continue
        if src[i] == '"':
            blank(i, i + 1)
            i = scan_string(i, '"')
            continue
        if src[i] == "'":
            j = i + 1
            while j < n and src[j] != "'":
                if src[j] == "\\":
                    j += 1
                j += 1
            blank(i, j + 1)
            i = j + 1
            continue
        i += 1
    return "".join(out)


def kotlin_files(root: str) -> list:
    found = []
    for dirpath, _, names in os.walk(root):
        for name in sorted(names):
            if name.endswith(".kt"):
                found.append(os.path.join(dirpath, name))
    return found


def kotlin_files_in(roots: list[str]) -> list:
    found = []
    for root in roots:
        if os.path.isdir(root):
            found.extend(kotlin_files(root))
    return found


def xml_files_in(roots: list[str]) -> list:
    found = []
    for root in roots:
        if not os.path.isdir(root):
            continue
        for dirpath, _, names in os.walk(root):
            for name in sorted(names):
                if name.endswith(".xml"):
                    found.append(os.path.join(dirpath, name))
    return found
