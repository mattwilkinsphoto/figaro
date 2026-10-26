package com.cra.figaro.algorithm.sampling

import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import java.io.{ByteArrayOutputStream, DataOutputStream}
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

/** Frozen TJB-01 comparison. Test-only; no tuning, retries or stopping changes. */
object TransformedJointBlockStudy {
  val names=Vector("product2","product8","correlatedNormal","coupledSupport","curved","separated")
  val kinds=Vector(Vector("g","b"),Vector("g","b","n","g","b","n","g","n"),
    Vector("n","n"),Vector("g","b"),Vector("g","b"),Vector("g","b"))
  val means=Vector(Vector(.2,-.3),Vector(.2,-.3,.1,.2,-.3,.1,.2,.1),
    Vector(0.0,0.0),Vector(.2,-.3),Vector(0.0,0.0),Vector(0.0,0.0))
  def diagonal(v: Vector[Double])=Vector.tabulate(v.size,v.size)((i,j)=>if(i==j)v(i) else 0.0)
  val covariance=Vector(diagonal(Vector(.25,.64)),diagonal(Vector(.25,.64,.49,.25,.64,.49,.25,.49)),
    Vector(Vector(1.0,.97),Vector(.97,1.0)),Vector(Vector(.25,.388),Vector(.388,.64)),
    diagonal(Vector(1.0,1.37)),Vector(Vector(6.61,3.87),Vector(3.87,2.74)))
  val arms=Vector("default","jointPrior","scalar","candidate")
  def coordinate(k: String,x: Double): Double=k match {
    case "g"=>math.log(x)
    case "b"=>math.log(x)-math.log1p(-x)
    case _=>x
  }
  def gaussian2(z: Vector[Double],m: Vector[Double],c: Vector[Vector[Double]]): Double={
    val a=z(0)-m(0);val b=z(1)-m(1)
    -.5*(c(1)(1)*a*a-2*c(0)(1)*a*b+c(0)(0)*b*b)/(c(0)(0)*c(1)(1)-c(0)(1)*c(0)(1))
  }
  def logTarget(f: Int,z: Vector[Double]): Double= f match {
    case 0|1=> -.5*z.indices.map(i=>math.pow(z(i)-means(f)(i),2)/covariance(f)(i)(i)).sum
    case 2|3=>gaussian2(z,means(f),covariance(f))
    case 4=> -.5*z(0)*z(0)-.5*math.pow((z(1)-.8*(z(0)*z(0)-1))/.3,2)
    case 5=>
      val c=Vector(Vector(.36,.12),Vector(.12,.49))
      val a=gaussian2(z,Vector(-2.5,-1.5),c);val b=gaussian2(z,Vector(2.5,1.5),c)
      val top=math.max(a,b);top+math.log(math.exp(a-top)+math.exp(b-top))-math.log(2)
  }
  def model(f: Int,arm: String,u: Universe,calls: AtomicLong): MH.Model={
    val roots: Vector[Element[Double]]=kinds(f).map {
      case "g"=>Gamma(2,3)(using "",u)
      case "b"=>Beta(2,3)(using "",u)
      case _=>Normal(0,1)(using "",u)
    }
    val joint=Inject(roots*)(using "",u)
    joint.addLogConstraint { xs =>
      calls.incrementAndGet()
      val z=xs.indices.map(i=>coordinate(kinds(f)(i),xs(i))).toVector
      val jac=xs.indices.map(i=>kinds(f)(i) match {
        case "g"=>math.log(xs(i))
        case "b"=>math.log(xs(i))+math.log1p(-xs(i))
        case _=>0.0
      }).sum
      logTarget(f,z)-jac-xs.indices.map(i=>roots(i).asInstanceOf[HasLogDensity[Double]].logDensity(xs(i))).sum
    }
    val queries=roots.indices.toVector.flatMap { i =>
      Vector(MH.Observable(s"x$i",roots(i))(identity),MH.Observable(s"x${i}sq",roots(i))(x=>x*x),
        MH.Observable(s"z$i",roots(i))(x=>coordinate(kinds(f)(i),x)),
        MH.Observable(s"z${i}sq",roots(i))(x=>math.pow(coordinate(kinds(f)(i),x),2)))
    }++Vector(MH.Observable("contrast",joint)(xs=>coordinate(kinds(f)(0),xs(0))-coordinate(kinds(f)(1),xs(1))),
      MH.Observable("contrastsq",joint)(xs=>math.pow(coordinate(kinds(f)(0),xs(0))-coordinate(kinds(f)(1),xs(1)),2)))++
      (0 to 1).map(i=>MH.Observable(s"positive$i",roots(i))(x=>if(coordinate(kinds(f)(i),x)>0)1.0 else 0.0))
    val proposal=arm match {
      case "default"=>None
      case "jointPrior"=>Some(ProposalScheme(roots*))
      case "scalar"=>
        val choices=roots.indices.map(i=>SupportAwareProposal.automatic(roots(i),math.sqrt(covariance(f)(i)(i))))
        Some(DisjointScheme(choices.map(p=>(1.0/roots.size,()=>p))*))
      case "candidate"=>Some(TransformedJointBlock(roots,covariance(f).map(_.map(_/roots.size))))
    }
    MH.Model(queries,proposal)
  }
  def encoded(chains: Vector[MH.ChainResult],q: String): String={
    val bytes=new ByteArrayOutputStream
    val out=new DataOutputStream(new GZIPOutputStream(bytes))
    chains.foreach(_.draws(q).foreach(out.writeDouble));out.close()
    Base64.getEncoder.encodeToString(bytes.toByteArray)
  }
  def attempt(f: Int,index: Int,seed: Long,arm: String,smoke: Boolean): Unit={
    val start=System.nanoTime();val calls=new AtomicLong
    val key=Vector(names(f),index.toString,arm)
    println((Vector("BEGIN")++key++Vector(seed.toString,smoke.toString)).mkString("\t"));System.out.flush()
    try {
      val r=MH.run(MH.Config(chains=4,drawsPerChain=if(smoke)128 else 8000,
        warmUp=if(smoke)64 else 4000,parallelism=1,seed=seed))((u,i)=>model(f,arm,u,calls))
      if(!smoke) {
        r.chains.foreach { c =>println((Vector("CHAIN")++key++Vector(c.index,c.seed,c.acceptanceRate,
          c.initializationAttempts,c.samplingSeconds).map(_.toString)).mkString("\t")) }
        r.diagnostics.toVector.sortBy(_._1).foreach { (q,s) =>
          println((Vector("SUMMARY")++key++Vector(q)++Vector(s.mean,s.standardDeviation,
            s.rHat.getOrElse(Double.NaN),s.bulkEss.getOrElse(Double.NaN),s.tailEss.getOrElse(Double.NaN),
            s.meanEss.getOrElse(Double.NaN),s.mcseMean.getOrElse(Double.NaN)).map(_.toString)++Vector(s.warnings.mkString("|"))).mkString("\t"))
          println((Vector("TRACE")++key++Vector(q,encoded(r.chains,q))).mkString("\t"))
        }
      }
      System.out.flush()
      println((Vector(if(smoke)"SMOKE_END" else "END")++key++Vector((System.nanoTime()-start)/1e9,
        calls.get(),r.elapsedSeconds).map(_.toString)).mkString("\t"));System.out.flush()
    } catch { case e: Throwable =>
      println((Vector("FAIL")++key++Vector(e.getClass.getName,Option(e.getMessage).getOrElse("").replace('\t',' '),
        ((System.nanoTime()-start)/1e9).toString)).mkString("\t"));System.out.flush();throw e
    }
  }
  def main(args: Array[String]): Unit={
    require(args.length==1 && Set("development","confirmation").contains(args(0)))
    // Fixed correctness/JIT smoke, separately costed, never used to choose a matrix or budget.
    for((seed,i)<-Vector(7412202501L,7412202502L).zipWithIndex;arm<-arms)
      attempt(i,i,seed,arm,true)
    val count=if(args(0)=="development")8 else 32
    val base=if(args(0)=="development")7412202601L else 7412202701L
    for(i<-0 until count;f<-names.indices;j<-arms.indices)
      attempt(f,i,base+i+10000*f,arms((j+i+f)%4),false)
    println(s"COMPLETE\t${args(0)}\t${count*names.size*arms.size}")
  }
}
