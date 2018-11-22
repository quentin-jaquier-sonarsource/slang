package org.sonarsource.slang.cfg;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.slang.api.Tree;

/**
 * A node of a {@link ControlFlowGraph}.
 * Successors are the nodes which may be executed after this one.
 * Predecessors are the nodes which may be executed before this one.
 * Elements are instances of {@link Tree} which are evaluated one after the other.
 */
public interface CfgBlock {
  Set<? extends CfgBlock> predecessors();
  Set<? extends CfgBlock> successors();

  /**
   * @return block following this one if no jump is applied
   * Returns {@code null} if this block doesn't end with jump statement (break, continue, return, goto, throw)
   */
  @Nullable
  CfgBlock syntacticSuccessor();

  List<Tree> elements();
}