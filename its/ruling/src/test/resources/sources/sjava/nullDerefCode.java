public class nullDerefCode {
  public void t(Object p) {
    p.toString();

    if(p == null){ a = 1; } // Noncompliant

    p.toString();

    p = new Object();
    if(p == null){ a = 1;} // Compliant

  }
}

