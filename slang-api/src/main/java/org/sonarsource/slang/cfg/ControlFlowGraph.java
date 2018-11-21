package org.sonarsource.slang.cfg;

import java.util.Collections;
import java.util.Set;
import org.sonarsource.slang.api.BlockTree;

public class ControlFlowGraph {
  private final CfgBlock start;
  private final CfgBlock end;

  private final Set<CfgBlock> blocks;

  ControlFlowGraph(Set<CfgBlock> blocks, CfgBlock start, CfgBlock end) {
    this.start = start;
    this.end = end;
    this.blocks = blocks;
    for (CfgBlock block : blocks) {
      for (CfgBlock successor : block.successors()) {
        ((CfgBlock) successor).addPredecessor(block);
      }
    }
  }
  public static ControlFlowGraph build(BlockTree body) {
    return new ControlFlowGraphBuilder().createGraph(body);
  }
  public static ControlFlowGraph build(ScriptTree scriptTree) {
    return new ControlFlowGraphBuilder().createGraph(scriptTree);
  }
  public CfgBlock start() {
    return start;
  }
  public CfgBlock end() {
    return end;
  }
  /**
   * Includes start and end blocks
   */
  public Set<CfgBlock> blocks() {
    return Collections.unmodifiableSet(blocks);
  }



}
