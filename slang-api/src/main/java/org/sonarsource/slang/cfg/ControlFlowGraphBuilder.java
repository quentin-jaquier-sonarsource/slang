package org.sonarsource.slang.cfg;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.IfTree;
import org.sonarsource.slang.api.Tree;

class ControlFlowGraphBuilder {

  private final Set<CfgBlock> blocks = new HashSet<>();
  private final PhpCfgEndBlock end = new PhpCfgEndBlock();

  private CfgBlock currentBlock = createSimpleBlock(end);

  private ControlFlowGraph createGraph(List<? extends Tree> items) {
    // TODO add end to throw targets
    build(items);
    PhpCfgBlock start = currentBlock;
    removeEmptyBlocks();
    blocks.add(end);
    return new ControlFlowGraph(blocks, start, end);
  }

  private void build(List<? extends Tree> trees) {
    Collections.reverse(trees);
    for (Tree tree : trees) {
      build(tree);
    }
  }

  private void build(Tree tree) {
    if (tree instanceof IfTree) {
      visitIfStatement((IfTree) tree);
    } else if (tree instanceof BlockTree) {
      visitBlock((BlockTree) tree);
    } else {
      currentBlock.addElement(tree);
    }
  }



  private void visitIfStatement(IfTree tree) {
  }
  private void visitBlock(BlockTree tree) {
  }

  private CfgBlock createSimpleBlock(CfgBlock successor) {
    CfgBlock block = new SlangCfgBlock(successor);
    blocks.add(block);
    return block;
  }

  private CfgBlock createBranchingBlock(Tree branchingTree, CfgBlock trueSuccessor, CfgBlock falseSuccessor) {
    CfgBlock block = new C(branchingTree, trueSuccessor, falseSuccessor);
    blocks.add(block);
    return block;
  }
}






  /*
  private final Set<PhpCfgBlock> blocks = new HashSet<>();
  private final PhpCfgEndBlock end = new PhpCfgEndBlock();
  private PhpCfgBlock currentBlock = createSimpleBlock(end);
  ControlFlowGraph createGraph(BlockTree body) {
    return createGraph(body.statements());
  }
  ControlFlowGraph createGraph(ScriptTree scriptTree) {
    return createGraph(scriptTree.statements());
  }
  private ControlFlowGraph createGraph(List<? extends Tree> items) {
    // TODO add end to throw targets
    build(items);
        PhpCfgBlock start = currentBlock;
        removeEmptyBlocks();
        blocks.add(end);
        return new ControlFlowGraph(blocks, start, end);
  }
private void removeEmptyBlocks() {
    Map<PhpCfgBlock, PhpCfgBlock> emptyBlockReplacements = new HashMap<>();
    for (PhpCfgBlock block : blocks) {
    if (block.elements().isEmpty()) {
    PhpCfgBlock firstNonEmptySuccessor = block.skipEmptyBlocks();
    emptyBlockReplacements.put(block, firstNonEmptySuccessor);
    }
    }
    blocks.removeAll(emptyBlockReplacements.keySet());
    for (PhpCfgBlock block : blocks) {
    block.replaceSuccessors(emptyBlockReplacements);
    }
    }

private void build(Tree tree) {
    if (tree.is(Kind.IF_STATEMENT)) {
    visitIfStatement((IfStatementTree) tree);
    } else if (tree.is(Kind.BLOCK)) {
    visitBlock((BlockTree) tree);
    } else if (tree.is(Kind.EXPRESSION_STATEMENT)) {
    currentBlock.addElement(tree);
    } else {
    throw new UnsupportedOperationException("Not supported tree kind " + tree.getKind());
    }
    }
private void visitBlock(BlockTree block) {
    build(block.statements());
    }
private void visitIfStatement(IfStatementTree tree) {
    PhpCfgBlock successor = currentBlock;
    PhpCfgBlock elseBlock = currentBlock;
    buildSubFlow(tree.statements(), successor);
    PhpCfgBlock thenBlock = currentBlock;
    currentBlock = createBranchingBlock(tree, thenBlock, elseBlock);
    currentBlock.addElement(tree.condition().expression());
    }
private void buildSubFlow(List<StatementTree> subFlowTree, PhpCfgBlock successor) {
    currentBlock = createSimpleBlock(successor);
    build(subFlowTree);
    }
private PhpCfgBranchingBlock createBranchingBlock(Tree branchingTree, PhpCfgBlock trueSuccessor, PhpCfgBlock falseSuccessor) {
    PhpCfgBranchingBlock block = new PhpCfgBranchingBlock(branchingTree, trueSuccessor, falseSuccessor);
    blocks.add(block);
    return block;
    }

    }*/