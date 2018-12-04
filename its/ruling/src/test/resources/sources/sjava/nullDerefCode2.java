public class nullDerefCode {
  public void t(Object p) {
    p.toString();

    if(p == null){ } // Noncompliant

    p.toString();

    p = new Object();
    if(p == null){ } // Compliant

  }
}

