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
package org.sonarsource.slang.cfg;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.sonarsource.slang.api.BinaryExpressionTree;
import org.sonarsource.slang.api.CatchTree;
import org.sonarsource.slang.api.FunctionDeclarationTree;
import org.sonarsource.slang.api.IdentifierTree;
import org.sonarsource.slang.api.JumpTree;
import org.sonarsource.slang.api.LoopTree;
import org.sonarsource.slang.api.MatchCaseTree;
import org.sonarsource.slang.api.Tree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sonarsource.slang.utils.TreeCreationUtils.assignment;
import static org.sonarsource.slang.utils.TreeCreationUtils.binary;
import static org.sonarsource.slang.utils.TreeCreationUtils.block;
import static org.sonarsource.slang.utils.TreeCreationUtils.identifier;
import static org.sonarsource.slang.utils.TreeCreationUtils.jumpTree;
import static org.sonarsource.slang.utils.TreeCreationUtils.loop;
import static org.sonarsource.slang.utils.TreeCreationUtils.matchCaseTree;
import static org.sonarsource.slang.utils.TreeCreationUtils.matchTree;
import static org.sonarsource.slang.utils.TreeCreationUtils.simpleFunction;
import static org.sonarsource.slang.utils.TreeCreationUtils.simpleIfTree;
import static org.sonarsource.slang.utils.TreeCreationUtils.simpleNative;
import static org.sonarsource.slang.utils.TreeCreationUtils.simpleReturn;
import static org.sonarsource.slang.utils.TreeCreationUtils.exceptionHandlingTree;
import static org.sonarsource.slang.utils.TreeCreationUtils.catchTree;
import static org.sonarsource.slang.utils.TreeCreationUtils.throwTree;

public class CfgTest {


  @Test
  public void testOreAssign() {
     /*
      a = b || c;
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), binary(BinaryExpressionTree.Operator.CONDITIONAL_OR, identifier("b"), identifier("c"))));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(2, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testFunCall() {
     /*
      a = f(p == null);
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), binary(BinaryExpressionTree.Operator.CONDITIONAL_OR, identifier("b"), identifier("c"))));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(2, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testIf() {
     /*
      a = 1;
      if(cond) {
        b = 2;
      }
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(simpleIfTree(
        identifier("cond"), //Cond
        block(assignment(identifier("b"), identifier("2"))), //Then block
        null //Else block
    ));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(3, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testIfElse() {
     /*
      a = 1;
      a = 1;
      a = 1;

      if(cond) {
        b = 2;
      } else {
        c = 3;
      }
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(assignment(identifier("a"), identifier("1")));
    body.add(assignment(identifier("a"), identifier("1")));
    body.add(simpleIfTree(
        identifier("cond"), //Cond
        block(assignment(identifier("b"), identifier("2"))), //Then block
        block(assignment(identifier("c"), identifier("3"))) //Else block
    ));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(4, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }


  @Test
  public void testNestedIf() {
     /*
      a = 1;
      if(cond1) {
        if(cond2) {
          n = 3;
        }
        b = 2;
      }
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(simpleIfTree(
        identifier("cond1"), //Cond
        block(simpleIfTree(
            identifier("cond2"), //Cond
            block(


                assignment(identifier("n"), identifier("3"))), //Then block
            null //Else block
            )),
        null //Else block
    ));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(4, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testWhile() {
     /*
      a = 1;
      while(n == 5) {
        b = 2;
      }
      c = 3;
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(loop(
        binary(BinaryExpressionTree.Operator.EQUAL_TO, identifier("n"), identifier("5")), //Cond
        block(assignment(identifier("b"), identifier("2"))), //Then block
        LoopTree.LoopKind.WHILE, //Else block
        "while"
    ));

    body.add(assignment(identifier("d"), identifier("3")));
    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(5, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }


  @Test
  public void testWhileBreak() {
    ControlFlowGraph cfg = whileWithJumpCfg(JumpTree.JumpKind.BREAK);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(7, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testWhileBreakLabel() {
    ControlFlowGraph cfg = whileWithJumpCfg(JumpTree.JumpKind.BREAK, identifier("label"));

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(7, cfg.blocks().size());
    assertFalse(cfg.isReliable());
  }

  @Test
  public void testWhileContinue() {
    ControlFlowGraph cfg = whileWithJumpCfg(JumpTree.JumpKind.CONTINUE);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(7, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }
  private ControlFlowGraph whileWithJumpCfg(JumpTree.JumpKind kind) {
    return whileWithJumpCfg(kind, null);
  }

  private ControlFlowGraph whileWithJumpCfg(JumpTree.JumpKind kind, IdentifierTree label) {
    /*
      a = 1;
      while(n == 5) {
        b = 2;
        if(cond2) {
          n = 3;
          [$kind]; //(break or continue)
        }
        b = 2;
      }
      c = 3;
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(loop(
        binary(BinaryExpressionTree.Operator.EQUAL_TO, identifier("n"), identifier("5")), //Cond
        block(
            assignment(identifier("b"), identifier("2")),
            simpleIfTree(
                identifier("cond2"), //Cond
                block(
                    assignment(identifier("n"), identifier("3")), jumpTree(kind, label)), //Then block
                null //Else block
            ),
            assignment(identifier("b"), identifier("2"))
        ), //Body
        LoopTree.LoopKind.WHILE,
        "while"
    ));

    body.add(assignment(identifier("d"), identifier("3")));
    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    return ControlFlowGraph.build(f);
  }

  @Test
  public void testDoWhile() {
     /*
      a = 1;
      do {
        b = 2;
      } while(cond);
      c = 3;
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));

    body.add(loop(
        identifier("cond"), //Cond
        block(assignment(identifier("b"), identifier("2"))),
        LoopTree.LoopKind.DOWHILE,
        "do"
    ));

    body.add(assignment(identifier("d"), identifier("3")));
    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(5, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testReturn() {
     /*
       if(cond) {
        return a;
       } else {
        b = 2;
       };
       c = 3;
     */
    List<Tree> body = new ArrayList<>();

    body.add(simpleIfTree(
        identifier("cond"), //Cond
        block(simpleReturn(identifier("a"))), //Then block
        block(assignment(identifier("b"), identifier("2"))) //Else block
    ));

    body.add(assignment(identifier("c"), identifier("3")));
    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(5, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testTryCatch() {
     /*
       a = 1;

       try{
        if(cond) {
          throw e;
        }
        b = 2;
       } catch (f) {
        c = 3:
       } finally {
        d = 4;
        d = 4;
       }
       x = 5;
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));

    List<Tree> tryBody = new ArrayList<>();

    tryBody.add(simpleIfTree(
        identifier("cond2"), //Cond
        block(
            throwTree(identifier("e"))), //Then block
        null //Else block
    ));
    tryBody.add(assignment(identifier("b"), identifier("2")));

    List<CatchTree> catchTrees = new ArrayList<>();
    catchTrees.add(catchTree(identifier("f"), block(assignment(identifier("b"), identifier("2")))));

    body.add(exceptionHandlingTree(
        block(tryBody), //Cond
        catchTrees,
        block(assignment(identifier("d"), identifier("4")), assignment(identifier("d"), identifier("4")))
    ));

    body.add(assignment(identifier("x"), identifier("5")));
    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(7, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testMatch() {
     /*
    x = 0;

    match(aaa) {
      case cond1:
        a = 1;
      case cond2:
      {
        b = 2;
      }
      case cond3:
      c = 3;
    }

    d = 4;
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("x"), identifier("0")));

    List<MatchCaseTree> cases = new ArrayList<>();
    cases.add(matchCaseTree(identifier("cond1"), assignment(identifier("a"), identifier("1"))));
    List<Tree> caseBody = new ArrayList<>();
    caseBody.add(assignment(identifier("b"), identifier("2")));
    cases.add(matchCaseTree(identifier("cond2"), block(caseBody)));
    cases.add(matchCaseTree(identifier("cond3"), assignment(identifier("c"), identifier("2"))));

    body.add(matchTree(identifier("aaa"), cases));

    body.add(assignment(identifier("d"), identifier("4")));
    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(6, cfg.blocks().size());
    assertTrue(cfg.isReliable());
  }

  @Test
  public void testTreeWithNative() {
     /*
      a = 1;
      if(cond) {
        [Native]{
        b = 2
        b = 2};
      }
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(simpleIfTree(
        identifier("cond"), //Cond

        block(simpleNative(null, assignment(identifier("b"), identifier("2")),assignment(identifier("b"), identifier("2")))), //Then block
        null //Else block
    ));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(3, cfg.blocks().size());
    assertFalse(cfg.isReliable());
  }

  @Test
  public void testTreeWithNative3() {
     /*
      a = 1;
      if(cond) {
        [Native]{
        if(cond2) {
          b = 2;
        }
        if(cond3) {
          c = 3;
        }
      }
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(simpleIfTree(
        identifier("cond"), //Cond

        block(simpleNative(null,             simpleIfTree(
            identifier("cond2"),block(assignment(identifier("b"), identifier("2"))) , null),
            simpleIfTree(
                identifier("cond3"),block(assignment(identifier("c"), identifier("3"))) , null)
        )), //Then block
        null //Else block
    ));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(6, cfg.blocks().size());
    assertFalse(cfg.isReliable());
  }

  @Test
  public void testTreeWithNative2() {
     /*
      a = 1;
      if(cond) {
        [Native]{
        b = 2
        c = 3};
      }
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(simpleIfTree(
        identifier("cond"), //Cond

        block(simpleNative(null, assignment(identifier("b"), identifier("2")),assignment(identifier("c"), identifier("2")))), //Then block
        null //Else block
    ));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(3, cfg.blocks().size());
    assertFalse(cfg.isReliable());
  }

  @Test
  public void testMatchBreak() {
     /*
    x = 0;

    match(aaa) {
      case cond1:
        a = 1;
      case cond2:
        b = 2;
        break;
      case cond3:
      c = 3;
    }

    d = 4;
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("x"), identifier("0")));

    List<MatchCaseTree> cases = new ArrayList<>();
    cases.add(matchCaseTree(identifier("cond1"), assignment(identifier("a"), identifier("1"))));
    cases.add(matchCaseTree(identifier("cond2"),
        block(assignment(identifier("b"), identifier("2")), jumpTree(JumpTree.JumpKind.BREAK, null))));
    cases.add(matchCaseTree(identifier("cond3"), assignment(identifier("c"), identifier("3"))));

    body.add(matchTree(identifier("aaa"), cases));

    body.add(assignment(identifier("d"), identifier("4")));
    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(6, cfg.blocks().size());
    assertFalse(cfg.isReliable());
  }

  @Test
  public void testLostBreak() {
     /*
      a = 1;
      if(cond) {
        b = 2;
        break;
        b = 2;
      }
     */
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));
    body.add(simpleIfTree(
        identifier("cond"), //Cond
        block(assignment(identifier("b"), identifier("2")), jumpTree(JumpTree.JumpKind.BREAK, null),
            assignment(identifier("b"), identifier("2"))), //Then block
        null //Else block
    ));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(3, cfg.blocks().size());
    assertFalse(cfg.isReliable());
  }

}
