package figaro.benchmark;

import java.util.HashSet;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import com.cra.figaro.util.HashSelectableSet;

/** JMH-only benchmark; not part of any Figaro runtime or published artifact. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class EnumerationBenchmark {
  @Param({"1000", "2000", "3000", "4000"}) public int size;
  private HashSelectableSet<Double> set;
  @Setup public void setup() {
    set=new HashSelectableSet<>();
    SplittableRandom random=new SplittableRandom(20261022L+size);
    HashSet<Double> expected=new HashSet<>();
    for(int i=0;i<size;i++){double x=random.nextDouble();set.addOne(x);expected.add(x);}
    HashSet<Double> actual=new HashSet<>();
    scala.collection.Iterator<Double> it=set.toList().iterator();
    while(it.hasNext()) actual.add(it.next());
    if(set.toList().size()!=size || !expected.equals(actual)) throw new AssertionError("Fixture contents");
  }
  @Benchmark public Object enumerate() { return set.toList(); }
}
