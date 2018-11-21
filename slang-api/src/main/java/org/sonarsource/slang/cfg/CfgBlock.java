package org.sonarsource.slang.cfg;

import java.util.List;
import java.util.Set;
import org.sonarsource.slang.api.Tree;

/**
 * A node of a {@link ControlFlowGraph}.
 * Successors are the nodes which may be executed after this one.
 * Predecessors are the nodes which may be executed before this one.
 * Elements are instances of {@link Tree} which are evaluated one after the other.
 */
public interface CfgBlock {
  Set<CfgBlock> predecessors();
  Set<CfgBlock> successors();
  List<Tree> elements();
}