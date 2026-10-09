# -*- coding: utf-8 -*-
"""Add @author yangdb to product source files."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AUTHOR = "yangdb"
JAVA_ROOTS = [
    ROOT / "super-employee-platform" / "super-employee-app" / "src",
    ROOT / "super-employee-platform" / "super-employee-core" / "src",
]
WEB_ROOT = ROOT / "super-employee-web" / "src"

TYPE_RE = re.compile(
    r"(?m)^(?:(?:public|protected|private|abstract|final|sealed|non-sealed|static)\s+)*"
    r"(?:class|interface|enum|record)\s+\w+"
)


def annotation_block_start(text: str, type_start: int) -> int:
    pos = type_start
    ann_start = type_start
    while True:
        line_start = text.rfind("\n", 0, pos) + 1
        line = text[line_start:pos].strip()
        if line.startswith("@") or line == "":
            ann_start = line_start
            if line_start == 0:
                break
            pos = line_start - 1
            continue
        break
    return ann_start


def add_author_java(text: str) -> tuple[str, bool]:
    if re.search(r"@author\s+yangdb\b", text):
        return text, False
    m = TYPE_RE.search(text)
    if not m:
        return text, False
    type_start = m.start()
    before = text[:type_start]
    end_javadoc = before.rfind("*/")
    if end_javadoc < 0:
        ann_start = annotation_block_start(text, type_start)
        block = f"/**\n * @author {AUTHOR}\n */\n"
        return text[:ann_start] + block + text[ann_start:], True

    start_javadoc = before.rfind("/**", 0, end_javadoc)
    if start_javadoc < 0:
        ann_start = annotation_block_start(text, type_start)
        block = f"/**\n * @author {AUTHOR}\n */\n"
        return text[:ann_start] + block + text[ann_start:], True

    between = before[end_javadoc + 2 :]
    for line in between.splitlines():
        s = line.strip()
        if s and not s.startswith("@"):
            ann_start = annotation_block_start(text, type_start)
            block = f"/**\n * @author {AUTHOR}\n */\n"
            return text[:ann_start] + block + text[ann_start:], True

    javadoc = text[start_javadoc : end_javadoc + 2]
    if "@author" in javadoc:
        if "yangdb" in javadoc:
            return text, False
        new_javadoc = javadoc[:-2].rstrip() + f"\n * @author {AUTHOR}\n */"
        return text[:start_javadoc] + new_javadoc + text[end_javadoc + 2 :], True

    inner = javadoc[3:-2].rstrip()
    if inner:
        inner = "\n" + inner + "\n"
    else:
        inner = "\n"
    new_javadoc = "/**" + inner + f" * @author {AUTHOR}\n */"
    return text[:start_javadoc] + new_javadoc + text[end_javadoc + 2 :], True


def add_author_web(path: Path, text: str) -> tuple[str, bool]:
    if re.search(r"@author\s+yangdb\b", text) or "作者 yangdb" in text:
        return text, False
    if path.suffix == ".vue":
        return f"<!-- @author {AUTHOR} -->\n" + text, True
    if path.suffix in {".js", ".ts"}:
        return f"/** @author {AUTHOR} */\n" + text, True
    return text, False


def main() -> None:
    java_changed = java_total = 0
    for root in JAVA_ROOTS:
        if not root.exists():
            continue
        for path in root.rglob("*.java"):
            java_total += 1
            raw = path.read_text(encoding="utf-8")
            new, changed = add_author_java(raw)
            if changed:
                path.write_text(new, encoding="utf-8", newline="\n")
                java_changed += 1

    web_changed = web_total = 0
    if WEB_ROOT.exists():
        for path in WEB_ROOT.rglob("*"):
            if path.suffix not in {".vue", ".js", ".ts"}:
                continue
            web_total += 1
            raw = path.read_text(encoding="utf-8")
            new, changed = add_author_web(path, raw)
            if changed:
                path.write_text(new, encoding="utf-8", newline="\n")
                web_changed += 1

    print(f"java: {java_changed}/{java_total} updated")
    print(f"web:  {web_changed}/{web_total} updated")


if __name__ == "__main__":
    main()
