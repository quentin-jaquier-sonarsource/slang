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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;
import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.CatchTree;
import org.sonarsource.slang.api.ExceptionHandlingTree;
import org.sonarsource.slang.api.IfTree;
import org.sonarsource.slang.api.JumpTree;
import org.sonarsource.slang.api.LoopTree;
import org.sonarsource.slang.api.MatchCaseTree;
import org.sonarsource.slang.api.MatchTree;
import org.sonarsource.slang.api.NativeTree;
import org.sonarsource.slang.api.ReturnTree;
import org.sonarsource.slang.api.ThrowTree;
import org.sonarsource.slang.api.Tree;

class ControlFlowGraphBuilder {

  private final List<SlangCfgBlock> blocks = new ArrayList<>();
  private final SlangCfgBlock end = new SlangCfgEndBlock();

  private final LinkedList<Breakable> breakables = new LinkedList<>();

  private final Deque<SlangCfgBlock> throwTargets = new ArrayDeque<>();

  private final Deque<TryBodyEnd> exitTargets = new LinkedList<>();

  private SlangCfgBlock start;

  private boolean reliable = true;

  private boolean reliableSubFlow = true;

  public ControlFlowGraphBuilder(List<? extends Tree> items) {
    throwTargets.push(end);
    exitTargets.push(new TryBodyEnd(end, end));
    start = build(items, createSimpleBlock(end));
    removeEmptyBlocks();
    start = createSimpleBlock(start);
    blocks.add(end);
    computePredecessors();
  }

  ControlFlowGraph getGraph() {
    return new ControlFlowGraph(ImmutableList.copyOf(blocks), start, end, reliable);
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
      //Break the current block if the next part is unreliable (and not still in unreliable block)
      if(!currentBlock.isReliable() && reliableSubFlow){
        currentBlock = createSimpleBlock(currentBlock);
      }
      currentBlock = build(tree, currentBlock);
    }

    return currentBlock;
  }

  private SlangCfgBlock build(Tree tree, SlangCfgBlock currentBlock) {
    if(!reliableSubFlow){
      makeUnreliable(currentBlock);
    }
    if(tree instanceof MatchTree) {
      return buildMatchTree((MatchTree) tree, currentBlock);
    } else if(tree instanceof JumpTree) {
      return buildJumpTree((JumpTree)tree, currentBlock);
    } else if(tree instanceof BlockTree){
      return buildBlock((BlockTree)tree, currentBlock);
    } else if(tree instanceof IfTree) {
      return buildIfStatement((IfTree) tree, currentBlock);
    } else if(tree instanceof LoopTree) {
      return buildLoopStatement((LoopTree) tree, currentBlock);
    } else if(tree instanceof ReturnTree) {
      return buildReturnStatement((ReturnTree) tree, currentBlock);
    } else if(tree instanceof ThrowTree) {
      return buildThrowStatement((ThrowTree) tree, currentBlock);
    } else if(tree instanceof ExceptionHandlingTree) {
      return buildExceptionHandling((ExceptionHandlingTree)tree, currentBlock);
    } else if(tree instanceof NativeTree) {
      if(tree.children().isEmpty()){
        currentBlock.addElement(tree);
        return currentBlock;
      } else if(tree.children().size() == 1) {
        //Add the child independently
        return build(tree.children(), currentBlock);
      } else {
        reliableSubFlow = false;
        SlangCfgBlock nativeB = buildSubFlow(tree.children(), currentBlock);
        reliableSubFlow = true;
        return nativeB;
      }
    } else {
      if(tree != null) {
        if(!tree.children().isEmpty()) {
          currentBlock =  build(tree.children(), currentBlock);
        }
        currentBlock.addElement(tree);
      }
      return currentBlock;
    }
  }

  private SlangCfgBlock buildMatchTree(MatchTree tree, SlangCfgBlock successor){
    if(tree.breakable()) {
      return buildBreakableMatchTree(tree, successor);
    } else {
      return buildSimpleMatchTree(tree, successor);
    }
  }

  private SlangCfgBlock buildSimpleMatchTree(MatchTree tree, SlangCfgBlock successor){
    //We assume that only one case can be executed
    Set<SlangCfgBlock> cases = new HashSet<>();

    for (MatchCaseTree caseTree : Lists.reverse(tree.cases())) {
      SlangCfgBlock cas = buildSubFlow(caseTree, successor);
      cases.add(cas);
    }

    SlangCfgBlock condition = createMultiSuccessorBlock(cases);
    return build(tree.expression(), condition);
  }

  private SlangCfgBlock buildBreakableMatchTree(MatchTree tree, SlangCfgBlock successor){
    ForwardingBlock defaultBlock = createForwardingBlock();
    defaultBlock.setSuccessor(successor);
    SlangCfgBlock nextCase = defaultBlock;
    SlangCfgBlock caseBody = successor;
    addBreakable(successor, successor);
    for (MatchCaseTree caseTree : Lists.reverse(tree.cases())) {
      caseBody = buildSubFlow(caseTree.body(), caseBody);
      SlangCfgBranchingBlock caseBranch = createBranchingBlock(caseTree, caseBody, nextCase);
      caseBranch.addElement(caseTree.expression());
      nextCase = caseBranch;
    }
    removeBreakable();
    SlangCfgBlock block = createSimpleBlock(nextCase);
    return build(tree.expression(), block);
  }

  private SlangCfgBlock buildJumpTree(JumpTree tree, SlangCfgBlock successor) {
    switch (tree.kind()) {
      case BREAK:
        return buildBreakStatement(tree, successor);
      case CONTINUE:
        return buildContinueStatement(tree, successor);
      default:
        throw new UnsupportedOperationException("Unknown loop tree kind");
    }
  }

  private SlangCfgBlock buildBreakStatement(JumpTree tree, SlangCfgBlock successor) {
    if(breakables.isEmpty()){
      //Break with unknown parent (i.e. in switch); do nothing
      makeUnreliable(successor);
      successor.addElement(tree);
      return successor;
    } else {
      checkReliableJump(tree, successor);
      SlangCfgBlock newBlock = createBlockWithSyntacticSuccessor(breakables.peek().breakTarget, successor);
      checkReliableJump(tree, newBlock);
      newBlock.addElement(tree);
      return newBlock;
    }
  }

  private SlangCfgBlock buildContinueStatement(JumpTree tree, SlangCfgBlock successor) {
    if(breakables.isEmpty()){
      //Continue with unknown parent; do nothing
      makeUnreliable(successor);
      successor.addElement(tree);
      return successor;
    } else {
      checkReliableJump(tree, successor);
      SlangCfgBlock newBlock = createBlockWithSyntacticSuccessor(breakables.peek().continueTarget, successor);
      newBlock.addElement(tree);
      checkReliableJump(tree, newBlock);
      return newBlock;
    }
  }

  private void checkReliableJump(JumpTree tree, SlangCfgBlock currentBlock) {
    if (tree.label() != null) {
      makeUnreliable(currentBlock);
    }
  }

  private void makeUnreliable(CfgBlock b) {
    b.notReliable();
    reliable = false;
  }

  private SlangCfgBlock buildBlock(BlockTree block, SlangCfgBlock successor) {
    return build(block.statementOrExpressions(), successor);
  }

  private SlangCfgBlock buildExceptionHandling(ExceptionHandlingTree tree, SlangCfgBlock successor) {
    SlangCfgBlock exitBlock = exitTargets.peek().exitBlock;
    SlangCfgBlock finallyBlockEnd = createMultiSuccessorBlock(ImmutableSet.of(successor, exitBlock));
    SlangCfgBlock finallyBlock;
    if (tree.finallyBlock() != null) {
      finallyBlock = build(tree.finallyBlock(), finallyBlockEnd);
    } else {
      finallyBlock = finallyBlockEnd;
    }

    List<SlangCfgBlock> catchBlocks = tree.catchBlocks().stream()
        .map(catchBlockTree -> buildCatchBlock(catchBlockTree, finallyBlock))
        .collect(Collectors.toList());

    if (catchBlocks.isEmpty()) {
      throwTargets.push(finallyBlock);
    } else {
      throwTargets.push(catchBlocks.get(0));
    }
    Set<SlangCfgBlock> bodySuccessors = new HashSet<>(catchBlocks);
    bodySuccessors.add(finallyBlock);
    SlangCfgBlock tryBodySuccessors = createMultiSuccessorBlock(bodySuccessors);
    addBreakable(tryBodySuccessors, tryBodySuccessors);
    exitTargets.push(new TryBodyEnd(tryBodySuccessors, finallyBlock));
    SlangCfgBlock tryBodyStartingBlock = build(tree.tryBlock(), tryBodySuccessors);
    throwTargets.pop();
    exitTargets.pop();
    removeBreakable();

    return tryBodyStartingBlock;
  }

  private SlangCfgBlock buildCatchBlock(CatchTree catchBlock, SlangCfgBlock block) {
    SlangCfgBlock cfgBlock = buildSubFlow(catchBlock.catchBlock(), block);

    SlangCfgBlock retCfgBlock = build(catchBlock.catchParameter(), cfgBlock);
    retCfgBlock.addElement(catchBlock);
    return retCfgBlock;
  }

  private SlangCfgBlock buildThrowStatement(ThrowTree tree, SlangCfgBlock successor) {
    // taking "latest" throw target is an estimation
    // In real a matching `catch` clause should be found (by exception type)
    SlangCfgBlock simpleBlock = createBlockWithSyntacticSuccessor(throwTargets.peek(), successor);
    SlangCfgBlock retBlock = build(tree.body(), simpleBlock);
    retBlock.addElement(tree);
    return retBlock;
  }

  private SlangCfgBlock buildReturnStatement(ReturnTree tree, SlangCfgBlock successor) {
    SlangCfgBlock simpleBlock = createBlockWithSyntacticSuccessor(exitTargets.peek().exitBlock, successor);
    SlangCfgBlock retBlock = build(tree.body(), simpleBlock);
    retBlock.addElement(tree);
    return retBlock;
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

    SlangCfgBlock retCondition = build(tree.condition(), conditionBlock);

    addBreakable(successor, retCondition);
    SlangCfgBlock loopBodyBlock = buildSubFlow(ImmutableList.of(tree.body()), retCondition);
    removeBreakable();

    linkToBody.setSuccessor(loopBodyBlock);
    return createSimpleBlock(loopBodyBlock);
  }

  private SlangCfgBlock buildWhileStatement(LoopTree tree, SlangCfgBlock successor) {
    ForwardingBlock linkToCondition = createForwardingBlock();

    addBreakable(successor, linkToCondition);
    SlangCfgBlock loopBodyBlock = buildSubFlow(tree.body(), linkToCondition);
    removeBreakable();

    SlangCfgBranchingBlock conditionBlock = createBranchingBlock(tree, loopBodyBlock, successor);

    //If the confition block is made of multiple blocks, we have to take the last
    SlangCfgBlock ret = build(tree.condition(), conditionBlock);

    linkToCondition.setSuccessor(ret);

    return createSimpleBlock(ret);
  }


  private SlangCfgBlock buildIfStatement(IfTree tree, SlangCfgBlock successor) {
    SlangCfgBlock falseBlock = successor;
    if (tree.elseBranch() != null) {
      falseBlock = buildSubFlow(tree.elseBranch(), successor);
    }
    SlangCfgBlock trueBlock = buildSubFlow(tree.thenBranch(), successor);
    SlangCfgBranchingBlock conditionBlock = createBranchingBlock(tree, trueBlock, falseBlock);
    return build(tree.condition(), conditionBlock);
  }

  private SlangCfgBlock buildSubFlow(Tree subFlowTree, SlangCfgBlock successor) {
    SlangCfgBlock currentSubFlow;

    if(subFlowTree instanceof MatchCaseTree) {
      //Add the expression and body in the same block
      MatchCaseTree tree = (MatchCaseTree) subFlowTree;
      List<Tree> expressionAndBody = new ArrayList<>();
      if(tree.body() != null) {
        if (tree.body() instanceof BlockTree) {
          expressionAndBody.addAll(((BlockTree) tree.body()).statementOrExpressions());
        } else {
          expressionAndBody.add(tree.body());
        }
      }
      expressionAndBody.add(tree.expression());
      currentSubFlow = buildSubFlow(expressionAndBody, successor);
    } else if(subFlowTree instanceof BlockTree){
      currentSubFlow = buildSubFlow(((BlockTree)subFlowTree).statementOrExpressions(), successor);
    } else {
      currentSubFlow = build(subFlowTree, createSimpleBlock(successor));
    }

    if(!reliableSubFlow) {
      makeUnreliable(currentSubFlow);
    }
    return currentSubFlow;
  }

  private SlangCfgBlock buildSubFlow(List<Tree> subFlowTree, SlangCfgBlock successor) {
    SlangCfgBlock currentSubFlow = build(subFlowTree, createSimpleBlock(successor));
    if(!reliableSubFlow) {
      makeUnreliable(currentSubFlow);
    }
    return currentSubFlow;
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
