# Scientific RNG literature assessment

Reviewed: 2026-09-08. Scope: noncryptographic sampling in Figaro on Java 17,
including independent Monte Carlo samples, MCMC chains and future parallel work.

Implementation follow-up: [versioned streams and Philox](RNG_STREAMS.md) now implement
the first two recommendations with local acceptance evidence. Descriptions of the
existing implementation below are the research snapshot at `5fb1dcf3`, before that
follow-up; the research findings and decision boundaries remain applicable.

## Decision in brief

**Retain the explicitly named L64X128MixRandom default for current Figaro workloads,
with Xoshiro256++ as an equally credible selectable alternative.** This is a
workload-specific engineering recommendation, not a finding that LXM is universally
faster or statistically superior. The literature assessment is complete for this
decision; future integration work is a separate milestone.

The earlier application benchmark alone did not justify the selection. This review
supplies the missing comparative research basis, including contrary evidence and
exact-variant distinctions. It does not retroactively make the earlier process adequate.

Recommended next implementation order:

1. Define a versioned logical-stream allocation/replay contract, with native Xoshiro
   jumping for fixed chains and deterministic LXM splitting where appropriate.
2. Evaluate the existing Apache **Philox4x64-10** implementation as the next
   structurally different backend. Compare Philox4x32-10 only when portability to
   32-bit/GPU consumers or measured JVM cost makes that distinction useful.
3. Keep **PCG64DXSM** on the qualified shortlist, but do not write a new Figaro PCG
   core simply to add it. Identify a maintained, appropriately licensed JVM provider
   before implementation. Our current PCG alternative is not this variant.

No engine, dependency, seed mapping or production default changes are made by this
assessment. Existing RNG APIs and benchmark evidence remain in the
[RNG guide](RNG_ASSESSMENT.md).

## Method and evidence standard

The starting point is original research and subsequent evaluations, not another
local randomness contest. This is a bounded technical literature assessment, not
an exhaustive systematic review of every PRNG. It covers the requested LXM, PCG,
Xoshiro/Xoroshiro and Mersenne Twister families, plus the counter-based alternative
directly relevant to Figaro's parallel roadmap.

Selection priorities, in order:

- Credible statistical analysis of the exact variant, including reported weaknesses.
- Appropriate stream construction and reproducibility for our execution model.
- Correct, maintained implementation and distribution transforms.
- End-to-end Figaro performance at comparable inferential accuracy.
- Dependency and maintenance cost, after the preceding requirements are satisfied.

Sources distinguish peer-reviewed papers, technical reports/preprints, maintainer
documentation, reproducible author-run comparisons and informal critique. An
algorithm author's test results are useful, but are not independent replications
merely because the battery was developed by another group. Papers by overlapping
authors are not independent endorsements either.

Passing a battery means the tested sequences and representations did not expose
the tested defects. It is neither proof of independence nor a certificate covering
every scientific calculation. Conversely, a known failure does not imply that all
past results from that generator are wrong.

## Exact variants: practical comparison

The last column contains this assessment's judgments. Periods describe engine
cycles or counter blocks, not quantities of trustworthy samples.

| Candidate | Construction / scale | Figaro status | Assessment |
| --- | --- | --- | --- |
| L64X128MixRandom | LCG plus xor-based state and strong mixer; period 2^64(2^128-1) | JDK backend, current default | Retain for general use; no universal quality advantage established |
| Xoshiro256++ / Xoshiro256** | Scrambled 256-bit xor-based engine; period 2^256-1 | JDK ++ is exposed; ** is not | ++ is a first-tier alternative for planned jump-separated chains |
| Xoroshiro128+ / Xoroshiro128++ | Smaller 128-bit engine; scrambler matters | Neither is exposed | Do not add plain + as a general default; ++ is credible but fills no pressing gap |
| PCG RXS-M-XS-64 | 64-bit evolving LCG state and output; period 2^64 per increment | Commons RNG 1.7 backend | Explicit comparisons; not the preferred large-scale stream backend |
| PCG CM DXSM 128/64 | 128-bit evolving LCG state, DXSM output; period 2^128 | Not exposed or found in the installed Commons RNG provider set | Qualified future alternative, not represented by existing PCG timings |
| MT19937 | 32-bit-output F2-linear recurrence; period 2^19937-1 | Commons Math 3.6.1 backend | Historical/reference use, not the new default |
| Philox4x64-10 / Philox4x32-10 | Keyed counter transformation; 256-/128-bit counter blocks | Both exist in installed Commons RNG 1.7; neither is exposed by Figaro | Next backend candidate for explicit stream addressing |
| Threefry, e.g. 4x64-20 | Keyed add/rotate/xor counter transformation | Not exposed | Credible alternative; no need to add alongside Philox without a specific advantage |
| Legacy java.util.Random | 48-bit LCG | Explicit replay backend | Historical replay only |

Properties are documented in the [Java specification][java], [PCG report][pcg],
[DXSM specification][dxsm], [Apache source][apache-source], and [Random123 paper][random123].
Fixed parameters and buffers consume additional storage: LXM's 192 evolving bits
exclude its fixed LCG parameter; PCG's seed-array length is not its recurrence size.

## Findings by family

### LXM: substantial evidence, with qualified parallel claims

Steele and Vigna's peer-reviewed 2021 paper analyzes period/equidistribution and
reports over 52,000 PractRand and 50,000 BigCrush runs **across investigated variants**,
not all against the exact Figaro engine. Section 7 covers multiple initialization
strategies and stream counts up to 2^24. Strongly mixed variants performed well;
reduced-width and unmixed variants exposed weaknesses. Its headline comparative
robustness claim concerns SplitMix, not proof that LXM beats Xoshiro256++ or DXSM.
[Paper, sections 6-7][lxm]

The JDK recommends L64X128MixRandom for general use, jumpable or splittable engines
for fixed batches of workers, and L128 variants when dynamically creating millions
of generators. The L64 recommendation has a workload boundary; it is not a blanket
massively parallel prescription. [Java guidance][java]

A November 2024 PractRand forum response raises possible gap-test and extreme
multi-stream concerns about the combining/mixing structure, but supplies no
reproduced failure. The retrieved response lacks clear author attribution: it is
**informal critique**, not attributed here to PractRand's author or treated as a
verified defect. [Discussion][lxm-critique]

### Xoshiro and Xoroshiro: do not conflate scramblers

Blackman and Vigna's peer-reviewed 2021 analysis reports no systematic BigCrush
failures for Xoshiro256++/** in tested configurations. Its Hamming-weight tests
also distinguish these from weaker variants. It recommends both 256-bit variants
for general use. Plain Xoroshiro128+ exhibits detectable Hamming-weight dependence
after about 5 TB, and plain + scramblers have weak low-bit linear complexity.
Those findings cannot be transferred to ++/**. [Paper, table 1 and section 5.3][xoshiro]

For Figaro, ++ is already implemented and tested. Choosing ** just because it has
a different equidistribution count would not establish better MCMC performance.
Jump-separated streams are attractive, but nonoverlap is not proof of independence.
The JDK exposes jumping; Figaro currently does not use it. [Jump contract][jump]

### PCG: supported family, noninterchangeable variants

O'Neill's 2014 technical report develops permutation-based output, statistical
headroom and stream selection. Sections 6.1 and 6.3.4 give RXS-M-XS strong results
in reduced-state tests, but are not a ranking against later LXM or DXSM. The
author's accompanying errata distinguishes distinct streams from potentially
similar streams. [Report][pcg], [author's qualifications][pcg-errata]

The RXS-M-XS-64 provider warns that changing only the increment portion of its
seed can yield highly correlated sequences. Figaro changes both seed words through
its pinned expansion; this avoids that literal increment-only recipe, but does not
establish independence. The warning is present in the published 1.7 source, not
just older online Javadoc. [Apache source][apache-source]

PCG64DXSM is specifically **PCG CM DXSM 128/64**, with different state width,
output transformation and advancement details from our backend. NumPy documents
the parallel weaknesses of its earlier PCG64 and the DXSM correction. The concern
principally involves enormous collections of long streams, not ordinary
single-stream use. [Specification][dxsm], [upgrade analysis][dxsm-upgrade]

Vigna's reproducible comparison includes NumPy PCG64-DXSM with no reported
systematic BigCrush or Hamming-weight failure. This is evaluation outside PCG's
originating group, though still a researcher's comparison, not certification.
It strengthens DXSM's candidacy; it does not predict unmeasured JVM performance.
[Comparison and methodology][shootout]

### Mersenne Twister: historical importance versus present suitability

Matsumoto and Nishimura's 1998 work established MT's long-period, high-dimensional
equidistribution design. Later limitations do not erase that contribution. The
original PDF endpoint was unavailable during this review; its bibliographic record
is available from the authors. No new finding here depends on an unread section of
the original paper. [Author bibliography][mt-original]

For our **32-bit-output MT19937**, Harase analyzes how concatenation into
higher-precision outputs interacts with linear relations, reporting failures under
specific lag selections. This is relevant to Double-valued APIs, but is not a
reproduction against Figaro's Commons Math transformation.
[Harase, revised 2018 manuscript][mt-harase]

NumPy's current performance guidance advises against selecting MT19937 for new
use, citing test failures and performance, while preserving historical replay.
Our conclusion is the same for Figaro; it does not invalidate every MT-based
analysis. [NumPy guidance][numpy-performance]

### Philox and Threefry: a different parallel architecture

Salmon, Moraes, Dror and Shaw's SC11 paper evaluates counter-based generators using
Crush batteries, including keyed, blocked and strided stream constructions.
Recommended round counts retain a margin beyond minimum tested passing counts.
Its hardware-specific recommendations were Threefry4x64-20 on evaluated CPUs
without AES-NI and Philox4x32-10 on GPUs. The 2011 throughput rankings are not modern
JVM predictions. [Paper, sections 2.2.1, 4-5][random123]

A logical address can select a random block, supporting scheduling-independent
work without advancing a shared sequential engine. However, we must prevent address
reuse and explicitly handle rejection-sampler attempts. Scalar outputs may
legitimately be equal: avoiding address reuse does not forbid random-value
collisions. Distinct keys do not imply disjoint output-value sets.
[Reference library and API][random123-code]

Commons RNG 1.7 source confirms ten-round Philox4x32/4x64, each with a six-word
constructor: two key words then four counter words. Counter words are not scrambled.
Arbitrary-jump and saved-state APIs exist. Philox4x64-10 is thus an actionable JVM
candidate without a new dependency, not an already integrated Figaro capability.
[Published source archive][apache-source]

## Corroboration and limits

LXM and Xoshiro share an author and xor-based building blocks. Their comparison is
useful, but not maximally structurally different. PCG and Philox offer alternative
structures. None replaces an analytic posterior or independent numerical reference.

Evidence is uneven: substantial author-run LXM testing, independent MT analysis,
maintainer-documented PCG64 remediation and a DXSM comparison outside its originating
group. This review did not identify one independent study comparing all exact
candidates under identical transforms, stream policies and hardware. A universal
statistical league table is therefore unjustified. Do not rank raw p-value counts
from different publications.

The recent-literature check included Vigna's June 2026 **preprint** on modular-rank
and linear-complexity tests. Its new reported failures concern MIXMAX, not our
chosen LXM, Xoshiro256++ or PCG64DXSM candidates. It illustrates why previous
battery passes cannot close the question forever; it is not evidence those other
engines failed. [Preprint][modular-tests]

## Mapping the research to today's Figaro

Read-only inspection of `SamplingRandom.scala` and the owned parallel runners found:

- Named JDK LXM/Xoshiro engines and native Apache PCG/MT implementations.
- Private engines per owned chain/worker, with child seeds assigned using
  SplittableRandom. No native LXM split, Xoshiro jump or Philox address allocation.
- A Long root seed: expanding it into more words does not create more entropy.
- Different nonuniform transforms across providers. The previous Gaussian timing
  improvement is a backend-plus-transform result, not an intrinsic engine ranking.
- A synchronized compatibility adapter: protection for individual fallback calls,
  not for a mutable model shared across threads.
- Provenance text, but no automatic persisted result provenance or portable Figaro
  checkpoint format. Provider state APIs do not supply those features by themselves.

The Commons RNG 1.7 core JAR was inspected without running a simulation. It contains
Philox4x32, Philox4x64 and PcgRxsMXs64; no PCG64DXSM or Threefry implementation was
found. SHA-256:
`eed60a70ad92ca6245699c5631d531da9c748b1df68ebd343218b2c8094f9218`.
The published source archive was read in memory to verify rounds, seed layout and
the PCG warning. Retrieved Javadoc was inconsistent with the installed release, so
absence from that page was not treated as proof of absence from the dependency.

The existing [application study](STATISTICAL_VALIDATION.md) remains useful: the
diagnosed importance-weight collapse persists across all five current backends.
It does not certify them or determine a universal default. No statistical battery
or application benchmark was rerun for this literature milestone.

## Implementation gates, not research to repeat

Before integrating stream allocation or Philox, specify and verify:

1. **Replay identity.** Engine, provider/transform versions, seed interpretation,
   allocation version, logical chain/sample ID and budgets. Preserve explicit replay
   of existing experiments instead of silently redefining a saved seed.
2. **Allocation invariants.** Allocate by logical task, not physical thread or race
   order. For jumps, bound raw consumption below reserved intervals and prevent
   wraparound/reuse. Distribution draws consume variable numbers of raw words.
   For Philox, specify key/counter/lane layout, carries, attempt indices, overflow
   and checkpoint state. A new RNG enum alone is insufficient.
3. **Implementation agreement.** Published known-answer vectors and native provider
   sequences, including word order, initial-counter convention, bounds, reseeding,
   Gaussian caches and restore behavior. These are integration checks, not attempts
   to rediscover the engine's statistical properties.
4. **Execution invariance.** Identical logical streams under worker-count changes,
   scheduling, cancellation/restart and nested use, within a stated contract. RNG
   invariance alone cannot ensure identical floating-point reductions or model safety.
5. **Targeted performance, if adding a backend.** Reuse the harness and fixed
   inference references; compare initialization, adapter-inclusive draws and
   representative inference at matched accuracy. Add JVM forks/repetitions only
   when a small timing difference would change a decision.

Do not rerun published terabyte-scale batteries to tick a box. Reopen that question
only for changed algorithms, missing implementation correspondence, uncovered
stream layouts, conflicting evidence or a reproducible unexplained anomaly.
State the hypothesis and stopping budget before running such a study.

## Why keep LXM, and what would change the recommendation?

Current workloads need a credible general-purpose engine with reliable owned
streams. LXM satisfies that baseline, Xoshiro256++ does too, and existing local
measurements do not materially distinguish their end-to-end value. There is no
evidence-based reason to force another default-sequence change solely to reverse
the earlier inadequately justified decision. Retaining LXM is a positive but
bounded recommendation, not an appeal to implementation convenience.

For fixed-chain parallelism, this assessment favors adding explicit Xoshiro jump
allocation first because the stream-spacing contract is straightforward to audit.
For dynamic splitting, LXM remains attractive. For stable random access by logical
sample identity, Philox is the better architectural candidate to evaluate. These
are different requirements, not contradictory rankings of randomness quality.

Reconsider the default for a reproduced defect applying to our exact use,
substantially larger stream counts, a hard portable-replay requirement, or an
integrated alternative materially improving matched-accuracy performance. No RNG
choice compensates for incorrect likelihoods, proposal mismatch, inadequate mixing
or invalid stopping rules.

## Sources and related work

These sources do not authorize copying code without checking its license. No
paywalled PDF is needed for the bounded decision. The unavailable original MT PDF
would be useful for historical study but does not block the modern comparison.

- [RNG user/API guide](RNG_ASSESSMENT.md)
- [Statistical validation](STATISTICAL_VALIDATION.md)
- [Parallel performance](PARALLEL_PERFORMANCE.md)
- [Modernization roadmap](../ROADMAP.md)

[lxm]: https://vigna.di.unimi.it/ftp/papers/LXM.pdf
[java]: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html
[lxm-critique]: https://sourceforge.net/p/pracrand/discussion/366935/thread/5e92807d15/
[xoshiro]: https://vigna.di.unimi.it/ftp/papers/ScrambledLinear.pdf
[jump]: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/RandomGenerator.JumpableGenerator.html
[pcg]: https://www.cs.hmc.edu/tr/hmc-cs-2014-0905.pdf
[pcg-errata]: https://www.pcg-random.org/paper.html
[dxsm]: https://numpy.org/doc/stable/reference/random/bit_generators/pcg64dxsm.html
[dxsm-upgrade]: https://numpy.org/doc/stable/reference/random/upgrading-pcg64.html
[shootout]: https://prng.di.unimi.it/
[mt-original]: https://www.math.sci.hiroshima-u.ac.jp/m-mat/MT/ARTICLES/earticles.html
[mt-harase]: https://arxiv.org/abs/1708.06018
[numpy-performance]: https://numpy.org/doc/stable/reference/random/performance.html
[random123]: https://www.thesalmons.org/john/random123/papers/random123sc11.pdf
[random123-code]: https://github.com/DEShawResearch/random123
[apache-source]: https://repo.maven.apache.org/maven2/org/apache/commons/commons-rng-core/1.7/commons-rng-core-1.7-sources.jar
[modular-tests]: https://arxiv.org/abs/2606.22684
