class A {
  int compliant3() {
    Object p = null;
    p.toString(); //Compliant, FN. Our checker is a naive version, the goal is not to find these kind of obvious NP.
  }

  int foo(Object p, boolean b) {
    //...
    if(p == null) { } // Compliant, one path does assign new value to p.


    if(a) {
      p = new Object();
      if(p == null){ } // Noncompliant
    }

    if(p == null) {} // Noncompliant

    p.toString(); //Add the belief that p is not null

  }


  int compliant(Object p, boolean b) {
    p.toString(); //Compliant
    if(p != null){ } // Noncompliant
  }

  int compliant(Object p, boolean b) {
    p.toString(); //Compliant
    if(null != p){ } // Noncompliant
  }

  int compliant(Object p, boolean b) {
    if(null != p){ } // Noncompliant
    p.toString(); //Compliant
  }

  int compliant(Object p, boolean b) {
    p.toString(); //Compliant
    if(p != p){ } // Compliant
  }

  int compliant(Object p, boolean b) {
    p.toString(); //Compliant
    if(null == p){ } // Noncompliant
  }

  int compliant(Object p, boolean b) {
    if(p == null){ } // Noncompliant
    p.toString(); // Compliant
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
    p = new Object();
    p.toString(); //Add the belief that p is not null
    //...
    if(p == null) {} // Noncompliant
  }

  int foo3(Object p, boolean b) {
    p.toString(); //Add the belief that p is not null
    p = new Object();
    //...
    if(p == null) {} // Compliant
  }

  int foo4(Object p, boolean b) {
    //...
    if(p == null || p.toString().equals("a")){} // Compliant
  }

  int foo5(Object p, boolean b) {
    String p = new Object();
    //...
    if(p == null) { } // Compliant
  }

  //== LOOP ===================================================

  int loop(Object p, boolean b) {
    for(int i = 0; i < 10; i ++){
      if(p == null){ } // Noncompliant
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

  private String shadowString(String s) {
    s.toString();

    String s = "";
    if(s == null){ } // Compliant
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


  private static class A {
    String s = "";

    void foo() {
      if(s == null){ //
        s = "a";
      }
      s.toString();
      changeS(); // An other method can change the value of s!

      if(s == null) { // Compliant, s can change in the function call
      }
    }

    void changeS() {
      s = null;
    }

  }

  // BACKWARD =========================================
  int foo(Object p, boolean b) {
    if(p == null) { // Noncompliant
      p.toString(); //Add the belief that p is not null
      p = new Object();
    }
    p.toString(); //Add the belief that p is not null
  }

  int foo(Object p, boolean b) {
    if(p == null) { // Compliant, one path re-assign p
      p = new Object();
      p.toString(); //Add the belief that p is not null
    }
    p.toString(); //Add the belief that p is not null
  }

  int foo(Object p, boolean b) {
    p = new Object();
    if(p == null) { } // Noncompliant
    p.toString(); //Add the belief that p is not null
  }

  int compliant(Object p, boolean b) {
    if(p == null){ } // Noncompliant
    p.toString(); //due to backward analysis
  }

  int compliant2(Object p, boolean b) {
    if (p == null) {//Compliant, one path has assigned p
      p = new Object();
    }
    p.toString(); //Compliant
  }
}