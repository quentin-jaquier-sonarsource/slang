package org.sonarsource.slang.cfg;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sonarsource.slang.api.Tree;
import com.google.common.collect.ImmutableSet;

public class SlangCfgBlock implements CfgBlock {

  private Set<CfgBlock> predecessors = new HashSet<>();
  private CfgBlock successor;

  public SlangCfgBlock(CfgBlock successor) {
    this.successor = successor;
  }

  @Override
  public Set<CfgBlock> predecessors() {
    return Collections.unmodifiableSet(predecessors);
  }

  @Override
  public Set<CfgBlock> successors() {
    return ImmutableSet.of(successor);
  }

  @Override
  public List<Tree> elements() {
    return null;
  }

  /*
  private Set<PhpCfgBlock> predecessors = new HashSet<>();
  private PhpCfgBlock successor;
   private LinkedList<Tree> elements = new LinkedList<>();
   PhpCfgBlock(PhpCfgBlock successor) {
    Preconditions.checkArgument(successor != null, "Successor cannot be null");
    this.successor = successor;
  }
   PhpCfgBlock() {
      // needed by inheriting classes
  }
   @Override
  public Set<CfgBlock> predecessors() {
    return Collections.unmodifiableSet(predecessors);
  }
   @Override
  public Set<CfgBlock> successors() {
    return ImmutableSet.of(successor);
  }
   @Override
  public List<Tree> elements() {
    return Collections.unmodifiableList(elements);
  }
   public void addElement(Tree element) {
    Preconditions.checkArgument(element != null, "Cannot add a null element to a block");
    elements.addFirst(element);
  }

  void replaceSuccessors(Map<PhpCfgBlock, PhpCfgBlock> replacements) {
    this.successor = replacement(successor, replacements);
  }
  static PhpCfgBlock replacement(PhpCfgBlock successor, Map<PhpCfgBlock, PhpCfgBlock> replacements) {
    PhpCfgBlock newSuccessor = replacements.get(successor);
    return newSuccessor == null ? successor : newSuccessor;
  }
  void addPredecessor(PhpCfgBlock predecessor) {
    predecessors.add(predecessor);
  }
  PhpCfgBlock skipEmptyBlocks() {
    Set<CfgBlock> skippedBlocks = new HashSet<>();
    PhpCfgBlock block = this;
    while (block.successors().size() == 1 && block.elements().isEmpty()) {
      PhpCfgBlock next = (PhpCfgBlock) block.successors().iterator().next();
      skippedBlocks.add(block);
      if (!skippedBlocks.contains(next)) {
        block = next;
      } else {
        return block;
      }
    }
    return block;
  }
   */
}
