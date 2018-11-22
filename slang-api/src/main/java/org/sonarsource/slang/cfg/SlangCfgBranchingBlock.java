package org.sonarsource.slang.cfg;


import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonarsource.slang.api.Tree;

public class SlangCfgBranchingBlock extends SlangCfgBlock implements CfgBranchingBlock {

  private SlangCfgBlock trueSuccessor;
  private SlangCfgBlock falseSuccessor;
  private Tree branchingTree;

  public SlangCfgBranchingBlock(Tree branchingTree, SlangCfgBlock trueSuccessor, SlangCfgBlock falseSuccessor) {
    this.trueSuccessor = trueSuccessor;
    this.falseSuccessor = falseSuccessor;
    this.branchingTree = branchingTree;
  }

  @Override
  public CfgBlock trueSuccessor() {
    return trueSuccessor;
  }

  @Override
  public CfgBlock falseSuccessor() {
    return falseSuccessor;
  }

  @Override
  public Tree branchingTree() {
    return branchingTree;
  }

  @Override
  public Set<CfgBlock> successors() {
    return ImmutableSet.of(trueSuccessor, falseSuccessor);
  }


  @Override
  public void replaceSuccessors(Map<SlangCfgBlock, SlangCfgBlock> replacements) {
    this.trueSuccessor = replacement(this.trueSuccessor, replacements);
    this.falseSuccessor = replacement(this.falseSuccessor, replacements);
  }

}
