import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.scalasbt.shadedgson.com.google.gson.Gson;
import sbt.internal.worker1.ForkTestMain;
import sbt.internal.worker1.WorkerMain;

/** Additive notification controls. The original eight-case matrix is untouched. */
public class ForkErrorCheck {
  static final String OK = "{\"id\":42,\"result\":0}";
  static String notification(long outer, long inner) throws Exception {
    Constructor<?> c = ForkTestMain.ForkError.class.getDeclaredConstructor(Throwable.class);
    c.setAccessible(true);
    var error = (ForkTestMain.ForkError)c.newInstance(new IllegalStateException("SENTINEL_FORK_ERROR"));
    Gson gson = WorkerMain.mkGson();
    String params = gson.toJson(new ForkTestMain.ForkErrorInfo(inner, error));
    return "{\"jsonrpc\":\"2.0\",\"method\":\"forkError\",\"re\":" + outer + ",\"params\":" + params + "}";
  }
  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !(args[0].equals("stock") || args[0].equals("repaired")))
      throw new IllegalArgumentException("stock|repaired");
    boolean stock = args[0].equals("stock");
    Class<?> react = Class.forName("sbt.React"), processType = Class.forName("scala.sys.process.Process");
    System.out.println("BINARY " + react.getProtectionDomain().getCodeSource().getLocation());
    Constructor<?> ctor = react.getDeclaredConstructors()[0]; ctor.setAccessible(true);
    Method apply = react.getDeclaredMethod("apply", String.class); apply.setAccessible(true);
    Method exit = react.getDeclaredMethod("notifyExit", processType); exit.setAccessible(true);
    Method block = react.getDeclaredMethod("blockForResponse"); block.setAccessible(true);
    Method pm = react.getDeclaredMethod("promise"); pm.setAccessible(true);
    Method completed = Class.forName("scala.concurrent.Promise").getMethod("isCompleted");
    String valid = notification(42,42);
    for (String mode : new String[]{"fork-before-success","fork-before-exit","fork-during-exit",
        "duplicate-fork","success-before-fork","wrong-outer-id","wrong-inner-id"}) {
      int duplicates = 0;
      boolean failure = !mode.startsWith("wrong-") && !mode.equals("success-before-fork");
      for (int trial=0; trial<20; trial++) {
        AtomicInteger traces = new AtomicInteger();
        xsbti.Logger logger = (xsbti.Logger)Proxy.newProxyInstance(xsbti.Logger.class.getClassLoader(),
          new Class<?>[]{xsbti.Logger.class}, (p,m,a) -> {
            if (m.getName().equals("trace")) {
              Object error = ((java.util.function.Supplier<?>)a[0]).get();
              if (!(error instanceof ForkTestMain.ForkError) ||
                  !((Throwable)error).getMessage().contains("SENTINEL_FORK_ERROR"))
                throw new AssertionError("Notification failed to deserialize");
              traces.incrementAndGet();
            }
            return null;
          });
        Object log = sbt.util.Logger$.MODULE$.xlog2Log(logger);
        Object[] holder = new Object[1];
        Object process = Proxy.newProxyInstance(processType.getClassLoader(),new Class<?>[]{processType},(p,m,a)->{
          if (m.getName().equals("isAlive")) return false;
          if (m.getName().equals("exitValue")) {
            if (mode.equals("fork-during-exit")) apply.invoke(holder[0],valid);
            return 0;
          }
          throw new UnsupportedOperationException(m.toString());
        });
        holder[0] = ctor.newInstance(42L,log,null,null,process);
        if (mode.equals("success-before-fork")) apply.invoke(holder[0],OK);
        if (!mode.equals("fork-during-exit")) {
          apply.invoke(holder[0],mode.equals("wrong-outer-id") ? notification(17,42) :
            mode.equals("wrong-inner-id") ? notification(42,17) : valid);
          if (mode.equals("duplicate-fork")) apply.invoke(holder[0],valid);
        }
        if (mode.startsWith("wrong-") && (boolean)completed.invoke(pm.invoke(holder[0])))
          throw new AssertionError("Unrelated notification completed request");
        if (mode.equals("fork-before-exit") || mode.equals("fork-during-exit")) {
          try { exit.invoke(holder[0],process); }
          catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof IllegalStateException) ||
                !"Promise already completed.".equals(e.getCause().getMessage())) throw e;
            duplicates++;
          }
        }
        int expectedTraces = mode.startsWith("wrong-") ? 0 : mode.equals("duplicate-fork") ? 2 : 1;
        if (traces.get()!=expectedTraces) throw new AssertionError("Notification not processed: " + mode);
        apply.invoke(holder[0],OK); // bounded completion even for wrong-ID controls
        try {
          Object value=block.invoke(holder[0]);
          if (failure || !Integer.valueOf(0).equals(value)) throw new AssertionError("Lost first error: " + mode);
        } catch (InvocationTargetException e) {
          if (!failure || !(e.getCause() instanceof ForkTestMain.ForkError) ||
              !e.getCause().getMessage().contains("SENTINEL_FORK_ERROR")) throw e;
        }
      }
      int expected = stock && mode.equals("fork-during-exit") ? 20 : 0;
      if (duplicates!=expected) throw new AssertionError(mode + ": " + duplicates + " != " + expected);
      System.out.printf("{\"mode\":\"%s\",\"trials\":20,\"duplicateExceptions\":%d,\"firstErrorPreserved\":%s,\"lateErrorLimitation\":%s}%n",
        mode,duplicates,failure,mode.equals("success-before-fork"));
    }
  }
}
