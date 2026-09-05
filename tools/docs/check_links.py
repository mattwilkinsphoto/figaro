#!/usr/bin/env python3
"""Check local Markdown file targets in current user documentation (no network)."""
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit


def local_targets(text):
    """Yield decoded local inline-link paths, ignoring code fences and URL schemes."""
    text = re.sub(r"^```[^\n]*\n.*?^```\s*$", "", text, flags=re.M | re.S)
    text = re.sub(r"(`+).*?\1", "", text, flags=re.S)
    for match in re.finditer(r"(?<!!)\[[^\]\n]*\]\((<[^>\n]+>|[^\s)]+)\)", text):
        target = match.group(1).strip("<>")
        parsed = urlsplit(target)
        if not parsed.scheme and not parsed.netloc and parsed.path:
            yield unquote(parsed.path)


def main():
    """Check maintained guides/reference; print counts, returning 1 for bad targets."""
    root = Path(__file__).resolve().parents[2]
    paths = [root / name for name in (
        "README.md", "Figaro/README.md", "FigaroExamples/README.md",
        "tools/docs/README.md", "MODERNIZATION.md", "DEPENDENCIES.md", "CONSUMER_BOUNDARY.md")]
    paths += sorted((root / "docs").rglob("*.md"))
    errors, count = [], 0
    for path in paths:
        for target in local_targets(path.read_text(encoding="utf-8")):
            count += 1
            if not (path.parent / target).resolve().exists():
                errors.append(f"{path.relative_to(root)}: {target}")
    if errors:
        print("Missing local documentation targets:\n" + "\n".join(errors))
        return 1
    print(f"Verified {count} local link targets in {len(paths)} Markdown files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
