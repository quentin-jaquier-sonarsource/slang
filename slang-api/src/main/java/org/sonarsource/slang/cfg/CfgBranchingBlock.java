package org.sonarsource.slang.cfg;

import org.sonarsource.slang.api.Tree;

public interface CfgBranchingBlock extends CfgBlock {
  CfgBlock trueSuccessor();
  CfgBlock falseSuccessor();
  /**
   * Syntax tree causing branching: e.g. loop tree, switch case clause tree, if statement tree, "&&" expression, "||" expression
   */
  Tree branchingTree();
}