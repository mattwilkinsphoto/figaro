# Documentation bridge and preservation audit

## Decision

Keep the original teaching material as history, and maintain a short bridge to the
modernized implementation. We do not need a second transcription of the old books.
Use the [current setup](../README.md), [migration changes](MIGRATION.md) and
[API reference](api/README.md) for executable commands, compatibility and signatures.
Use the archived tutorials for the deeper explanations, with the corrections below.

| Legacy item | Assessment and action | Current entry point |
| --- | --- | --- |
| Root `figaro_2.12-5.0.0.0-javadoc.jar` | Removed: generated Scala 2.12/Figaro 5 API, not this Scala 3 library; recoverable from Git history | [API guide](API_GUIDE.md), generated Scala 3 HTML and [method reference](api/README.md) |
| `doc/Figaro Quick Start Guide.pdf` (22 pages) | Archived unchanged; installation instructions are superseded, introductory models retained | [Setup](../README.md), [tested classic walkthroughs](TUTORIAL.md) |
| `doc/Figaro Release Notes.pdf` (5 pages) | Archived unchanged: valuable Figaro 5 history, not a modernization release announcement | [Current release notes](RELEASE_NOTES.md) and [migration changes](MIGRATION.md) |
| `doc/Figaro Tutorial.pdf` (95 pages) | Archived unchanged; substantial modeling and extension material remains useful | [Modeling companion](LEGACY_MODELING_GUIDE.md) and chapter map below |
| `FigaroLaTeX/Tutorial/FigaroTutorial.pdf` (109 pages) | Archived separately: a different edition, including structured inference and experimental material | Same bridge; do not discard this as a duplicate |
| `FigaroLaTeX/FigaroGuide/FigaroGuide.tex` | Original Figaro 3 source preserved under `FigaroLaTeX/archive`; active guide replaced by a concise modernization bridge | [LaTeX source guide](../FigaroLaTeX/README.md) |
| `FigaroLaTeX/Tutorial` chapter sources | Preserved, with a new unnumbered modernization preface and title-page context | [Shared modernization preface](../FigaroLaTeX/Modernization.tex) |

All four original PDFs are in the [archive](../doc/archive/README.md). Their bytes and
original notices are unchanged; the preservation test records their SHA-256 digests.
The obsolete JAR and checked-in `.aux` build intermediate are removed, not the underlying
Scala source or tutorial chapters. Their previous versions remain in Git history.
The historical `ScalaDoc/` tree is outside this cleanup and is not the current API.

## Reading the old tutorial today

This is a topic-coverage audit, not a claim that every historical listing has been
compiled or every paragraph rewritten. The following map uses the retained LaTeX
chapter filenames so readers can locate the corresponding source. Chapters and page
numbers differ between the two PDF editions; follow topic names, not page offsets.

| Original chapter source | Material worth retaining | Modern bridge / cautions |
| --- | --- | --- |
| `1Introduction` | Probabilistic-programming motivation, model/inference separation | [User guide](USER_GUIDE.md); replace old installation instructions completely |
| `2HelloWorld` | Constant, uncertain greeting, first inference lifecycle | [Classic walkthrough](TUTORIAL.md), executable Scala 3 checks and cleanup |
| `3FigaroRepresentation` | Atomic/compound elements, Chain, Apply, processes and containers | [Modeling companion](LEGACY_MODELING_GUIDE.md); current overloads in API reference |
| `4CreatingModels` | Conditions/constraints, object relationships, mutable fields, universes, names/references/aggregates | Companion: evidence, identity and ownership; no automatic cache invalidation or shared-graph thread safety |
| `5Reasoning` | Ranges, VE, BP, lazy inference, importance/MH/Gibbs, proposals/debugging, SFI/LSFI strategies, evidence probability, MPE, dependent universes and abstractions | Companion retains the algorithm-selection and extension map; [parallel importance](PARALLEL_PERFORMANCE.md) and [multi-chain MCMC](MULTI_CHAIN_MCMC.md) are opt-in additions |
| `6DynamicModels` | Particle filtering and factored frontier over time-indexed models | Companion: filtering is not the same as independent-chain parallelism |
| `7Decisions` | Utility, single/multiple decisions, finite/infinite parent support and policy learning | Companion: distinguish expected utility from marginal probability |
| `8LearningParams` | Beta/Dirichlet parameters, EM, shared parameter collections | Companion: current EM names, shared identities and point-estimate limitations |
| `9HierReasoning` | Hierarchical/relational reasoning and open-universe models | Companion: retained examples; finite exact-inference assumptions do not cover every dynamic model |
| `10Elements` | Custom atomic/compound elements, sampling, factor conversion, parameterized elements and proposals | Companion: a sampling implementation alone does not establish factor support |
| `11CreateNew` | One-time/anytime algorithms, query categories, sampling/factors, learning, code sharing and extensions | Companion and [migration guide](MIGRATION.md): current lifecycle/concurrency contracts and Scala 3 syntax |
| `12Experimental` | Marginal MAP, collapsed Gibbs, normal proposals | Companion: research/experimental status is not a production or performance guarantee |
| `13Conclusion` | Original project perspective and further learning | [Current release notes](RELEASE_NOTES.md), [acceptance boundaries](CORE_PERFORMANCE_ACCEPTANCE.md) |

The companion points to the existing advanced examples and APIs instead of reproducing
the full chapters. It also preserves the learning paths for kernel density and curried
operations mentioned in the historical release notes. The original source remains the
place to read their longer derivations and implementation discussions.

## What is actually refreshed and checked

- `ClassicTutorials` ports Constant/Importance, Select/elimination and the original
  Burglary network. It checks probabilities, uses explicit cleanup and independently
  enumerates the Burglary posterior. CI runs it alongside the current onboarding models.
- Current guides replace obsolete FigaroWork, sbt 0.13 and Scala 2 dependency/install
  instructions. The full modernization compatibility changes remain in one migration guide.
- The LaTeX preface explains the new toolchain, optional parallel APIs, stopping safeguards,
  credits and the limits of using old listings. It does not silently relabel old content
  as fully ported Scala 3 material. Archived PDFs do not contain this new preface.
- Local-link checks include the new `doc` and `FigaroLaTeX` Markdown entry points.
  Preservation tests check the archived bytes and all thirteen chapter-map entries.
- Matthew Wilkins and Codex receive modernization credit in the attribution file,
  supplementing original authorship and unchanged license notices.

No new PDF is distributed by this cleanup. The LaTeX changes require a TeX installation
for typesetting; static checks are not a substitute for a rendered-PDF review. The
previously built `6.0.0-modern.10-rc.1` bundle is unchanged. New builds include the updated
attribution file; they must not be substituted under an existing bundle's checksum.
