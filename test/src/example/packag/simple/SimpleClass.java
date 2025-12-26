package example.packag.simple;

public class SimpleClass {
    
  public static Integer sum(Integer a, Integer b) {
    return a + b;
  }

  public static void main(String[] args) {
    System.out.println("Hello World!");

    var test1 = 120;

    var t2 = sum(2, 3);

    assert (t2 > 2);

    var t3 = 120 + t2;


    System.exit(1);
  }

}

