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

import java.util.HashMap;
import java.util.Map;

class CfgPrinter {

  private CfgPrinter() {
    // this is an utility class and should not be instantiated
  }

  public static String toDot(ControlFlowGraph cfg) {
    StringBuilder sb = new StringBuilder();
    int graphNodeId = 0;
    Map<CfgBlock, Integer> graphNodeIds = new HashMap<>();
    for (CfgBlock block : cfg.blocks()) {
      graphNodeIds.put(block, graphNodeId);
      graphNodeId++;
    }
    for (CfgBlock block : cfg.blocks()) {
      int id = graphNodeIds.get(block);
      sb.append(id + "[label=\"" + blockLabel(block) + "\"");
      if(!block.isReliable()){
        sb.append(",color=darkorange1,style=filled");
      }
      sb.append( "];");
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
        if(!block.isReliable()){
          edgeLabel = "[color=darkorange1]";
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
    return block.toString();
  }

}
