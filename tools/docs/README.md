# Documentation maintenance tools

## Overview

These standard-library Python 3.10+ command-line tools turn Scala 3 compiler documentation into a browsable Markdown method reference and check its freshness/local links. They exist to keep the exhaustive inventory synchronized with actual overloads and inferred types. They are maintenance tooling, not part of the published Figaro API.

The reference contains every public `def` exposed on the library's `com.cra.figaro` Scaladoc pages. It excludes protected/private methods, preserves separately rendered inherited members, and includes compiler-generated public methods. It is not a count of unique handwritten functions. Fields, types, primary constructors, and inheritance remain in the full Scala 3 HTML site. Historical examples have their own `examples / Compile / doc` site; new onboarding functions have a handwritten [module reference](../../FigaroExamples/README.md#documentation-example-api-reference).

## Quick start

Run from the repository root:

1. `sbt "figaro / Compile / doc"`
2. `python -B tools/docs/build_reference.py`
3. `python -B -m unittest discover -s tools/docs -p "test_*.py"` followed by `python -B tools/docs/build_reference.py --check` and `python -B tools/docs/check_links.py`.

## CLI reference

| Command / argument | Input | Output / failure behavior | Example |
| --- | --- | --- | --- |
| `build_reference.py` | Generated Scala 3 HTML | Writes generated Markdown plus `inventory.json`; nonzero on parse errors, missing API, or unsafe overwrite | `python -B tools/docs/build_reference.py` |
| `--api-dir PATH` | API root containing `com/cra/figaro`; default `target/out/jvm/scala-3.9.0/figaro/api` | Changes the source HTML directory | `python -B tools/docs/build_reference.py --api-dir target/out/jvm/scala-3.9.0/figaro/api` |
| `--output PATH` | Dedicated generated directory; parent must exist; default `docs/api` | Writes the reference there; existing unrelated Markdown is not overwritten | `python -B tools/docs/build_reference.py --output docs/api` |
| `--check` | Existing output and generated HTML | Read-only; nonzero for missing, changed, or obsolete output files | `python -B tools/docs/build_reference.py --check` |
| `--acl-script PATH` | Optional trusted PowerShell script accepting `-Paths` with one absolute path | Runs immediately after each file/directory creation or change; hook failure stops generation | `python -B tools/docs/build_reference.py --acl-script C:/workspace/Grant-Access.ps1` with your actual access hook |
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

- Edit contracts in source Scaladoc and explanations in the handwritten guides. Generated call templates are **not runnable examples**: they assume an existing receiver, correctly typed arguments, and in-scope type parameters. The tested complete models live in the examples module. Missing behavioral prose is labeled explicitly; types alone cannot explain undocumented invariants.
- The extractor consumes Scala 3 HTML structure, so a compiler upgrade can require parser changes. Failures must be investigated, not bypassed by silently omitting methods. Review representative generic, contextual, overloaded, parameterless, varargs, operator, and auxiliary-constructor entries.
- Scaladoc gives some extension-method overloads the same HTML anchor (for example arithmetic/tuple extensions on `Element`). The reference preserves their distinct signatures; a full-entry link may land on the first overload. Identify the intended overload by its parameter and result types, not the anchor alone.
- Run generation from the repository root. Change the default API path and all documented artifact paths when changing compiler versions. Keep the generated HTML beside this checkout if using Markdown-to-HTML links locally; those links do not resolve in GitHub's viewer because build output is not committed.
- The link checker checks local **file existence**, not section anchors, arbitrary Markdown syntax, external URLs, or HTML content. Generate Scaladoc before running it. It deliberately does not audit the historical `ScalaDoc/` tree.
- Generation does not delete obsolete files. If a package disappears, inspect the obsolete generated files reported by `--check` and remove only those confirmed obsolete files.
- `-B` avoids creating Python bytecode cache files. On Windows with explicit workspace ACL requirements, supply a suitable `--acl-script`; the hook must preserve existing ACLs/ownership, grant the intended account access, and verify it. The generator prefers `pwsh` when available.

Internal Python functions/classes implement HTML parsing, signature decomposition, rendering, and link scanning. They are not a supported import API; their contracts are tested in the adjacent unit tests. The supported entry points are the CLI commands above.

## Related

[Build/CI guide](../../docs/BUILDING.md), [user guide](../../docs/USER_GUIDE.md), [public method reference](../../docs/api/README.md), and [runnable examples](../../FigaroExamples/README.md).
