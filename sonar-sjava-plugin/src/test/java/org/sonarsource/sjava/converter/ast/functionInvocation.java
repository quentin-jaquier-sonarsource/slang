public class functionInvocation {
  public static Object q = "";

  public void t(Object p) {
    f(1,2,3);
    p.toString();
    this.q.toString();
    functionInvocation.q.toString();
    this.f(1,2,3);
  }

  private int f(int i, int j, int k) {
    return i + j + k;
  }
}

