public class jumps {
  public int t(int i) {
    while(i == i){
      i = 1;
      if(i == 123) {
        break;
      }
    }

    do {
      if(i == 123) {
        continue;
      }
    } while (i == 1);

    for (int j = 0; j <  19; j++) {
      if(j == 128) {
        return j + 1;
      }
      i = j % 10;
    }
    return 0 + i;
  }
}

