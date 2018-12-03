
public class switchTree {
  enum letter {
    A,B,C}

  public void t(letter l) {
    int i = 1;
    switch(l){
      case A:
        i = 2;
        break;
      case B:
        i = 3;
      case C:
        i = 4;
        break;
      default:
        i = 6;
    }

  }
}

