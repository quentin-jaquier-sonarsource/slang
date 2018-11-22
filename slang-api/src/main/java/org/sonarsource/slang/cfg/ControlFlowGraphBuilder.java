package org.sonarsource.slang.cfg;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.checkerframework.checker.units.qual.C;
import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.IfTree;
import org.sonarsource.slang.api.LoopTree;
import org.sonarsource.slang.api.Tree;

class ControlFlowGraphBuilder {

  private final Set<SlangCfgBlock> blocks = new HashSet<>();
  private final SlangCfgBlock end = new SlangCfgEndBlock();

  private CfgBlock currentBlock = createSimpleBlock(end);

  private final LinkedList<Breakable> breakables = new LinkedList<>();

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
    } else if(tree instanceof LoopTree) {
      return buildLoopStatement((LoopTree) tree, currentBlock);
    } else {
      currentBlock.addElement(tree);
      return currentBlock;
    }
  }

  private SlangCfgBlock buildLoopStatement(LoopTree tree, SlangCfgBlock successor) {
    switch (tree.kind()) {
      case DOWHILE:
        return buildDoWhileStatement(tree, successor);
      case WHILE:
        return buildWhileStatement(tree, successor);
      case FOR:
        return buildForStatement(tree, successor);
      default:
          throw new UnsupportedOperationException("Unknown loop tree kind");
    }
  }

  private SlangCfgBlock buildForStatement(LoopTree tree, SlangCfgBlock successor) {
    // We have no separation of the for loop content, naively build the same way as a while loop
    //TODO: Can we do better here ?
    return buildWhileStatement(tree, successor);
  }

  private SlangCfgBlock buildDoWhileStatement(LoopTree tree, SlangCfgBlock successor) {
    ForwardingBlock linkToBody = createForwardingBlock();
    SlangCfgBranchingBlock conditionBlock = createBranchingBlock(tree, linkToBody, successor);

    conditionBlock.addElement(tree.condition());

    //addBreakable(successor, conditionBlock);
    SlangCfgBlock loopBodyBlock = buildSubFlow(ImmutableList.of(tree.body()), conditionBlock);
    //removeBreakable();

    linkToBody.setSuccessor(loopBodyBlock);
    return createSimpleBlock(loopBodyBlock);
  }

  private SlangCfgBlock buildWhileStatement(LoopTree tree, SlangCfgBlock successor) {
    ForwardingBlock linkToCondition = createForwardingBlock();

    //addBreakable(successor, linkToCondition);
    SlangCfgBlock loopBodyBlock = buildSubFlow(tree.body(), linkToCondition);
    //removeBreakable();

    SlangCfgBranchingBlock conditionBlock = createBranchingBlock(tree, loopBodyBlock, successor);

    conditionBlock.addElement(tree.condition());
    linkToCondition.setSuccessor(conditionBlock);
    return createSimpleBlock(conditionBlock);
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

  private SlangCfgBlock buildSubFlow(Tree subFlowTree, SlangCfgBlock successor) {
    if(subFlowTree instanceof BlockTree){
      return buildSubFlow(((BlockTree)subFlowTree).statementOrExpressions(), successor);
    } else {
      return build(subFlowTree, createSimpleBlock(successor));
    }
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

  private ForwardingBlock createForwardingBlock() {
    ForwardingBlock block = new ForwardingBlock();
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

  private void removeBreakable() {
    breakables.pop();
  }

  private void addBreakable(SlangCfgBlock breakTarget, SlangCfgBlock continueTarget) {
    breakables.push(new Breakable(breakTarget, continueTarget));
  }

  private static class ForwardingBlock extends SlangCfgBlock {

    private SlangCfgBlock successor;

    @Override
    public ImmutableSet<CfgBlock> successors() {
      Preconditions.checkState(successor != null, "No successor was set on %s", this);
      return ImmutableSet.of(successor);
    }

    @Override
    public void addElement(Tree element) {
      throw new UnsupportedOperationException("Cannot add an element to a forwarding block");
    }

    void setSuccessor(SlangCfgBlock successor) {
      this.successor = successor;
    }

    @Override
    public void replaceSuccessors(Map<SlangCfgBlock, SlangCfgBlock> replacements) {
      throw new UnsupportedOperationException("Cannot replace successors for a forwarding block");
    }
  }

  private static class Breakable {
    SlangCfgBlock breakTarget;
    SlangCfgBlock continueTarget;

    Breakable(SlangCfgBlock breakTarget, SlangCfgBlock continueTarget) {
      this.breakTarget = breakTarget;
      this.continueTarget = continueTarget;
    }
  }

  static class TryBodyEnd {
    final SlangCfgBlock catchAndFinally;
    final SlangCfgBlock exitBlock;

    TryBodyEnd(SlangCfgBlock catchAndFinally, SlangCfgBlock exitBlock) {
      this.catchAndFinally = catchAndFinally;
      this.exitBlock = exitBlock;
    }
  }

}
