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

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.sonarsource.slang.api.BlockTree;
import org.sonarsource.slang.api.FunctionDeclarationTree;

public class ControlFlowGraph {
  private final CfgBlock start;
  private final SlangCfgBlock end;
  private final List<CfgBlock> blocks;

  ControlFlowGraph(List<CfgBlock> blocks, CfgBlock start, SlangCfgBlock end) {
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
  public List<CfgBlock> blocks() {
    return Collections.unmodifiableList(Lists.reverse(blocks));
  }



}
