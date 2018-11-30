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

import com.sonar.sslr.api.RecognitionException;
import com.sonar.sslr.api.typed.ActionParser;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.java.ast.parser.JavaParser;
import org.sonar.java.model.JavaTree;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BreakStatementTree;
import org.sonar.plugins.java.api.tree.ContinueStatementTree;
import org.sonar.plugins.java.api.tree.DoWhileStatementTree;
import org.sonar.plugins.java.api.tree.ForStatementTree;
import org.sonar.plugins.java.api.tree.IfStatementTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ModifierTree;
import org.sonar.plugins.java.api.tree.WhileStatementTree;
import org.sonarsource.slang.api.ASTConverter;
import org.sonarsource.slang.api.BinaryExpressionTree;
import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.IdentifierTree;
import org.sonarsource.slang.api.JumpTree;
import org.sonarsource.slang.api.LoopTree;
import org.sonarsource.slang.api.ParseException;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.api.TreeMetaData;
import org.sonarsource.slang.impl.AssignmentExpressionTreeImpl;
import org.sonarsource.slang.impl.BinaryExpressionTreeImpl;
import org.sonarsource.slang.impl.BlockTreeImpl;
import org.sonarsource.slang.impl.ClassDeclarationTreeImpl;
import org.sonarsource.slang.impl.FunctionDeclarationTreeImpl;
import org.sonarsource.slang.impl.IdentifierTreeImpl;
import org.sonarsource.slang.impl.IfTreeImpl;
import org.sonarsource.slang.impl.JumpTreeImpl;
import org.sonarsource.slang.impl.LiteralTreeImpl;
import org.sonarsource.slang.impl.LoopTreeImpl;
import org.sonarsource.slang.impl.NativeTreeImpl;
import org.sonarsource.slang.impl.TopLevelTreeImpl;

public class SJavaConverter implements ASTConverter {

  @Override
  public Tree parse(String content) {
    ActionParser parser = JavaParser.createParser();

    try {
      org.sonar.plugins.java.api.tree.Tree t = (org.sonar.plugins.java.api.tree.Tree) parser.parse(content);
      return convert(t);
    } catch (RecognitionException e){
      throw new ParseException("Unable to parse file.");
    }
  }


  private List<Tree> convert(Iterable<? extends org.sonar.plugins.java.api.tree.Tree> trees) {
    List<Tree> convertedTrees = new ArrayList<>();
    for(org.sonar.plugins.java.api.tree.Tree t: trees){
      if(t != null) {
        convertedTrees.add(convert(t));
      }
    }
    return convertedTrees;
  }

  private Tree convert(org.sonar.plugins.java.api.tree.Tree t) {
    switch (t.kind()) {
      case COMPILATION_UNIT:
        return new TopLevelTreeImpl(metaData(t), convert(((JavaTree.CompilationUnitTreeImpl)t).children()), new ArrayList<>());
      case CLASS:
        return createClassDecl((org.sonar.plugins.java.api.tree.ClassTree)t);
      case METHOD:
        return createMethodDecl((MethodTree) t);
      case WHILE_STATEMENT:
        return createLoopTree(t, ((WhileStatementTree)t).statement(), ((WhileStatementTree)t).condition(), LoopTree.LoopKind.FOR);
      case FOR_STATEMENT:
        return createLoopTree(t, ((ForStatementTree)t).statement(), ((ForStatementTree)t).condition(), LoopTree.LoopKind.FOR);
      case DO_STATEMENT:
        return createLoopTree(t, ((DoWhileStatementTree)t).statement(), ((DoWhileStatementTree)t).condition(), LoopTree.LoopKind.DOWHILE);
      case IF_STATEMENT:
        return createIfTree((IfStatementTree)t);
      case ASSIGNMENT:
      return new AssignmentExpressionTreeImpl(metaData(t), org.sonarsource.slang.api.AssignmentExpressionTree.Operator.EQUAL,
          convert(((AssignmentExpressionTree)t).variable()),
          convert(((AssignmentExpressionTree)t).expression()));
      case BLOCK:
        return new BlockTreeImpl(metaData(t), convert(((org.sonar.plugins.java.api.tree.BlockTree) t).body()));
      case IDENTIFIER:
        return new IdentifierTreeImpl(metaData(t), ((org.sonar.plugins.java.api.tree.IdentifierTree)t).name());
      case BREAK_STATEMENT:
        return createJumpStatement(t, JumpTree.JumpKind.BREAK, ((BreakStatementTree) t).label());
      case CONTINUE_STATEMENT:
        return createJumpStatement(t, JumpTree.JumpKind.CONTINUE, ((ContinueStatementTree) t).label());
      case INT_LITERAL:
      case LONG_LITERAL:
      case FLOAT_LITERAL:
      case DOUBLE_LITERAL:
      case BOOLEAN_LITERAL:
      case CHAR_LITERAL:
      case STRING_LITERAL:
      case NULL_LITERAL:
        return new LiteralTreeImpl(metaData(t), t.kind().toString());
      case EQUAL_TO: //TODO: add other binary
        return new BinaryExpressionTreeImpl(metaData(t), BinaryExpressionTree.Operator.EQUAL_TO, null,
            convert(((org.sonar.plugins.java.api.tree.BinaryExpressionTree) t).leftOperand()),
            convert(((org.sonar.plugins.java.api.tree.BinaryExpressionTree) t).rightOperand()));

      default:
        return createNativeTree(t);
        // Ignore other kind of elements, no change of gen/kill
    }
  }

  private Tree createIfTree(IfStatementTree t) {
    Tree cond = convert(t.condition());
    Tree thenP = convert(t.thenStatement());
    Tree elseP = null;
    if(t.elseStatement() != null){
      elseP = convert(t.elseStatement());
    }
    return new IfTreeImpl(metaData(t), cond, thenP, elseP, null, null);
  }

  private Tree createLoopTree(org.sonar.plugins.java.api.tree.Tree t, org.sonar.plugins.java.api.tree.Tree body, org.sonar.plugins.java.api.tree.Tree cond, LoopTree.LoopKind kind) {
    return new LoopTreeImpl(metaData(t), convert(cond), convert(body), kind, null);
  }

  private Tree createMethodDecl(MethodTree t) {
    List<Tree> modifiers = convert((List<ModifierTree>)t.modifiers());
    IdentifierTree returnType = null;
    if(t.returnType() != null){
      //TODO : add for a complete mapping
    }

    IdentifierTree name = (IdentifierTree)convert(t.simpleName());
    List<Tree> parameters = convert(t.parameters()); //TODO: change this for a better mapping
    BlockTree body = null;
    if(t.block() != null){
      body = (BlockTree)convert(t.block());
    }
    return new FunctionDeclarationTreeImpl(metaData(t), modifiers, returnType, name, parameters, body, new ArrayList<>());
  }

  private Tree createJumpStatement(org.sonar.plugins.java.api.tree.Tree t, JumpTree.JumpKind kind, @Nullable org.sonar.plugins.java.api.tree.Tree label){
    IdentifierTree labelName = null;
    if(label != null){
      labelName = (IdentifierTree)convert(label);
    }
    return new JumpTreeImpl(metaData(t), null, kind, labelName);
  }

  private Tree createClassDecl(org.sonar.plugins.java.api.tree.ClassTree t) {
    IdentifierTree name = null;
    if(t.simpleName() != null){
      name = (IdentifierTree) convert(t.simpleName());
    }
    List<Tree> members = convert(t.members());

    return new ClassDeclarationTreeImpl(metaData(t), name, createNativeTree(t, members));
  }

  private Tree createNativeTree(org.sonar.plugins.java.api.tree.Tree t) {
    List<Tree> children = new ArrayList<>();
    if(t instanceof JavaTree){
      JavaTree javaTree = (JavaTree) t;
      if(!javaTree.isLeaf()){
        List<org.sonar.plugins.java.api.tree.Tree> javaChildren = javaTree.getChildren();
        javaChildren = javaChildren.stream().filter(p -> !p.is(org.sonar.plugins.java.api.tree.Tree.Kind.TOKEN)).collect(Collectors.toList());
        if(!javaChildren.isEmpty()){
          children.addAll(convert(javaChildren));
        }
      }
    }
    return createNativeTree(t, children);
  }

  private Tree createNativeTree(org.sonar.plugins.java.api.tree.Tree t, List<Tree> children) {
    return new NativeTreeImpl(null, new SJavaNativeKind(t.kind().toString()), children);
  }


  public TreeMetaData metaData(org.sonar.plugins.java.api.tree.Tree t) {
    return null;
  }
}
