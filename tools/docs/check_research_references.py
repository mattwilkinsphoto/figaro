"""Read-only scientific-index registration and reciprocal-link checks (Python 3.10+)."""
from pathlib import Path, PurePosixPath
import posixpath
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
INDEX = "docs/RESEARCH_REFERENCES.md"
ANCHOR = re.compile(r'<a id="([a-z]+-\d+)"></a>')
LINK = re.compile(r"\]\(([^)]+)\)")
BACKLINK = re.compile(r"RESEARCH_REFERENCES\.md#([a-z]+-\d+)")


def validate(text, documents, exists):
    """Return actionable errors; inputs/callback permit filesystem-free negative tests."""
    errors = []
    matches = list(ANCHOR.finditer(text))
    ids = [match.group(1) for match in matches]
    if not ids:
        return ["Research index has no reference groups"]
    if len(ids) != len(set(ids)):
        errors.append("Research index contains duplicate reference IDs")
    headings = re.findall(r"^### ([A-Z]+-\d+):", text, re.MULTILINE)
    if [heading.lower() for heading in headings] != ids:
        errors.append("Reference headings and stable anchors disagree")
    for target in re.findall(r"\]\(#([a-z]+-\d+)\)", text):
        if target not in ids:
            errors.append(f"Index navigation points to unknown reference {target}")
    guides = {}
    for i, match in enumerate(matches):
        ref = match.group(1)
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        block = text[match.end():end]
        fields = {}
        for field in ("Source", "Role", "Code", "Validation", "Guide"):
            found = re.findall(rf"^\*\*{field}:\*\* (\S[^\r\n]*)", block, re.MULTILINE)
            if len(found) != 1:
                errors.append(f"{ref}: expected one nonempty {field} field")
            else:
                fields[field] = found[0]
        if "Source" in fields and "https://" not in fields["Source"]:
            errors.append(f"{ref}: Source needs a stable HTTPS reference")
        for field in ("Code", "Validation", "Guide"):
            targets = LINK.findall(fields.get(field, ""))
            local_guides = 0
            for target in targets:
                if target.startswith(("https://", "http://", "#")):
                    continue
                path = posixpath.normpath("docs/" + target.split("#", 1)[0])
                if not exists(path):
                    errors.append(f"{ref}: missing {field} target {path}")
                if field == "Guide" and path.endswith(".md"):
                    guides.setdefault(path, set()).add(ref)
                    local_guides += 1
            if field == "Guide" and not local_guides:
                errors.append(f"{ref}: Guide must link a local Markdown explanation")
    for path, refs in guides.items():
        backlinks = set(BACKLINK.findall(documents.get(path, "")))
        if not backlinks.intersection(refs):
            expected = ", ".join(sorted(refs))
            errors.append(f"{path}: add a reciprocal RESEARCH_REFERENCES.md backlink to {expected}")
    for path, body in documents.items():
        if path == INDEX:
            continue
        for ref in BACKLINK.findall(body):
            if ref not in ids:
                errors.append(f"{path}: backlink points to unknown reference {ref}")
        name = PurePosixPath(path).name.upper()
        if path.startswith("docs/") and ("RESEARCH" in name or "LITERATURE" in name) and path not in guides:
            errors.append(f"{path}: research/literature note is not registered in the index")
    return errors


def check_repository(root=ROOT):
    index = root / INDEX
    if not index.is_file():
        return [f"Missing {INDEX}"]
    paths = [root / "README.md"]
    paths.extend(path for path in (root / "docs").rglob("*.md")
                 if not {"api", "archive"}.intersection(path.relative_to(root / "docs").parts))
    documents = {path.relative_to(root).as_posix(): path.read_text(encoding="utf-8")
                 for path in paths if path.is_file()}
    return validate(index.read_text(encoding="utf-8"), documents,
                    lambda path: (root / path).is_file())


def main():
    errors = check_repository()
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("Research reference registration, metadata, local mappings and reciprocal guide links verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
