public class nullDerefCode {
  public void t(Object p) {
    if(n == 2) {
      b = 1;
    } else if(p == null){
      b = 2;
    } else  {
      p.toString();
    }

  }
}

