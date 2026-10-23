package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{WeightedParticleProposal as P,LogParticleWeights as L}
import com.cra.figaro.algorithm.sampling.GaussianMixtureProposal as EM
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
import com.cra.figaro.util.{random,SamplingRandom}

/** INF-18 research only. Explicit scalar branches; no automatic discovery or fallback. */
object MulticomponentGuidePrototype {
  type V=Vector[Double]
  def check():Unit=if(Thread.currentThread().isInterrupted)throw new InterruptedException("Guide preparation interrupted")
  final case class Limits(parents:Int,stored:Long) {
    require(parents>0 && stored>0 && stored<=100000)
  }
  final case class Branches(y:Double,variance:Double,slopes:V,offsets:V) {
    val size=slopes.size
  }
  final case class Mixture(logs:V,laws:Vector[P.Law[V]]) {
    require(logs.size==laws.size && laws.nonEmpty && laws.size<=5 && laws.forall(_!=null))
    val normalized=L.normalize(logs).logs
    private val tree=new L.Tree(normalized)
    // Both callbacks use the same normalized log mixture, including tiny components.
    val law=P.Law[V](()=>{check();laws(tree.draw(L.logUniform(random.self))).sample()},x=>{
      check();laws.indices.map(i=>normalized(i)+laws(i).logDensity(x)).foldLeft(Double.NegativeInfinity)(L.add)
    })
  }
  final case class Prepared(law:P.Law[V],components:Int,conditioning:Int,stored:Long)
  def anchors(y:Double,variance:Double,points:V,h:Double=>Double,jac:Double=>Double):Branches={
    check()
    require(y.isFinite && variance.isFinite && variance>0 && points!=null && points.nonEmpty && points.size<=4)
    require(points.forall(_.isFinite) && points.distinct.size==points.size && h!=null && jac!=null)
    val values=points.map(a=>{check();val v=h(a);require(v.isFinite);v})
    val slopes=points.map(a=>{check();val v=jac(a);require(v.isFinite);v})
    val offsets=values.indices.map(i=>values(i)-slopes(i)*points(i)).toVector
    require(offsets.forall(_.isFinite),"Unrepresentable affine offset")
    Branches(y,variance,slopes,offsets)
  }
  def defend(prior:P.Law[V],guide:Mixture,epsilon:Double):P.Law[V]={
    require(epsilon.isFinite && epsilon>0 && epsilon<1)
    Mixture(Vector(math.log(epsilon))++guide.normalized.map(_+math.log1p(-epsilon)),Vector(prior)++guide.laws).law
  }
  def branch(prior:G,b:Branches,epsilon:Double):Prepared={
    check();require(prior.dimension==1)
    val parts=b.slopes.indices.map(j=>P.linearGaussianObservation(prior,Vector(b.y),
      Vector(Vector(b.slopes(j))),Vector(Vector(b.variance)),Vector(b.offsets(j)))).toVector
    val mixture=Mixture(parts.map(_.logPredictive-math.log(b.size)),parts.map(_.proposal))
    Prepared(defend(P.multivariateGaussian(prior),mixture,epsilon),b.size+1,b.size,slots(b.size))
  }
  // Conservative scalar-slot bound includes laws, tree arrays, weights and temporary conditioning.
  def slots(k:Int):Long=64L+32L*k
  def parentLookup(parents:V,b:Branches,limits:Limits,makePrior:Double=>G,epsilon:Double):Map[Double,Prepared]={
    check();require(parents.nonEmpty && parents.forall(_.isFinite))
    val keys=parents.distinct
    require(keys.size<=limits.parents && keys.size.toLong*slots(b.size)<=limits.stored,"Preparation budget exceeded")
    keys.map(x=>{check();x->branch(makePrior(x),b,epsilon)}).toMap
  }
  final case class Pilot(result:EM.Result,mixture:Option[Mixture],draws:Int,ancestors:Int,stored:Long)
  def pilot(seed:Long,initial:G,parents:Vector[G],logWeights:V,y:Double,noise:Double,
      h:Double=>Double,k:Int):Pilot={
    check();require(k>=1 && k<=4 && noise.isFinite && noise>0 && y.isFinite)
    require(initial.dimension==1 && parents.forall(_.dimension==1))
    require(parents.isEmpty && logWeights.isEmpty || parents.nonEmpty && parents.size==logWeights.size)
    val stored=512L*(k+12)+parents.size*2L+slots(k)
    require(stored<=100000,"Pilot preparation storage budget exceeded")
    val rng=SamplingRandom.seeded(seed);val sr=new scala.util.Random(rng)
    val tree=if(parents.isEmpty)None else Some(new L.Tree(logWeights))
    val points=Vector.fill(512){check();tree.fold(initial)(t=>parents(t.draw(L.logUniform(rng)))).sample(sr)}
    val weights=points.map(x=>{check();val z=(y-h(x.head))/noise;val v= -.5*z*z-math.log(noise)-.5*math.log(2*math.Pi)
      require(v.isFinite || v==Double.NegativeInfinity);v})
    val config=EM.Config(k,50,1e-6,20,200000,100000,Vector(1e-4),1.5)
    val result=EM.fitWeighted(points,weights,config)
    val mixture=result.proposal.map(p=>Mixture(p.probabilities.map(math.log),p.components.map(c=>P.Law[V](()=>c.sample(random),c.logDensity))))
    Pilot(result,mixture,512,if(tree.isDefined)512 else 0,stored)
  }
  /** Counts derived from the unchanged fitter's successful paths. Unknown on arithmetic failure. */
  def fitConstructions(r:EM.Result):Long=r.status match {
    case EM.Status.Fitted=>1L+r.config.components*(r.iterations+1L)
    case EM.Status.InsufficientPilot=>0L
    case EM.Status.DegeneratePilot=>1L
    case EM.Status.InsufficientComponent | EM.Status.IterationLimit=>1L+r.config.components*r.iterations
    case EM.Status.EvaluationLimit=>1L+r.config.components*(r.iterations+1L)
    case EM.Status.NumericalFailure=> -1L
  }
}
