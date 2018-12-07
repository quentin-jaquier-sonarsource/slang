class A {

  int shortcircuit152(Object p, boolean b) {
    if(p.toString() || p == null){} // Compliant, multiple method select are not supported
  }

  int loop0(Object p, boolean b) {
    a = "" + p.toString();
    if(p == null){ } // Noncompliant
  }

  int foo4(Object p, boolean b) {
    a = 1;

    A ? B : p.toString(); // Compliant

    p == null;
  }

  int foo3(Object p, boolean b) {
    p.toString(); //Add the belief that p is not null
    p = new Object();
    //...
    if(p == null) {} // Compliant
  }

  int foo4(Object p, boolean b) {
    String s = (p == null) ? "" : p.toString(); // Compliant
  }

  int foo4(Object p, boolean b) {
    String s = (p == null) ? "" : p.toString(); // Compliant
    if(p == null) {} // Compliant
  }

  int foo4(Object p, boolean b) {
    p.toString();
    String s = (p == null) ? "" : q.toString(); // Noncompliant
  }

  int foo41(Object p, boolean b) {
    String s = false ? "" : p.toString(); // Compliant
    if(p == null) {} // Compliant
  }

  int foo5(Object p, boolean b) {
    q.fun(p.toString());
    if(p == null) { } // Noncompliant
  }



  int assign(Object p, boolean b) {
    foo6 = p.toString();
    if(p == null) {} // Noncompliant
  }

  int assign2(Object p, boolean b) {
    p = p.toString();
    if(p == null) {} // Compliant
  }

  int foo4(Object p, boolean b) {
    String s = a ? p.f(q.toString()) : (q == null);
  }

  //== Short circuit ===================================================

  int shortcircuit01(Object p, boolean b) {
    ((p == null)) || p.toString(); // Compliant
    if(p == null) {} // Compliant, the pointer use has been short circuited
  }

  int shortcircuitAnd01(Object p, boolean b) {
    p != null && p.toString(); // Compliant
    if(p == null) {} // Compliant, the call to the fun has not been made
  }

  int shortcircuitAnd01(Object p, boolean b) {
    p != null && a == p.toString(); // Compliant
    if(p == null) {} // Compliant, the call to the fun has not been made
  }

  int shortcircuitAnd01(Object p, boolean b) {
    p != null && p.toString(); // Compliant
    if(p == null) {} // Compliant, the pointer use has been short circuited
  }

  int shortcircuit01(Object p, boolean b) {
    p == null || p.toString(); // Compliant
    if(p == null) {} // Compliant, the pointer use has been short circuited
  }

  int shortcircuit01(Object p, boolean b) {
    p == null || p.toString(); // Compliant
    p.toString();
    if(p == null) {} // Compliant, FN, see todo
  }

  int shortcircuit0(Object p, boolean b) {
    if(p == null || (a || b) || p.toString()){} // Compliant
  }

  int shortcircuit15(Object p, boolean b) {
    if(p.equals("a") || p == null){} // Compliant, FN, due to the naive short circuit
  }

  int shortcircuit152(Object p, boolean b) {
    if(p.toString().equals("a") || p == null){} // Compliant, multiple method select are not supported
  }

  int shortcircuit2(Object p, boolean b) {
    f(p == null, p.toString()); // Noncompliant
  }

  int shortcircuit25(Object p, boolean b) {
    f(p.toString(), p == null); // Noncompliant
  }

  int shortcircuit3(Object p, boolean b) {
    p == null || p.toString(); // Compliant
  }

  int shortcircuit01(Object p, boolean b) {
    (q == a) || p == null || p.toString(); // Compliant
  }

  int shortcircuit01(Object p, boolean b) {
    q == null || q.toString() || (q == a) || p == null || p.toString(); // Compliant
  }

  int shortcircuit01(Object p, boolean b) {
    (p != null && q.a == p.foo());
    if (p == null) {} // Compliant
  }

  int f(Object p, boolean b) {
    p.toString();
    p == null;// Compliant, FN, it works with a IF because the next line will be in another block
    //This may seems bad, but we initially want to find check in if, finding this kind of checks is only bonus
    p = "";
  }

  int foo5(Object p, boolean b) {
    if (!level.equals(levelImpl.toString())) {
      if (levelImpl == null) { // Noncompliant
        levelImpl = Level.DEBUG;
        getLogger().debug("found unexpected level: " + level + ", logger: " + logger.getName() + ", msg: " + message);
        message = level + " " + message;
      }
    }
  }

  int foo5(Object p, boolean b) {
    q.fun(p.toString());
    if(p == null) { } // Noncompliant
  }

  int foo3(Object p, boolean b) {
    p.toString(); //Add the belief that p is not null
    p = new Object();
    //...
    if(p == null) {} // Compliant
  }

  int noIf(Object p) {
    x = p.toString();
    boolean b = p == null; // Noncompliant
  }

  int noIf(Object p) {
    p.toString();
    boolean b = p == null; // Noncompliant
  }

  int noIf2(Object p) {
    boolean b = p == null; // Noncompliant
    p.toString();
  }

  int compliant(Object p, boolean b) {
    if(p == null){ } //Compliant
    p.toString(); //Compliant, even though we have not assign p
  }

  int compliant2(Object p, boolean b) {
    if (p == null) {//Compliant
      p = new Object();
    }
    p.toString(); //Compliant
  }

  int compliant3() {
    Object p = null;
    p.toString(); //Compliant, FN. Our checker is a naive version, the goal is not to find these kind of obvious NP.
  }

  int foo(Object p, boolean b) {
    p.toString(); //Add the belief that p is not null
    //...

    if(p == null) {} // Noncompliant

    if(a) {
      if(p == null){ } // Noncompliant
      p = new Object();
    }

    if(p == null) { } // Compliant, one path does assign new value to p.
  }

  int foo2(Object p, boolean b) {
    p = "";
    p.toString(); //Add the belief that p is not null
    //...
    if(p == null) {} // Noncompliant
  }

  int foo5(Object p, boolean b) {
    String p = new Object();
    //...
    if(p == null) { } // Compliant
  }

  //== LOOP ===================================================

  int loop0(Object p, boolean b) {
    for(int i = 0; i < 10; i ++){
      p.toString();
    }
    if(p == null){ } // Compliant, a path (entry) have not used p
  }

  int loop0(Object p, boolean b) {
    for(int i = 0; i < p.toString(); i ++){

    }
    if(p == null){ } // Compliant, FN, the check is in a native node
  }

  int loop0(Object p, boolean b) {
    p.toString();
    for(int i = 0; i < 10; i ++){
      b = false;
    }
    if(p == null){ } // Noncompliant
  }

  int loop(Object p, boolean b) {
    b = false;
    for(int i = 0; i < 10; i ++){
      if(p == null){ } // Compliant
      p.toString();
    }
  }

  int loop1(Object p, boolean b) {
    b = false;
    for(int i = 0; i < 10; i ++){
      if(p == null){ } // Compliant
      p.toString();
    }
  }

  int loop2(Object p, boolean b) {
    p.toString();
    for(int i = 0; i < 10; i ++){
      if(p == null){ } // Noncompliant
    }
  }

  int loop3(Object p, boolean b) {
    p.toString();
    for(int i = 0; i < 10; i ++){
      if(b){
        p = new Object();
      }
    }
    if(p == null){ } // Compliant, one path can re-asign p
  }

  int loop4(Object p, boolean b) {
    if(p.toString().equals("a")) {
      while (p == null) { // Noncompliant
      }
    }
  }

  int loop5(Object p, boolean b) {
    p.toString();
    int i = 2;
    do {
      if(p == null){ } // Noncompliant
      i++;
    } while(i < 10);
  }

  private String loopFooString() {
    for (;;) {
      String s = "a";
      if (s == null) {
        return "a";
      }

      s.toString();
    }
  }

  int loop0(Object p, boolean b) {
    for(int i = 0; i < p.size(); i ++){
      a = 1;
    }
    if(p == null){ } // Compliant, FN due to for loop head
  }

  private Map.Entry<K,V> lowestEntry() {
    for (; ; ) {
      ConcurrentSkipListMap.Node<K, V> n = loNode();
      if (!isBeforeEnd(n))
        return null;
      Map.Entry<K, V> e = n.createSnapshot();
      if (e != null)
        return e;
    }
  }

  //== Exception =======================================

  void foo(boolean a, Object b) {
    b.toString();

    try{
      doSomething();
      if(b == null){ } // Noncompliant
    } catch(Exception e) {
      doSomething();
      if(b == null){ } // Noncompliant
    } finally {
      doSomething();
      if(b == null){ } // Noncompliant
    }

    if(b == null){ } // Noncompliant
  }

  void foo(boolean a, Object b) {
    b.toString();

    try{
      b = new Object();
    } catch(Exception e) {
      e.toString();
    }

    if(b == null){ } // Compliant
  }

  void foo(boolean a, Object b) {
    b.toString();

    try{
      doSomething();
    } catch(Exception e) {
      b = new Object();
    }

    if(b == null){ } // Compliant
  }

  void foo(boolean a, Object b) {
    b.toString();

    try{
      doSomething();
    } catch(Exception e) {
      doSomething();
    } finally {
      b = new Object();
    }

    if(b == null){ } // Compliant
  }

  void doSomething() { }

  //== Other =======================================

  private String shadowString(String s) {
    s.toString();

    String s = "";
    if(s == null){ } // Compliant
  }

  void inReturn(String p) {
    p.toString();
    return p == null; // Noncompliant
  }

  void nullObject() {
    A a = new A();

    a.equals("a");

    if(a == null){ // Noncompliant

    }
  }

  void nullObject() {
    A a = new A();

    a.s;

    if(a == null){ // Noncompliant

    }
  }

  void nullArrayAcess(int[] p) {
    p[0] = 1;
    if(p == null){ // Compliant, FN array not supported

    }
  }

  private static class A {
    String s = "";

    void foo() {
      if(s == null){ // Compliant
        s = "a";
      }
      s.toString();
      changeS(); // An other method can change the value of s!

      if(s == null) { // Noncompliant
        //, s is a field FP

      }
    }

    String getS() {
      return "s";
    }

    void changeS() {
      s = null;
    }
  }

  //== In code =======================================

  public boolean accept(long value) {
    return ((valids == null) || (valids.contains(value))) && ((invalids == null) || (!invalids.contains(value))); // Compliant
  }

  public boolean equals(Object o) {
    return a ? Objects.equals(dateTimeFormatter.locale(), that.dateTimeFormatter.locale())
        : dateTimeFormatter == null; // Compliant
  }

}