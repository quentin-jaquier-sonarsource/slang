package org.sonarsource.slang.cfg;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import org.sonarsource.slang.api.Tree;

public class SlangCfgEndBlock extends SlangCfgBlock {
  @Override
  public ImmutableSet<CfgBlock> successors() {
    return ImmutableSet.of();
  }

  @Override
  public void addElement(Tree element) {
    throw new UnsupportedOperationException("Cannot add element to end block");
  }

  @Override
  public void replaceSuccessors(Map<SlangCfgBlock, SlangCfgBlock> replacements) {
    throw new UnsupportedOperationException("Cannot replace successors of end block");
  }

  @Override
  public String toString() {
    return "END";
  }
}
