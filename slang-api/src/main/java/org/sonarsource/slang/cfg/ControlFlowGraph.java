package org.sonarsource.slang.cfg;

import java.util.Collections;
import java.util.Set;
import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.FunctionDeclarationTree;

public class ControlFlowGraph {
  private final CfgBlock start;
  private final SlangCfgBlock end;
  private final Set<CfgBlock> blocks;

  ControlFlowGraph(Set<CfgBlock> blocks, CfgBlock start, SlangCfgBlock end) {
    this.start = start;
    this.end = end;
    this.blocks = blocks;
  }

  public static ControlFlowGraph build(FunctionDeclarationTree function) {
    return build(function.body());
  }

  public static ControlFlowGraph build(BlockTree body) {
    return new ControlFlowGraphBuilder(body.statementOrExpressions()).getGraph();
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
