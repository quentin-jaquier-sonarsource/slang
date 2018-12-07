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
import org.apache.commons.lang.ObjectUtils;
import org.sonar.java.ast.parser.JavaParser;
import org.sonar.java.model.JavaTree;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BreakStatementTree;
import org.sonar.plugins.java.api.tree.CaseGroupTree;
import org.sonar.plugins.java.api.tree.CatchTree;
import org.sonar.plugins.java.api.tree.ContinueStatementTree;
import org.sonar.plugins.java.api.tree.DoWhileStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.ForStatementTree;
import org.sonar.plugins.java.api.tree.IfStatementTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ModifierTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.SwitchStatementTree;
import org.sonar.plugins.java.api.tree.SyntaxToken;
import org.sonar.plugins.java.api.tree.ThrowStatementTree;
import org.sonar.plugins.java.api.tree.TryStatementTree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.plugins.java.api.tree.WhileStatementTree;
import org.sonarsource.slang.api.ASTConverter;
import org.sonarsource.slang.api.BinaryExpressionTree;
import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.IdentifierTree;
import org.sonarsource.slang.api.JumpTree;
import org.sonarsource.slang.api.LoopTree;
import org.sonarsource.slang.api.MatchCaseTree;
import org.sonarsource.slang.api.ParseException;
import org.sonarsource.slang.api.TextRange;
import org.sonarsource.slang.api.Token;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.api.TreeMetaData;
import org.sonarsource.slang.impl.AssignmentExpressionTreeImpl;
import org.sonarsource.slang.impl.BinaryExpressionTreeImpl;
import org.sonarsource.slang.impl.BlockTreeImpl;
import org.sonarsource.slang.impl.CatchTreeImpl;
import org.sonarsource.slang.impl.ClassDeclarationTreeImpl;
import org.sonarsource.slang.impl.ExceptionHandlingTreeImpl;
import org.sonarsource.slang.impl.FunctionDeclarationTreeImpl;
import org.sonarsource.slang.impl.FunctionInvocationTreeImpl;
import org.sonarsource.slang.impl.IdentifierTreeImpl;
import org.sonarsource.slang.impl.IfTreeImpl;
import org.sonarsource.slang.impl.JumpTreeImpl;
import org.sonarsource.slang.impl.LiteralTreeImpl;
import org.sonarsource.slang.impl.LoopTreeImpl;
import org.sonarsource.slang.impl.MatchCaseTreeImpl;
import org.sonarsource.slang.impl.MatchTreeImpl;
import org.sonarsource.slang.impl.MemberSelectImpl;
import org.sonarsource.slang.impl.NativeTreeImpl;
import org.sonarsource.slang.impl.ParenthesizedExpressionTreeImpl;
import org.sonarsource.slang.impl.ReturnTreeImpl;
import org.sonarsource.slang.impl.TextRangeImpl;
import org.sonarsource.slang.impl.ThrowTreeImpl;
import org.sonarsource.slang.impl.TokenImpl;
import org.sonarsource.slang.impl.TopLevelTreeImpl;
import org.sonarsource.slang.impl.TreeMetaDataProvider;
import org.sonarsource.slang.impl.VariableDeclarationTreeImpl;

public class SJavaConverter implements ASTConverter {

  TreeMetaDataProvider metaDataProvider;

  @Override
  public Tree parse(String content) {
    ActionParser parser = JavaParser.createParser();

    try {
      org.sonar.plugins.java.api.tree.Tree t = (org.sonar.plugins.java.api.tree.Tree) parser.parse(content);
      List<SyntaxToken> tokens = new ArrayList<>();
      gatherToken(t,tokens);

      metaDataProvider = new TreeMetaDataProvider(new ArrayList<>(), tokens.stream().map(this::convertToSlangToken).collect(Collectors.toList()));

      return convert(t);
    } catch (RecognitionException e){
      throw new ParseException("Unable to parse file.");
    }
    catch (NullPointerException np) {
      throw new ParseException("Unable to parse file due to null pointer ex");
    }
  }

  private Token convertToSlangToken(SyntaxToken token) {
    return new TokenImpl(getTextRange(token), token.text(), Token.Type.KEYWORD); //TODO Change here for better mapping
  }

  private void gatherToken(org.sonar.plugins.java.api.tree.Tree t, List<SyntaxToken> tokens) {
    //TODO: Check if not easier way to do it
    if(t instanceof JavaTree){
      if(t instanceof SyntaxToken) {
        SyntaxToken stxToken = (SyntaxToken) t;
        if(stxToken.text().length() > 0) {
          tokens.add(stxToken);
        }
        return;
      }
      JavaTree javaTree = (JavaTree) t;
      if(!javaTree.isLeaf()) {
        javaTree.getChildren().stream().forEach(child -> gatherToken(child, tokens));
      }
    } // Else, unknown, ignore it
  }


  private List<Tree> convert(Iterable<? extends org.sonar.plugins.java.api.tree.Tree> trees) {
    List<Tree> convertedTrees = new ArrayList<>();
    for(org.sonar.plugins.java.api.tree.Tree t: trees){
      if(t != null && !(t instanceof SyntaxToken) && t.firstToken() != null) {
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
      case METHOD_INVOCATION:
        return createMethodInvocation((MethodInvocationTree) t);
      case WHILE_STATEMENT:
        return createLoopTree(t, ((WhileStatementTree)t).statement(), ((WhileStatementTree)t).condition(), LoopTree.LoopKind.WHILE, keyword(((WhileStatementTree)t).whileKeyword()));
      case FOR_STATEMENT:
        if(((ForStatementTree)t).condition() == null) {
          return createNativeTree(t);
        } else {
          return createLoopTree(t, ((ForStatementTree) t).statement(), ((ForStatementTree) t).condition(), LoopTree.LoopKind.FOR, keyword(((ForStatementTree) t).forKeyword()));
        }
      case DO_STATEMENT:
        return createLoopTree(t, ((DoWhileStatementTree)t).statement(), ((DoWhileStatementTree)t).condition(), LoopTree.LoopKind.DOWHILE, keyword(((DoWhileStatementTree)t).doKeyword()));
      case IF_STATEMENT:
        return createIfTree((IfStatementTree)t);
      case SWITCH_STATEMENT:
        return createMatchTree((SwitchStatementTree)t);
      case ASSIGNMENT:
      return new AssignmentExpressionTreeImpl(metaData(t), org.sonarsource.slang.api.AssignmentExpressionTree.Operator.EQUAL,
          convert(((AssignmentExpressionTree)t).variable()),
          convert(((AssignmentExpressionTree)t).expression()));
      case BLOCK:
        return new BlockTreeImpl(metaData(t), convert(((org.sonar.plugins.java.api.tree.BlockTree) t).body()));
      case IDENTIFIER:
        return createIdentifierTree((org.sonar.plugins.java.api.tree.IdentifierTree) t);
      case MEMBER_SELECT:
        return createMemberSelectTree((MemberSelectExpressionTree) t);
      case BREAK_STATEMENT:
        return createBreakTree((BreakStatementTree) t);
      case CONTINUE_STATEMENT:
        return createContinueTree((ContinueStatementTree) t);
      case RETURN_STATEMENT:
        return createReturnTree((ReturnStatementTree)t);
      case TRY_STATEMENT:
        return createTryTree((TryStatementTree) t);
      case THROW_STATEMENT:
        return createThrowTree((ThrowStatementTree) t);
      case INT_LITERAL:
      case LONG_LITERAL:
      case FLOAT_LITERAL:
      case DOUBLE_LITERAL:
      case BOOLEAN_LITERAL:
      case CHAR_LITERAL:
      case STRING_LITERAL:
        return new LiteralTreeImpl(metaData(t), t.kind().toString());
      case NULL_LITERAL:
        return new LiteralTreeImpl(metaData(t), "null");
      case NOT_EQUAL_TO:
        return createBinaryExpression((org.sonar.plugins.java.api.tree.BinaryExpressionTree) t, BinaryExpressionTree.Operator.NOT_EQUAL_TO);
      case EQUAL_TO: //TODO: add other binary
        return createBinaryExpression((org.sonar.plugins.java.api.tree.BinaryExpressionTree) t, BinaryExpressionTree.Operator.EQUAL_TO);
      case PLUS:
        return createBinaryExpression((org.sonar.plugins.java.api.tree.BinaryExpressionTree) t, BinaryExpressionTree.Operator.PLUS);
      case CONDITIONAL_OR:
        return createBinaryExpression((org.sonar.plugins.java.api.tree.BinaryExpressionTree) t, BinaryExpressionTree.Operator.CONDITIONAL_OR);
      case CONDITIONAL_AND:
        return createBinaryExpression((org.sonar.plugins.java.api.tree.BinaryExpressionTree) t, BinaryExpressionTree.Operator.CONDITIONAL_AND);
      case VARIABLE:
        return createVariableTree((VariableTree) t);
      case PARENTHESIZED_EXPRESSION:
        return new ParenthesizedExpressionTreeImpl(metaData(t), convert(((ParenthesizedTree)t).expression()),
            keyword(((ParenthesizedTree) t).openParenToken()),
            keyword(((ParenthesizedTree) t).closeParenToken()));
      default:
        return createNativeTree(t);
        // Ignore other kind of elements, no change of gen/kill
    }
  }

  private Tree createMemberSelectTree(MemberSelectExpressionTree t) {
    return new MemberSelectImpl(metaData(t), convert(t.expression()), createIdentifierTree(t.identifier()));
  }

  private Tree createVariableTree(VariableTree t) {
    IdentifierTree identifier = createIdentifierTree(t.simpleName());
    Tree type = null;
    Tree init = null;
    if(t.initializer() != null){
      init = convert(t.initializer());
    }
    if(t.type() != null){
      //TODO
    }
    return new VariableDeclarationTreeImpl(metaData(t), identifier, type, init,  false);
  }

  private Tree createBinaryExpression(org.sonar.plugins.java.api.tree.BinaryExpressionTree t, BinaryExpressionTree.Operator operator) {
   return new BinaryExpressionTreeImpl(metaData(t), operator, keyword(t.operatorToken()),
    convert(t.leftOperand()),
    convert(t.rightOperand()));
  }

  private IdentifierTree createIdentifierTree(org.sonar.plugins.java.api.tree.IdentifierTree t) {
    return new IdentifierTreeImpl(metaData(t), (t).name());
  }

  private Tree createMethodInvocation(MethodInvocationTree t) {
    Tree methodSelect = convert(t.methodSelect());
    List<Tree> arguments = convert((List<ExpressionTree>)t.arguments());

    return new FunctionInvocationTreeImpl(metaData(t), methodSelect, arguments);
  }

  private Tree createMatchTree(SwitchStatementTree t) {
    Tree expression = convert(t.expression());

    List<MatchCaseTree> cases = t.cases().stream().map(this::createNatchCaseTree).collect(Collectors.toList());

    return new MatchTreeImpl(metaData(t), expression, cases, keyword(t.switchKeyword()));
  }

  private MatchCaseTree createNatchCaseTree(CaseGroupTree t) {
    Tree label;
    List<Tree> labels = convert(t.labels());
    if(labels.size() == 1) {
      label = labels.get(0);
    } else {
      label = createNativeTree(t, labels);
    }

    Tree body = createNativeTree(t, convert(t.body()));

    return new MatchCaseTreeImpl(metaData(t), label, body);
  }


  private Tree createIfTree(IfStatementTree t) {
    Tree cond = convert(t.condition());
    Tree thenP = convert(t.thenStatement());
    Tree elseP = null;
    Token elseKeyword = null;
    if(t.elseStatement() != null){
      elseP = convert(t.elseStatement());
      elseKeyword = keyword(t.elseKeyword());
    }
    return new IfTreeImpl(metaData(t), cond, thenP, elseP, keyword(t.ifKeyword()), elseKeyword);
  }

  private Tree createLoopTree(org.sonar.plugins.java.api.tree.Tree t, org.sonar.plugins.java.api.tree.Tree body, org.sonar.plugins.java.api.tree.Tree cond, LoopTree.LoopKind kind, Token keyword) {
    return new LoopTreeImpl(metaData(t), convert(cond), convert(body), kind, keyword);
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

  private Tree createTryTree(TryStatementTree t) {
    Tree tryBlock = convert(t.block());
    List<org.sonarsource.slang.api.CatchTree> catchBlocks = t.catches().stream().map(this::createCatchTree).collect(Collectors.toList());
    Tree finallyBlock = null;
    if(t.finallyBlock() != null){
      finallyBlock = convert(t.finallyBlock());
    }
    return new ExceptionHandlingTreeImpl(metaData(t), tryBlock, keyword(t.tryKeyword()), catchBlocks, finallyBlock);
  }


  private Tree createThrowTree(ThrowStatementTree t) {
    return new ThrowTreeImpl(metaData(t), keyword(t.throwKeyword()), convert(t.expression()));
  }

  private org.sonarsource.slang.api.CatchTree createCatchTree(CatchTree t) {
    Tree catchParameters = convert(t.parameter());
    Tree catchBlock = convert(t.block());
    return new CatchTreeImpl(metaData(t), catchParameters, catchBlock, keyword(t.catchKeyword()));
  }

  private Tree createBreakTree(org.sonar.plugins.java.api.tree.BreakStatementTree t){
    IdentifierTree labelName = null;
    if(t.label() != null){
      labelName = (IdentifierTree)convert(t.label());
    }
    return new JumpTreeImpl(metaData(t), keyword(t.breakKeyword()), JumpTree.JumpKind.BREAK, labelName);
  }

  private Tree createContinueTree(org.sonar.plugins.java.api.tree.ContinueStatementTree t){
    IdentifierTree labelName = null;
    if(t.label() != null){
      labelName = (IdentifierTree)convert(t.label());
    }
    return new JumpTreeImpl(metaData(t), keyword(t.continueKeyword()), JumpTree.JumpKind.CONTINUE, labelName);
  }

  private Tree createReturnTree(ReturnStatementTree t) {
    Tree body = null;
    if(t.expression() != null){
      body = convert(t.expression());
    }

    return new ReturnTreeImpl(metaData(t), keyword(t.returnKeyword()), body);
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
    return new NativeTreeImpl(metaData(t), new SJavaNativeKind(t.kind().toString()), children);
  }


  public TreeMetaData metaData(org.sonar.plugins.java.api.tree.Tree t) {
    return metaDataProvider.metaData(getTextRange(t));
  }

  private Token keyword(org.sonar.plugins.java.api.tree.SyntaxToken t) {
    return metaDataProvider.keyword(getTextRange(t));
  }

  private TextRange getTextRange(org.sonar.plugins.java.api.tree.Tree t) {
    if(t instanceof SyntaxToken) {
      return getTextRange((SyntaxToken) t);
    } else {
      int startL = t.firstToken().line();
      int startC = t.firstToken().column();
      int endL = t.lastToken().line();
      int endC = t.lastToken().column() + t.lastToken().text().length();
      if(startL == endL && startC == endC) {
        throw new ParseException("Unknown token");
      }

      return new TextRangeImpl(startL, startC, endL, endC);
    }
  }

  private TextRange getTextRange(org.sonar.plugins.java.api.tree.SyntaxToken t) {
    int startLine = t.line();
    int startCol = t.column();
    return new TextRangeImpl(startLine, startCol, startLine, startCol + t.text().length());
  }
}
