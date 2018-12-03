public class exeptionHandling {
  public void t(int i) {
    int a = 1;
    try{
      if(i == 2) {
        throw new IllegalArgumentException("a");
      }
      a = 2;
    } catch (Exception e) {
      a = 3;
    } finally {
      a = 4;
    }
  }

  public void t1(int i) {
    int a = 1;
    try{
      if(i == 2) {
        throw new IllegalArgumentException("a");
      }
      a = 2;
    } catch (Exception e) {
      a = 3;
    }
  }
}

