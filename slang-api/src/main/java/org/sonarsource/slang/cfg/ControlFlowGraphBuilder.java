package org.sonarsource.slang.cfg;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.checkerframework.checker.units.qual.C;
import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.IfTree;
import org.sonarsource.slang.api.Tree;

class ControlFlowGraphBuilder {

  private final Set<SlangCfgBlock> blocks = new HashSet<>();
  private final SlangCfgBlock end = new SlangCfgEndBlock();

  private CfgBlock currentBlock = createSimpleBlock(end);

  private SlangCfgBlock start;

  public ControlFlowGraphBuilder(List<? extends Tree> items) {
//    throwTargets.push(end);
//    exitTargets.push(new TryBodyEnd(end, end));
    start = build(items, createSimpleBlock(end));
    removeEmptyBlocks();
    blocks.add(end);
    computePredecessors();

  }

  ControlFlowGraph getGraph() {
    return new ControlFlowGraph(ImmutableSet.copyOf(blocks), start, end);
  }

  private void computePredecessors() {
    for (SlangCfgBlock block : blocks) {
      for (CfgBlock successor : block.successors()) {
        ((SlangCfgBlock) successor).addPredecessor(block);
      }
    }
  }

  private void removeEmptyBlocks() {
    Map<SlangCfgBlock, SlangCfgBlock> emptyBlockReplacements = new HashMap<>();
    for (SlangCfgBlock block : blocks) {
      if (block.elements().isEmpty() && block.successors().size() == 1) {
        SlangCfgBlock firstNonEmptySuccessor = block.skipEmptyBlocks();
        emptyBlockReplacements.put(block, firstNonEmptySuccessor);
      }
    }

    blocks.removeAll(emptyBlockReplacements.keySet());

    for (SlangCfgBlock block : blocks) {
      block.replaceSuccessors(emptyBlockReplacements);
    }

    start = emptyBlockReplacements.getOrDefault(start, start);
  }

  private SlangCfgBlock build(List<? extends Tree> trees, SlangCfgBlock successor) {
    SlangCfgBlock currentBlock = successor;
    for (Tree tree : Lists.reverse(trees)) {
      currentBlock = build(tree, currentBlock);
    }

    return currentBlock;
  }

  private SlangCfgBlock build(Tree tree, SlangCfgBlock currentBlock) {
    if(tree instanceof IfTree) {
      return buildIfStatement((IfTree) tree, currentBlock);
    } else {
      currentBlock.addElement(tree);
      return currentBlock;
    }
  }


  private SlangCfgBlock buildIfStatement(IfTree tree, SlangCfgBlock successor) {
    SlangCfgBlock falseBlock = successor;
    if (tree.elseBranch() != null) {
      falseBlock = buildSubFlow(tree.elseBranch().children(), successor);
    }
    SlangCfgBlock trueBlock = buildSubFlow(tree.thenBranch().children(), successor);
    SlangCfgBranchingBlock conditionBlock = createBranchingBlock(tree, trueBlock, falseBlock);
    conditionBlock.addElement(tree.condition());
    return conditionBlock;
  }

  private SlangCfgBlock buildSubFlow(List<Tree> subFlowTree, SlangCfgBlock successor) {
    return build(subFlowTree, createSimpleBlock(successor));
  }

  private SlangCfgBlock createSimpleBlock(SlangCfgBlock successor) {
    SlangCfgBlock block = new SlangCfgBlock(successor);
    blocks.add(block);
    return block;
  }

  private SlangCfgBranchingBlock createBranchingBlock(Tree branchingTree, SlangCfgBlock trueSuccessor, SlangCfgBlock falseSuccessor) {
    SlangCfgBranchingBlock block = new SlangCfgBranchingBlock(branchingTree, trueSuccessor, falseSuccessor);
    blocks.add(block);
    return block;
  }

  private SlangCfgBlock createMultiSuccessorBlock(Set<SlangCfgBlock> successors) {
    SlangCfgBlock block = new SlangCfgBlock(successors);
    blocks.add(block);
    return block;
  }

  private SlangCfgBlock createBlockWithSyntacticSuccessor(SlangCfgBlock successor, SlangCfgBlock syntacticSuccessor) {
    SlangCfgBlock block = new SlangCfgBlock(successor, syntacticSuccessor);
    blocks.add(block);
    return block;
  }

}
