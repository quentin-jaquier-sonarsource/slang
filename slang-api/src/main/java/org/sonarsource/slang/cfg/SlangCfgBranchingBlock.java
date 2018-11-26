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
