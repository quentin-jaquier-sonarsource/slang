


//===========


fun foo(Object p) {
    p.toString();

    if(p == null) { }; // Noncompliant
}

fun foo(Object p) {
   if(p == null){ }; //Compliant
   p.toString(); //Compliant,even though we have not assign p
}

fun foo(Object p) {
   if(p == null){ //Compliant
    p = "";
   }; //Compliant
   p.toString(); //Compliant
}

fun foo(Object p) {
   p = "null";
   p.toString(); //Compliant, FN. Our checker is a naive version, the goal is not to find these kind of obvious NP.
}

fun foo(Object p, boolean b) {
    p.toString(); //Add the belief that p is not null
    //...

    if(p == null) {}; // Noncompliant

    if(a) {
      if(p == null){ }; // Noncompliant
      p = "new";
    };

    if(p == null) { }; // Compliant, one path does assign new value to p.
}

fun foo(Object p) {
   p = "null";
   p.toString();
   if(p == null) {} // Noncompliant
}


fun foo(Object p) {
   p.toString();
   p = "null";
   if(p == null) {}; // Compliant
}

fun foo(Object p) {
   p.toString();
   if(a) {
    if(p == null) {}; // Noncompliant
   }
}

  //== LOOP ===================================================

fun loop(Object p, boolean b) {
  p.toString();
  for(int var x = list){
    if(p == null){ }; // Noncompliant
  };
}

fun loop(Object p, boolean b) {
  other.toString();
  for(int var x = list){
    if(p == null){ }; // Compliant
    p.toString();
  };
}

fun loop(Object p, boolean b) {
    if(p.toString() == "a") {
      if(p == "null"){ // Compliant, FN, we don't look inside the content of the elements of the cfg

      };
    };
}

  //== Exception =======================================


fun foo(Object p) {
   p.toString();
    try {
        if(p == null){ }; // Noncompliant
    } catch () {
        if(p == null){ }; // Noncompliant
    } finally {
        if(p == null){ }; // Noncompliant
    };
    p = "";
    try {
        if(p == null){ }; // Compliant
    } catch () {
        if(p == null){ }; // Compliant
    } finally {
        if(p == null){ }; // Compliant
    };
}



