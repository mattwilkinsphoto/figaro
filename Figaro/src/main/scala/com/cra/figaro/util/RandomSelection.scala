package com.cra.figaro.util

import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH,
  MultiChainVectorSliceSampler as MC, ParImportance, ParSampler, ParOneTime}
import com.cra.figaro.language.{Reference, Universe}

/** Transparent, versioned purpose-based defaults; no timing probes or runtime adaptation. */
object RandomSelection {
  /** V1 is immutable policy: general=LXM/seeded, fixed chains=Xoshiro/jumped,
    * counter ranges=Philox/partitioned. New recommendations require a new version.
    */
  enum Preset { case V1 }
  enum Purpose { case GeneralInference, FixedChains, CounterRanges }

  /** Explicit engine/allocation pair, still subject to purpose and requirement checks.
    * @param algorithm named backend
    * @param allocation versioned allocation policy
    */
  final case class Choice(algorithm: SamplingRandom.Algorithm, allocation: RandomStreams.Allocation) {
    require(algorithm != null && allocation != null, "Explicit backend and allocation required")
  }

  /** Hard requirements, not hints that may silently be ignored.
    * @param disjointIntervals require separated intervals within one allocation and its enforced budget;
    *                          not a proof of statistical independence across experiments
    * @param perSampleAddressing require logical sample addresses independent of worker partitions;
    *                            currently unsupported, even with Philox
    */
  final case class Requirements(disjointIntervals: Boolean = false, perSampleAddressing: Boolean = false)

  /** Immutable resolved selection. Persist these fields alongside run descriptors and model settings.
    * There is no automatic serialization format or mid-run checkpoint.
    */
  final class Decision private[RandomSelection] (
    val purpose: Purpose, val preset: Preset, val algorithm: SamplingRandom.Algorithm,
    val streams: RandomStreams.Config, val requirements: Requirements,
    val overridden: Boolean, val reason: String, val provider: String) {

    private def checkProvider(): Unit =
      require(provider == SamplingRandom.provenance(algorithm), "Selection provider/JDK mismatch")

    /** Allocate owned logical streams without selecting again.
      * @param rootSeed experiment root seed
      * @param count nonnegative logical stream count
      * @return index-ordered owned streams and replay descriptors
      * @example `resolve(Purpose.CounterRanges).allocate(42L, 4)`
      */
    def allocate(rootSeed: Long, count: Int): Vector[RandomStreams.Stream] = {
      checkProvider()
      RandomStreams.allocate(rootSeed, count, algorithm, streams)
    }

    /** Apply both resolved fields to graph-chain settings; all other settings remain unchanged.
      * @param config existing non-null MH settings; their RNG fields are explicitly replaced
      * @return copied config, without allocating streams or invoking callbacks
      * @example `resolve(Purpose.FixedChains).configure(MH.Config(chains = 4))`
      */
    def configure(config: MH.Config): MH.Config = {
      require(config != null, "MH config required")
      checkProvider()
      config.copy(randomAlgorithm = algorithm, randomStreams = streams)
    }

    /** Apply both resolved fields to vector-chain settings; work/seed/worker limits remain unchanged.
      * @param config existing non-null vector-chain settings
      * @return copied config with copied nested sampler settings
      * @example `selection.configure(MC.Config(sampler))`
      */
    def configure(config: MC.Config): MC.Config = {
      require(config != null, "Vector-chain config required")
      checkProvider()
      config.copy(sampler = config.sampler.copy(randomAlgorithm = algorithm), randomStreams = streams)
    }

    /** Build blocking importance with this resolved pair and existing ownership/lifecycle semantics.
      * @param generator fresh independent universe factory, called during construction
      * @param numThreads positive maximum worker count; changes still change sample partitions
      * @param numSamples positive total sample budget
      * @param seed experiment root seed
      * @param targets references resolved independently in each universe
      * @return one-time sampler with stream descriptors; start/query/kill in try/finally
      * @example `selection.importance(makeModel, 4, 10000, 42L, "query")`
      */
    def importance(generator: () => Universe, numThreads: Int, numSamples: Int, seed: Long,
      targets: Reference[?]*): ParSampler & ParOneTime & ParImportance.StreamProvenance = {
      require(generator != null, "Universe factory required")
      checkProvider()
      ParImportance.seededWithStreams(generator, numThreads, numSamples, seed, algorithm, streams, targets*)
    }
  }

  /** Resolve once before model construction. This is a policy choice, not a fastest/best-RNG proof.
    * @param purpose intended execution pattern, not the probability distribution family
    * @param preset pinned selection-policy version, V1 by default
    * @param explicit optional engine/allocation pair overriding the preset, never hard requirements
    * @param maxRawDraws positive per-stream raw-word cap; ignored by SeededV1
    * @param requirements hard guarantees; unsupported requests throw IllegalArgumentException
    * @return immutable decision with resolved pair, reason, override flag and provider/JDK identity
    * @example `RandomSelection.resolve(Purpose.CounterRanges, requirements = Requirements(disjointIntervals = true))`
    */
  def resolve(purpose: Purpose, preset: Preset = Preset.V1, explicit: Option[Choice] = None,
    maxRawDraws: Long = 1L << 48, requirements: Requirements = Requirements()): Decision = {
    require(purpose != null && preset != null && explicit != null && !explicit.contains(null) && requirements != null,
      "Purpose, preset, optional choice and requirements must be non-null")
    require(!requirements.perSampleAddressing,
      "Per-sample addressing is not implemented; Philox currently reserves ranges per logical stream")
    val recommended = (preset, purpose) match {
      case (Preset.V1, Purpose.GeneralInference) =>
        Choice(SamplingRandom.Algorithm.Lxm, RandomStreams.Allocation.SeededV1)
      case (Preset.V1, Purpose.FixedChains) =>
        Choice(SamplingRandom.Algorithm.Xoshiro256PlusPlus, RandomStreams.Allocation.PartitionedV1)
      case (Preset.V1, Purpose.CounterRanges) =>
        Choice(SamplingRandom.Algorithm.Philox4x64, RandomStreams.Allocation.PartitionedV1)
    }
    val choice = explicit.getOrElse(recommended)
    val streams = RandomStreams.Config(choice.allocation, maxRawDraws)
    streams.validate(choice.algorithm)
    require(purpose != Purpose.CounterRanges ||
      (choice.algorithm == SamplingRandom.Algorithm.Philox4x64 && choice.allocation == RandomStreams.Allocation.PartitionedV1),
      "CounterRanges requires Philox4x64 with PartitionedV1")
    require(purpose != Purpose.FixedChains || choice.allocation == RandomStreams.Allocation.PartitionedV1,
      "FixedChains requires native PartitionedV1 allocation; use GeneralInference for seeded compatibility")
    val separated = choice.allocation == RandomStreams.Allocation.PartitionedV1 &&
      Set(SamplingRandom.Algorithm.Xoshiro256PlusPlus, SamplingRandom.Algorithm.Philox4x64).contains(choice.algorithm)
    require(!requirements.disjointIntervals || separated,
      "Disjoint intervals require bounded Xoshiro jumps or Philox counter ranges; LXM splitting is not this guarantee")
    val rationale = if (explicit.nonEmpty) "Explicit engine/allocation override; purpose and requirements validated"
      else purpose match {
        case Purpose.GeneralInference => "Balanced LXM default with existing seeded allocation preserved"
        case Purpose.FixedChains => "Fixed logical chains use bounded native Xoshiro256++ jump intervals"
        case Purpose.CounterRanges => "Philox4x64-10 assigns bounded counter ranges to logical streams, not individual samples"
      }
    new Decision(purpose, preset, choice.algorithm, streams, requirements, explicit.nonEmpty,
      rationale, SamplingRandom.provenance(choice.algorithm))
  }
}
