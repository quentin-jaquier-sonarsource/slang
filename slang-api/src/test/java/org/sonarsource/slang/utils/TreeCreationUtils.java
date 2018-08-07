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
package org.sonarsource.slang.utils;

import org.sonarsource.slang.api.AssignmentExpressionTree;
import org.sonarsource.slang.api.BinaryExpressionTree;
import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.FunctionDeclarationTree;
import org.sonarsource.slang.api.IdentifierTree;
import org.sonarsource.slang.api.LiteralTree;
import org.sonarsource.slang.api.ModifierTree;
import org.sonarsource.slang.api.NativeKind;
import org.sonarsource.slang.api.NativeTree;
import org.sonarsource.slang.api.TopLevelTree;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.api.VariableDeclarationTree;
import org.sonarsource.slang.impl.AssignmentExpressionTreeImpl;
import org.sonarsource.slang.impl.BinaryExpressionTreeImpl;
import org.sonarsource.slang.impl.BlockTreeImpl;
import org.sonarsource.slang.impl.FunctionDeclarationTreeImpl;
import org.sonarsource.slang.impl.IdentifierTreeImpl;
import org.sonarsource.slang.impl.LiteralTreeImpl;
import org.sonarsource.slang.impl.ModifierTreeImpl;
import org.sonarsource.slang.impl.NativeTreeImpl;
import org.sonarsource.slang.impl.TopLevelTreeImpl;
import org.sonarsource.slang.impl.VariableDeclarationTreeImpl;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;

public class TreeCreationUtils {
  private TreeCreationUtils() {
  }

  public static LiteralTree literal(String value) {
    return new LiteralTreeImpl(null, value);
  }

  public static IdentifierTree identifier(String name) {
    return new IdentifierTreeImpl(null, name);
  }

  public static VariableDeclarationTree variable(String name) {
    return new VariableDeclarationTreeImpl(null, identifier(name), null, null, false);
  }

  public static VariableDeclarationTree value(String name) {
    return new VariableDeclarationTreeImpl(null, identifier(name), null, null, true);
  }

  public static BinaryExpressionTree binary(BinaryExpressionTree.Operator operator, Tree leftOperand, Tree rightOperand) {
    return new BinaryExpressionTreeImpl(null, operator, null, leftOperand, rightOperand);
  }

  public static AssignmentExpressionTree assignment(Tree leftOperand, Tree rightOperand) {
    return assignment(AssignmentExpressionTree.Operator.EQUAL, leftOperand, rightOperand);
  }

  public static BlockTree block(List<Tree> body) {
    return new BlockTreeImpl(null, body);
  }

  public static FunctionDeclarationTree simpleFunction(IdentifierTree name, BlockTree body) {
    return new FunctionDeclarationTreeImpl(null, Collections.emptyList(), null, name, Collections.emptyList(), body, emptyList());
  }

  public static AssignmentExpressionTree assignment(AssignmentExpressionTree.Operator operator, Tree leftOperand, Tree rightOperand) {
    return new AssignmentExpressionTreeImpl(null, operator, leftOperand, rightOperand);
  }

  public static NativeTree simpleNative(NativeKind kind, List<Tree> children) {
    return new NativeTreeImpl(null, kind, children);
  }

  public static ModifierTree simpleModifier(ModifierTree.Kind kind) {
    return new ModifierTreeImpl(null, kind);
  }

  public static TopLevelTree topLevel(List<Tree> declarations) {
    return new TopLevelTreeImpl(null, declarations, null);
  }

}