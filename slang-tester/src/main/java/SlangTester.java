/*
 * SonarSource SLang
 * Copyright (C) 2009-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
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
