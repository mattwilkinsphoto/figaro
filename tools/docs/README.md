# Documentation maintenance tools

## Overview

These standard-library Python 3.10+ command-line tools turn Scala 3 compiler documentation into a browsable Markdown method reference and check its freshness/local links. They exist to keep the exhaustive inventory synchronized with actual overloads and inferred types. They are maintenance tooling, not part of the published Figaro API.

The reference contains every public `def` exposed on the library's `com.cra.figaro` Scaladoc pages. It excludes protected/private methods, preserves separately rendered inherited members, and includes compiler-generated public methods. It is not a count of unique handwritten functions. Fields, types, primary constructors, and inheritance remain in the full Scala 3 HTML site. Historical examples have their own `examples / Compile / doc` site; new onboarding functions have a handwritten [module reference](../../FigaroExamples/README.md#documentation-example-api-reference).

## Quick start

Scientific provenance is maintained separately from the generated API inventory.
Follow [CONTRIBUTING.md](../../CONTRIBUTING.md) and run
`python -B tools/docs/check_research_references.py` for reference registration,
metadata, local mappings and reciprocal guide links. This read-only check performs
no network requests and does not assess scientific correctness.

Run from the repository root:

1. `sbt "figaro / Compile / doc"`
2. `python -B tools/docs/build_reference.py`
3. `python -B -m unittest discover -s tools/docs -p "test_*.py"` followed by `python -B tools/docs/build_reference.py --check` and `python -B tools/docs/check_links.py`.

## CLI reference

### Standalone experiment report PDF

The checked-in [PDF](../../docs/reports/FIGARO_LITERATURE_EXPERIMENTS.pdf) is generated
from the [maintained Markdown report](../../docs/LITERATURE_EXPERIMENTS_REPORT.md).
It is repository documentation, included in GitHub source downloads; it is not a
new Maven classifier or a replacement of previously published artifact bytes.

The optional generator uses ReportLab 4.4.9 and Node's `marked` 17.0.5 parser. Install these
only in an isolated documentation environment; they are not runtime dependencies.
Make `marked` available on Node's module search path (including `NODE_PATH` when
installed outside the checkout), then run:

```text
python -B tools/docs/build_experiment_report.py --output report-review.pdf
pdftoppm -r 90 -png report-review.pdf report-review
```

The output and its `.json` manifest must not exist: preserve the old PDF while reviewing a regenerated
candidate. Optional `--source` selects a Markdown input; the default is the report
above. The companion `report_tokens.cjs` delegates Markdown parsing to `marked`.
On managed Windows workspaces, `FIGARO_ACCESS_HELPER` may identify the trusted
PowerShell grant hook, invoked immediately for generated paths. The renderer
supports the report's headings, paragraphs, emphasis, links, lists and three-column
summary table; it fails on unsupported token types rather than silently dropping
content. PDF links target public GitHub documents, not workstation paths.

When the report changes, regenerate its PDF in the same change, compare extracted
text against every source block, inspect all rendered pages, and verify links,
bookmarks, tables, page numbers and source-note status labels. `pypdf` and Poppler
are optional review tools. The ordinary documentation tests do not install these
packages or regenerate PDFs. Historical numerical experiments are not rerun merely
to change formatting. The original source-note hashes remain in the companion
[records](../../docs/reports/EXPERIMENT_RECORDS.md).

### Scala API documentation tools

| Command / argument | Input | Output / failure behavior | Example |
| --- | --- | --- | --- |
| `build_reference.py` | Generated Scala 3 HTML | Writes generated Markdown plus `inventory.json`; nonzero on parse errors, missing API, or unsafe overwrite | `python -B tools/docs/build_reference.py` |
| `--api-dir PATH` | API root containing `com/cra/figaro`; default `target/out/jvm/scala-3.9.0/figaro/api` | Changes the source HTML directory | `python -B tools/docs/build_reference.py --api-dir target/out/jvm/scala-3.9.0/figaro/api` |
| `--output PATH` | Dedicated generated directory; parent must exist; default `docs/api` | Writes the reference there; existing unrelated Markdown is not overwritten | `python -B tools/docs/build_reference.py --output docs/api` |
| `--check` | Existing output and generated HTML | Read-only; nonzero for missing, changed, or obsolete output files | `python -B tools/docs/build_reference.py --check` |
| `--acl-script PATH` | Optional trusted PowerShell permission hook accepting `-Paths` with one absolute path | Runs immediately after each file/directory creation or change; hook failure stops generation; not required for ordinary generation | `python -B tools/docs/build_reference.py --acl-script path/to/access-hook.ps1` (replace the placeholder with an existing script) |
| `check_links.py` | Maintained Markdown and built HTML, located relative to the script | Checks local inline-link file targets, prints counts, exits 1 on missing files; performs no network requests | `python -B tools/docs/check_links.py` |

`inventory.json` uses schema version 1. Each method records owner, name, compiler signature, type parameters, ordered parameter lists (with contextual flags), return type, source prose, invocation template, object/instance classification, HTML page, and anchor. Treat this as generator data, not a published model-serialization format.

## Three common patterns

```sh
# 1. Change an API: rebuild HTML, then regenerate the Markdown.
sbt "figaro / Compile / doc"
python -B tools/docs/build_reference.py

# 2. Review a documentation-only change without rewriting output.
python -B tools/docs/build_reference.py --check
python -B tools/docs/check_links.py

# 3. Change the parser or upgrade Scaladoc: exercise parser cases first.
python -B -m unittest discover -s tools/docs -p "test_*.py"
```

## Gotchas

- `test_legacy_documentation.py` verifies the four archived PDFs against their original
  SHA-256 digests, checks the thirteen-chapter bridge and shared LaTeX preface, and
  protects original and modernization attribution. It writes no files and does not
  compile LaTeX. `check_links.py` includes maintained Markdown in `doc/` and `FigaroLaTeX/`.
- Edit contracts in source Scaladoc and explanations in the handwritten guides. Generated call templates are **not runnable examples**: they assume an existing receiver, correctly typed arguments, and in-scope type parameters. The tested complete models live in the examples module. Missing behavioral prose is labeled explicitly; types alone cannot explain undocumented invariants.
- The extractor consumes Scala 3 HTML structure, so a compiler upgrade can require parser changes. Failures must be investigated, not bypassed by silently omitting methods. Review representative generic, contextual, overloaded, parameterless, varargs, operator, and auxiliary-constructor entries.
- API pages are ordered by case-sensitive relative-path strings on every platform. Sorting native `Path` objects instead gives a different order on Windows and Linux for names such as `INode` and `Index`, creating false freshness failures without an API change.
- Scaladoc gives some extension-method overloads the same HTML anchor (for example arithmetic/tuple extensions on `Element`). The reference preserves their distinct signatures; a full-entry link may land on the first overload. Identify the intended overload by its parameter and result types, not the anchor alone.
- Run generation from the repository root. Change the default API path and all documented artifact paths when changing compiler versions. Keep the generated HTML beside this checkout if using Markdown-to-HTML links locally; those links do not resolve in GitHub's viewer because build output is not committed.
- The link checker checks local **file existence**, not section anchors, arbitrary Markdown syntax, external URLs, or HTML content. Generate Scaladoc before running it. It deliberately does not audit the historical `ScalaDoc/` tree.
- Generation does not delete obsolete files. If a package disappears, inspect the obsolete generated files reported by `--check` and remove only those confirmed obsolete files.
- `-B` avoids creating Python bytecode cache files. The optional permission hook uses `pwsh` when available and otherwise falls back to `powershell`.
- Keep Figaro documentation self-contained: use repository-relative paths and portable examples, and exclude developer account names, workstation locations, and unrelated project details. Preserve Figaro provenance, dependency coordinates, and reproducible modernization evidence.

Internal Python functions/classes implement HTML parsing, signature decomposition, rendering, and link scanning. They are not a supported import API; their contracts are tested in the adjacent unit tests. The supported entry points are the CLI commands above.

## Related

[Build/CI guide](../../docs/BUILDING.md), [user guide](../../docs/USER_GUIDE.md), [public method reference](../../docs/api/README.md), and [runnable examples](../../FigaroExamples/README.md).
