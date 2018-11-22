package org.sonarsource.slang.cfg;

import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.signature.qual.Identifier;
import org.junit.Test;
import org.sonarsource.slang.api.BinaryExpressionTree;
import org.sonarsource.slang.api.FunctionDeclarationTree;
import org.sonarsource.slang.api.LoopTree;
import org.sonarsource.slang.api.Tree;

import static org.junit.Assert.assertEquals;
import static org.sonarsource.slang.utils.TreeCreationUtils.assignment;
import static org.sonarsource.slang.utils.TreeCreationUtils.binary;
import static org.sonarsource.slang.utils.TreeCreationUtils.block;
import static org.sonarsource.slang.utils.TreeCreationUtils.identifier;
import static org.sonarsource.slang.utils.TreeCreationUtils.loop;
import static org.sonarsource.slang.utils.TreeCreationUtils.simpleFunction;
import static org.sonarsource.slang.utils.TreeCreationUtils.simpleIfTree;


public class CfgTest {


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
  }

  @Test
  public void testIfElse() {
     /*
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
        block(assignment(identifier("b"), identifier("2"))), //Then block
        LoopTree.LoopKind.DOWHILE, //Else block
        "do"
    ));

    body.add(assignment(identifier("d"), identifier("3")));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    System.out.println(CfgPrinter.toDot(cfg));

    assertEquals(5, cfg.blocks().size());
  }

}
