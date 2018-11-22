package org.sonarsource.slang.cfg;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.sonarsource.slang.api.FunctionDeclarationTree;
import org.sonarsource.slang.api.Tree;

import static org.junit.Assert.assertEquals;
import static org.sonarsource.slang.utils.TreeCreationUtils.assignment;
import static org.sonarsource.slang.utils.TreeCreationUtils.block;
import static org.sonarsource.slang.utils.TreeCreationUtils.identifier;
import static org.sonarsource.slang.utils.TreeCreationUtils.simpleFunction;
import static org.sonarsource.slang.utils.TreeCreationUtils.simpleIfTree;


public class CfgTest {


  @Test
  public void test() {
    List<Tree> body = new ArrayList<>();

    body.add(assignment(identifier("a"), identifier("1")));

    body.add(simpleIfTree(
        identifier("cond"), //Cond
        block(assignment(identifier("a"), identifier("b"))), //Then block
        null //Else block
    ));

    FunctionDeclarationTree f = simpleFunction(identifier("foo"), block(body));

    ControlFlowGraph cfg = ControlFlowGraph.build(f);

    assertEquals(3, cfg.blocks().size());
  }

}
