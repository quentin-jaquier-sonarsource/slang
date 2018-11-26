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
package org.sonarsource.scala.converter;

import org.junit.Test;
import org.sonarsource.slang.api.FunctionDeclarationTree;
import org.sonarsource.slang.api.FunctionInvocationTree;
import org.sonarsource.slang.api.NativeTree;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.cfg.ControlFlowGraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.slang.testing.TreeAssert.assertTree;

public class FunctionInvocationTreeTest extends AbstractScalaConverterTest {

  @Test
  public void if_without_else() {
    Tree tree = scalaStatement("def method(param: Int): Int = { val p = 1;\n\np.toString()\nif(p == null){ }\n }");

    System.out.println("s");

    ControlFlowGraph cfg = ControlFlowGraph.build((FunctionDeclarationTree) tree);
    System.out.println("");
  }

  @Test
  public void fun_Invocation() {
    Tree tree = scalaStatement("p.toString()");
    FunctionInvocationTree fTree = (FunctionInvocationTree) tree;
    assertThat(fTree.arguments()).isEmpty();
    assertTree(fTree.methodName()).isIdentifier("toString");
    assertTree(fTree.methodSelect()).isIdentifier("p");
  }

  @Test
  public void fun_invocation_no_select() {
    Tree tree = scalaStatement("toString()");
    FunctionInvocationTree fTree = (FunctionInvocationTree) tree;
    assertThat(fTree.arguments()).isEmpty();
    assertTree(fTree.methodName()).isIdentifier("toString");
    assertTree(fTree.methodSelect()).isNull();
  }

  @Test
  public void fun_invocation_chained_select() {
    Tree tree = scalaStatement("A.p.toString()");
    FunctionInvocationTree fTree = (FunctionInvocationTree) tree;
    assertThat(fTree.arguments()).isEmpty();
    assertTree(fTree.methodName()).isIdentifier("toString");
    assertTree(fTree.methodSelect()).isInstanceOf(NativeTree.class);
  }
}
