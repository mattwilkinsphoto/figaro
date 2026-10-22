package com.cra.figaro.test.modernization

import java.math.{BigDecimal as D, MathContext, RoundingMode as R}

/** INF-15 isolated prototype; not called by any production filter. */
object DecisionStratifiedPrototype {
  private val zero=D.ZERO; private val one=D.ONE; private val two=D.valueOf(2)
  final class Unresolved extends ArithmeticException("Decision precision budget exhausted; same draw not replaced")
  private def check():Unit=if(Thread.currentThread().isInterrupted)throw new InterruptedException("Decision prototype interrupted")
  private def down(x:Double)=math.max(0.0,math.nextDown(x))
  private def up(x:Double)=math.nextUp(x)
  final case class Bounds(lo:D,hi:D)

  /** Directed positive Taylor sum/remainder, reciprocal and repeated squaring. */
  def expNegative(x:D,p:Int):Bounds={
    require(x.signum>=0 && x.compareTo(D.valueOf(1000000))<=0 && Vector(40,80,160).contains(p))
    if(x.signum==0)return Bounds(one,one)
    val floor=new MathContext(p,R.FLOOR);val ceil=new MathContext(p,R.CEILING)
    var z=x;var squares=0
    while(z.compareTo(new D("0.125"))>0){check();z=z.divide(two);squares+=1;require(squares<=24)}
    var tl=one;var th=one;var sl=one;var sh=one;var k=0
    var remainder=one
    val tolerance=one.scaleByPowerOfTen(-p)
    while(remainder.compareTo(tolerance)>0 && k<512){
      check();k+=1
      tl=tl.multiply(z,floor).divide(D.valueOf(k),floor)
      th=th.multiply(z,ceil).divide(D.valueOf(k),ceil)
      sl=sl.add(tl,floor);sh=sh.add(th,ceil)
      val next=th.multiply(z,ceil).divide(D.valueOf(k+1),ceil)
      val denominator=one.subtract(z.divide(D.valueOf(k+2),ceil),floor)
      remainder=next.divide(denominator,ceil)
    }
    if(remainder.compareTo(tolerance)>0)throw new Unresolved
    var lo=one.divide(sh.add(remainder,ceil),floor)
    var hi=one.divide(sl,ceil)
    for(_<-0 until squares){check();lo=lo.multiply(lo,floor);hi=hi.multiply(hi,ceil)}
    Bounds(lo,hi)
  }

  final class Table(val logs:Vector[Double],val levels:Vector[Int]=Vector(40,80,160)) {
    require(logs.size>=2 && logs.size<=4096 && logs.exists(_.isFinite) &&
      logs.forall(x=>x.isFinite || x==Double.NegativeInfinity))
    require(levels==Vector(40,80,160) || levels.isEmpty,"Only full or test-disabled refinement")
    val n=logs.size;private val peak=logs.max
    private val span=peak-logs.filter(_.isFinite).min
    require(span.isFinite && span<=1000000,"Prototype log-span bound exceeded")
    val lower=new Array[Double](n+1);val upper=new Array[Double](n+1)
    for(i<-logs.indices){
      check()
      val (l,h)=if(logs(i)==Double.NegativeInfinity)(0.0,0.0)
        else if(logs(i)==peak)(1.0,1.0)
        else {
          val d=logs(i)-peak
          (down(down(math.exp(math.nextDown(d)))),math.min(1.0,up(up(math.exp(math.nextUp(d))))))
        }
      lower(i+1)=if(l==0)lower(i)else down(lower(i)+l)
      upper(i+1)=if(h==0)upper(i)else up(upper(i)+h)
    }
    private val cache=scala.collection.mutable.Map.empty[Int,Vector[Bounds]]
    var refinedDecisions=0L;var maxPrecision=0;var exactTies=0L
    def tablesBuilt:Int=cache.size
    private lazy val groups=logs.indices.filter(i=>logs(i).isFinite).groupBy(logs)
    private def exactTie(k:Int,q:D):Boolean=groups.values.forall { indices=>
      D.valueOf(n.toLong*indices.count(_<k)).compareTo(q.multiply(D.valueOf(indices.size)))==0
    }
    private def prefixes(p:Int):Vector[Bounds]=cache.getOrElseUpdate(p,{
      check();val f=new MathContext(p,R.FLOOR);val c=new MathContext(p,R.CEILING)
      val weights=logs.filter(_.isFinite).distinct.map(l=>l->expNegative(new D(peak).subtract(new D(l)),p)).toMap
      var lo=zero;var hi=zero
      Vector(Bounds(zero,zero))++logs.map { l=>
        val w=if(l==Double.NegativeInfinity)Bounds(zero,zero)else weights(l)
        lo=lo.add(w.lo,f);hi=hi.add(w.hi,c);Bounds(lo,hi)
      }
    })
    private def compare(k:Int,j:Int,u:Double):Int={
      if(k==n)return 1 // q < N exactly.
      val qlo=down(j.toDouble+u);val qhi=up(j.toDouble+u)
      val leftLo=down(n.toDouble*lower(k));val leftHi=up(n.toDouble*upper(k))
      val rightLo=down(qlo*lower(n));val rightHi=up(qhi*upper(n))
      if(leftHi<rightLo)return -1
      if(leftLo>rightHi)return 1
      val q=new D(j).add(new D(u))
      if(exactTie(k,q)){exactTies+=1;return 0}
      for(p<-levels){
        check();decimalUsed=true;maxPrecision=math.max(maxPrecision,p)
        val a=prefixes(p);val f=new MathContext(p,R.FLOOR);val c=new MathContext(p,R.CEILING)
        val lo=a(k).lo.multiply(D.valueOf(n),f);val hi=a(k).hi.multiply(D.valueOf(n),c)
        val rl=q.multiply(a(n).lo,f);val rh=q.multiply(a(n).hi,c)
        if(hi.compareTo(rl)<0)return -1
        if(lo.compareTo(rh)>0)return 1
      }
      throw new Unresolved
    }
    def choose(j:Int,u:Double):Int={
      check();require(j>=0 && j<n && u.isFinite && u>=0 && u<1)
      if(j==0 && u==0)return logs.indexWhere(_.isFinite)
      var lo=0;var hi=n-1
      while(lo<hi){val mid=(lo+hi)>>>1;if(compare(mid+1,j,u)>0)hi=mid else lo=mid+1}
      // Count a decision which invokes decimal comparisons, including cached tables.
      if(decimalUsed){refinedDecisions+=1;decimalUsed=false}
      require(logs(lo).isFinite,"Selected zero support")
      lo
    }
    private var decimalUsed=false
    def draw(rng:java.util.Random):Vector[Int]=Vector.tabulate(n)(j=>choose(j,rng.nextDouble()))
  }
}
