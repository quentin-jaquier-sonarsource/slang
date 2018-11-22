package org.sonarsource.slang.cfg;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.slang.api.Tree;

public class SlangCfgBlock implements CfgBlock {

  private Set<SlangCfgBlock> predecessors = new HashSet<>();
  private Set<SlangCfgBlock> successors;

  private SlangCfgBlock syntacticSuccessor;

  private LinkedList<Tree> elements = new LinkedList<>();

  public SlangCfgBlock(Set<SlangCfgBlock> successors, @Nullable SlangCfgBlock syntacticSuccessor) {
    this.successors = ImmutableSet.copyOf(successors);
    this.syntacticSuccessor = syntacticSuccessor;
  }

  SlangCfgBlock(SlangCfgBlock successor, SlangCfgBlock syntacticSuccessor) {
    this(ImmutableSet.of(successor), Preconditions.checkNotNull(syntacticSuccessor,
        "Syntactic successor cannot be null"));
  }

  SlangCfgBlock(Set<SlangCfgBlock> successors) {
    this(successors, null);
  }

  SlangCfgBlock(SlangCfgBlock successor) {
    this(ImmutableSet.of(successor));
  }

  SlangCfgBlock() {
    // needed by inheriting classes
  }

  @Override
  public Set<CfgBlock> predecessors() {
    return Collections.unmodifiableSet(predecessors);
  }

  @Override
  public Set<? extends CfgBlock> successors() {
    return successors;
  }

  @Nullable
  @Override
  public CfgBlock syntacticSuccessor() {
    return syntacticSuccessor;
  }

  @Override
  public List<Tree> elements() {
    return Collections.unmodifiableList(elements);
  }

  public void addElement(Tree element) {
    Preconditions.checkArgument(element != null, "Cannot add a null element to a block");
    elements.addFirst(element);
  }

  /**
   * Replace successors based on a replacement map.
   * This method is used when we remove empty blocks:
   * we have to replace empty successors in the remaining blocks by non-empty successors.
   */
  void replaceSuccessors(Map<SlangCfgBlock, SlangCfgBlock> replacements) {
    successors = successors.stream()
        .map(successor -> replacement(successor, replacements))
        .collect(ImmutableSet.toImmutableSet());
    if (syntacticSuccessor != null) {
      syntacticSuccessor = replacement(syntacticSuccessor, replacements);
    }
  }

  /**
   * Replace oldSucc with newSucc
   */
  void replaceSuccessor(SlangCfgBlock oldSucc, SlangCfgBlock newSucc) {
    Map<SlangCfgBlock, SlangCfgBlock> map = new HashMap<>();
    map.put(oldSucc, newSucc);
    replaceSuccessors(map);
  }

  static SlangCfgBlock replacement(SlangCfgBlock successor, Map<SlangCfgBlock, SlangCfgBlock> replacements) {
    SlangCfgBlock newSuccessor = replacements.get(successor);
    return newSuccessor == null ? successor : newSuccessor;
  }

  void addPredecessor(SlangCfgBlock predecessor) {
    predecessors.add(predecessor);
  }

  SlangCfgBlock skipEmptyBlocks() {
    Set<CfgBlock> skippedBlocks = new HashSet<>();
    SlangCfgBlock block = this;
    while (block.successors().size() == 1 && block.elements().isEmpty()) {
      SlangCfgBlock next = (SlangCfgBlock) block.successors().iterator().next();
      skippedBlocks.add(block);
      if (!skippedBlocks.contains(next)) {
        block = next;
      } else {
        return block;
      }
    }
    return block;
  }

  @Override
  public String toString() {
    if (elements.isEmpty()) {
      return "empty";
    }
    StringBuilder builder = new StringBuilder();
    for(Tree t: elements){
      builder.append(t.getClass().getSimpleName());
      builder.append("\\n");
    }
    return builder.toString();
  }
}
