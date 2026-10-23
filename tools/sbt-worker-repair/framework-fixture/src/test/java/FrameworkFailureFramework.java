import sbt.testing.*;

/** Throws in worker runner.tasks, outside the per-test assertion/event handler. */
public class FrameworkFailureFramework implements Framework {
  public String name() { return "Deliberate forkError fixture"; }
  public Fingerprint[] fingerprints() {
    return new Fingerprint[]{new SubclassFingerprint() {
      public boolean isModule() { return false; }
      public String superclassName() { return FrameworkFailureMarker.class.getName(); }
      public boolean requireNoArgConstructor() { return true; }
    }};
  }
  public Runner runner(String[] args, String[] remoteArgs, ClassLoader loader) {
    return new Runner() {
      public String[] args() { return args; }
      public String[] remoteArgs() { return remoteArgs; }
      public String done() { return ""; }
      public Task[] tasks(TaskDef[] defs) {
        if (Boolean.getBoolean("figaro.fixture.worker"))
          throw new IllegalStateException("SENTINEL_FRAMEWORK_FORK_ERROR");
        return new Task[0];
      }
    };
  }
}
