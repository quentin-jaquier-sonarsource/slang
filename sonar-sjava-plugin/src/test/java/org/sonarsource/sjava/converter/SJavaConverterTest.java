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
package org.sonarsource.sjava.converter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.checkerframework.checker.units.qual.C;
import org.junit.Test;
import org.sonarsource.slang.api.ParseException;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.visitors.TreePrinter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class SJavaConverterTest {

  private static SJavaConverter converter = new SJavaConverter();


  @Test
  public void all_java_files() throws IOException {
    for (Path javaPath : getJavaSources()) {
      Path astPath = Paths.get(javaPath.toString().replaceFirst("\\.java$", ".txt"));
      String actualAst = TreePrinter.tree2string(parse(javaPath));
      String expectingAst = astPath.toFile().exists() ? new String(Files.readAllBytes(astPath), UTF_8) : "";
      assertThat(actualAst)
          .describedAs("In the file: " + astPath + " (run ApexConverterTest.main manually)")
          .isEqualTo(expectingAst);
    }
  }

  public static void main(String[] args) throws IOException {
    fix_all_java_files_test_automatically();
  }

  private static void fix_all_java_files_test_automatically() throws IOException {
    for (Path javaPath : getJavaSources()) {
      Path astPath = Paths.get(javaPath.toString().replaceFirst("\\.java", ".txt"));
      String actualAst = TreePrinter.tree2string(parse(javaPath));
      Files.write(astPath, actualAst.getBytes(UTF_8));
    }
  }

  public static List<Path> getJavaSources() throws IOException {
    try (Stream<Path> pathStream = Files.walk(Paths.get("sonar-sjava-plugin", "src", "test", "java", "org", "sonarsource", "sjava", "converter", "ast"))) {
      return pathStream
          .filter(path -> !path.toFile().isDirectory() && path.getFileName().toString().endsWith(".java"))
          .sorted()
          .collect(Collectors.toList());
    }
  }


  @Test
  public void test_in() {

    String content = "class T {\n" +
        "int a = 1; // comment \n" +
        "public void t(int i) {\n" +
        "if(p < 19) {\n" +
        " a = true || false;" +
        "}\n" +
        " while (p == null) {\n" +
        "    break;" +
        "   }\n" +
        " }\n" +
        "}\n";
    Tree t = converter.parse(content);


    System.out.println(TreePrinter.tree2string(t));
  }


  @Test
  public void test_nested_if() {

    String content = "class T {\n" +
        "public void t(int i) {\n" +
        "  if(p < 19) {\n" +
        "    if(p == null) {\n" +
        "       a = b;\n" +
        "    }\n" +
        "  } else {\n" +
        "    if(a == 2) {" +
        "     b = 2;\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "}\n";
    Tree t = converter.parse(content);


    System.out.println(TreePrinter.tree2string(t));
  }

  @Test
  public void test_switch() {

    String a = "";

    switch(a) {


    }

    String content = "class T {\n" +
        "int a = 1; // comment \n" +
        "public void t(int i) {\n" +
        "if(p < 19) {\n" +
        " a = true || false;" +
        "}\n" +
        " while (p == null) {\n" +
        "    break;" +
        "   }\n" +
        " }\n" +
        "}\n";
    Tree t = converter.parse(content);


    System.out.println(TreePrinter.tree2string(t));
  }

  public static Tree parse(Path path) throws IOException {
    String code = new String(Files.readAllBytes(path), UTF_8);
    try {
      return converter.parse(code);
    } catch (ParseException e) {
      throw new ParseException(e.getMessage() + " in file " + path, e.getPosition(), e);
    } catch (RuntimeException e) {
      throw new RuntimeException(e.getClass().getSimpleName() + ": " + e.getMessage() + " in file " + path, e);
    }
  }
}
