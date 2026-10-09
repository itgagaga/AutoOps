# -*- coding: utf-8 -*-
"""Normalize @author yangdb javadoc formatting."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOTS = [
    ROOT / "super-employee-platform" / "super-employee-app" / "src",
    ROOT / "super-employee-platform" / "super-employee-core" / "src",
]

# Fix blank/space-only lines before @author yangdb inside javadoc
PATTERN = re.compile(
    r"(?ms)/\*\*(.*?)\n[ \t]*\n[ \t]*\* @author yangdb\n \*/"
)
SIMPLE = re.compile(
    r"(?ms)/\*\*(.*?)\r?\n[ \t]*\r?\n[ \t]*\* @author yangdb\r?\n \*/"
)


def fix_text(text: str) -> tuple[str, bool]:
    original = text
    # Collapse any whitespace-only lines immediately before "* @author yangdb"
    text = re.sub(
        r"(?m)([^\n]*\S[^\n]*)\n(?:[ \t]*\n)+([ \t]*\* @author yangdb\n)",
        r"\1\n\2",
        text,
    )
    # Also handle case where blank line is " \n" with spaces after *
    text = re.sub(
        r"(?m)(\* @author yangdb\n)[ \t]*\n([ \t]*\*/)",
        r"\1\2",
        text,
    )
    return text, text != original


def main() -> None:
    changed = total = 0
    for root in JAVA_ROOTS:
        for path in root.rglob("*.java"):
            total += 1
            raw = path.read_text(encoding="utf-8")
            new, ok = fix_text(raw)
            if ok:
                path.write_text(new, encoding="utf-8", newline="\n")
                changed += 1
    print(f"fixed: {changed}/{total}")


if __name__ == "__main__":
    main()
