package org.sonarsource.slang.cfg;

import java.util.HashMap;
import java.util.Map;

class CfgPrinter {

  private CfgPrinter() {
    // this is an utility class and should not be instantiated
  }

  static String toDot(ControlFlowGraph cfg) {
    StringBuilder sb = new StringBuilder();
    int graphNodeId = 0;
    Map<CfgBlock, Integer> graphNodeIds = new HashMap<>();
    for (CfgBlock block : cfg.blocks()) {
      graphNodeIds.put(block, graphNodeId);
      graphNodeId++;
    }
    for (CfgBlock block : cfg.blocks()) {
      int id = graphNodeIds.get(block);
      sb.append(id + "[label=\"" + blockLabel(block) + "\"];");
    }
    for (CfgBlock block : cfg.blocks()) {
      int id = graphNodeIds.get(block);
      for (CfgBlock successor : block.successors()) {
        String edgeLabel = "";
        if (block instanceof SlangCfgBranchingBlock) {
          SlangCfgBranchingBlock branching = (SlangCfgBranchingBlock) block;
          // branch value can be True or False
          boolean branchingValue = successor.equals(branching.trueSuccessor());
          edgeLabel = "[label=" + branchingValue + "]";
        }
        sb.append(id + "->" + graphNodeIds.get(successor) + edgeLabel + ";");
      }
      if (block.syntacticSuccessor() != null) {
        sb.append(id + "->" + graphNodeIds.get(block.syntacticSuccessor()) + "[style=dotted];");
      }
    }

    return sb.toString();
  }

  private static String blockLabel(CfgBlock block) {
    return "Expected: " + block.toString();
  }

}