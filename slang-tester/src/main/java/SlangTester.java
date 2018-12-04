import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.sonarsource.scala.converter.ScalaConverter;
import org.sonarsource.sjava.converter.SJavaConverter;
import org.sonarsource.slang.api.ASTConverter;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SlangTester {

//  private static final Path fileName = Paths.get("slang-tester\\src\\main\\resources\\test1.scala");

  private static final Path fileName = Paths.get("slang-tester\\src\\main\\resources\\test1.java");


  public static void main(String[] args) {



    String content = readFile(fileName);

//    LanguageTester kotlinTester = new LanguageTester(new KotlinConverter());
//    LanguageTester rubyTester = new LanguageTester(new RubyConverter());

//    LanguageTester scalaTester = new LanguageTester(new ScalaConverter());

    LanguageTester javaTester = new LanguageTester(new SJavaConverter());

    System.out.println(javaTester.getTable(content));

    System.out.println(javaTester.getDotCfg(content));


//    System.out.println(scalaTester.getTable(content));
//
//    System.out.println(scalaTester.getDotCfg(content));
  }



  private static String readFile(Path path) {
    try {
      return new String(Files.readAllBytes(path), UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + path, e);
    }
  }


}
