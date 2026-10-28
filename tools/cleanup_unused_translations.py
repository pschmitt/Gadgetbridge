#!/usr/bin/env python3
"""
Remove strings from the non-English strings.xml files that do not have English versions anymore.
"""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
TAGS = ("string", "plurals", "string-array")

ELEMENT_RE = re.compile(
    r'^[ \t]*<(?P<tag>' + "|".join(re.escape(t) for t in TAGS) + r')\b[^>]*?\bname="(?P<name>[^"]+)"[^>]*?'
    r'(?:/>|>.*?</(?P=tag)\s*>)[ \t]*\r?\n?',
    re.MULTILINE | re.DOTALL,
)


def base_names(res_dir: Path) -> tuple[set[str], set[str]]:
    names = set()
    untranslatable = set()
    for xml_file in sorted((res_dir / "values").glob("*.xml")):
        root = ET.parse(xml_file).getroot()
        for element in root:
            if element.tag not in TAGS:
                continue
            name = element.get("name")
            names.add(name)
            if element.get("translatable") == "false":
                untranslatable.add(name)
    return names, untranslatable


def cleanup(path: Path, keep: set[str], dry_run: bool) -> list[str]:
    content = path.read_text(encoding="utf-8")
    removed = []

    def replace(match: re.Match) -> str:
        if match.group("name") in keep:
            return match.group(0)
        removed.append(match.group("name"))
        return ""

    new_content = ELEMENT_RE.sub(replace, content)
    if removed and not dry_run:
        path.write_text(new_content, encoding="utf-8")
    return removed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="print the strings that would be removed")
    parser.add_argument(
        "--untranslatable",
        action="store_true",
        help='also remove strings that have translatable="false" in English',
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="print the name of each removed string")
    args = parser.parse_args()

    names, untranslatable = base_names(RES_DIR)
    keep = names - untranslatable if args.untranslatable else names

    total = 0
    for path in sorted(RES_DIR.glob("values-*/strings.xml")):
        removed = cleanup(path, keep, args.dry_run)
        if not removed:
            continue
        total += len(removed)
        print(f"{path.relative_to(RES_DIR)}: {len(removed)}")
        if args.verbose:
            for name in removed:
                print(f"    {name}")

    action = "Would remove" if args.dry_run else "Removed"
    print(f"{action} {total} strings")
    return 0


if __name__ == "__main__":
    sys.exit(main())
