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

import org.junit.Test;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.visitors.TreePrinter;

public class SJavaConverterTest {

  private SJavaConverter converter = new SJavaConverter();


  @Test
  public void test_in() {

    String content = "class T {\n" +
        "int a = 1;\n" +
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
}
