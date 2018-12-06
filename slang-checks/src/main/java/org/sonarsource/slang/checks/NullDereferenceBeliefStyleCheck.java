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
package org.sonarsource.slang.checks;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.check.Rule;
import org.sonarsource.slang.api.AssignmentExpressionTree;
import org.sonarsource.slang.api.BinaryExpressionTree;
import org.sonarsource.slang.api.FunctionDeclarationTree;
import org.sonarsource.slang.api.FunctionInvocationTree;
import org.sonarsource.slang.api.IdentifierTree;
import org.sonarsource.slang.api.LiteralTree;
import org.sonarsource.slang.api.NativeTree;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.api.VariableDeclarationTree;
import org.sonarsource.slang.cfg.CfgBlock;
import org.sonarsource.slang.cfg.CfgPrinter;
import org.sonarsource.slang.cfg.ControlFlowGraph;
import org.sonarsource.slang.checks.api.CheckContext;
import org.sonarsource.slang.checks.api.InitContext;
import org.sonarsource.slang.checks.api.SlangCheck;

@Rule(key = "S12345678")
public class NullDereferenceBeliefStyleCheck implements SlangCheck {

  @Override
  public void initialize(InitContext init) {
    init.register(FunctionDeclarationTree.class, (ctx, functionDeclarationTree) -> {
      if(functionDeclarationTree.body() != null){
        ControlFlowGraph cfg = ControlFlowGraph.build(functionDeclarationTree);
        NullTracking nullTracking = NullTracking.analyse(cfg);

        for (CfgBlock block : cfg.blocks()) {
          if(block.isReliable()) {
            block.elements().forEach(element -> checkElement(element, nullTracking.getOut(block), ctx));
          }
        }
      }
    });
  }

  private void checkElement(Tree element, Set<String> out, CheckContext ctx) {
    if(element instanceof BinaryExpressionTree){
      BinaryExpressionTree binOp = (BinaryExpressionTree) element;
      if(binOp.operator().equals(BinaryExpressionTree.Operator.EQUAL_TO)){
        processEqualTo(binOp, out, ctx);
      }
    }
  }

  private void processEqualTo(BinaryExpressionTree element, Set<String> out, CheckContext ctx) {
    if(element.rightOperand() instanceof LiteralTree) {
      IdentifierTree lhs = NullTracking.getIdentifierIfPresent(element.leftOperand());
      if(lhs == null){
        return;
      }

      LiteralTree rhs = (LiteralTree) element.rightOperand();

      String pointerChecked;

      //TODO: Support null == p correctly
      if(rhs.value().equals("null")){
        pointerChecked = lhs.name();
      } else if(lhs.name().equals("null")){
        pointerChecked = rhs.value();
      } else {
        return;
      }

      if(out.contains(pointerChecked)) {
        ctx.reportIssue(element, "This check is either always false, or a null pointer has been raised before.");
      }
    }
  }

  private static class NullTracking {
    private final ControlFlowGraph cfg;
    private final Map<CfgBlock, Set<String>> out = new HashMap<>();

    private NullTracking(ControlFlowGraph cfg) {
      this.cfg = cfg;
    }

    private Set<String> getOut(CfgBlock block) {
      return out.get(block);
    }

    private static NullTracking analyse(ControlFlowGraph cfg) {
      NullTracking nullTracking = new NullTracking(cfg);
      //Generate kill/gen for each block in isolation
      Map<CfgBlock, Set<String>> kill = new HashMap<>();
      Map<CfgBlock, Set<String>> gen = new HashMap<>();

      for (CfgBlock block: cfg.blocks()) {
        Set<String> blockKill = new HashSet<>();
        Set<String> blockGen = new HashSet<>();

        nullTracking.processBlockElements(block, blockKill, blockGen);

        kill.put(block, blockKill);
        gen.put(block, blockGen);
      }
      nullTracking.analyzeCFG(kill, gen);

      // Make things immutable.
      for (Map.Entry<CfgBlock, Set<String>> blockSetEntry : nullTracking.out.entrySet()) {
        blockSetEntry.setValue(ImmutableSet.copyOf(blockSetEntry.getValue()));
      }

      return nullTracking;
    }

    //Forward analysis
    private void analyzeCFG(Map<CfgBlock, Set<String>> kill, Map<CfgBlock, Set<String>> gen) {
      Deque<CfgBlock> workList = new LinkedList<>();
      workList.addAll(cfg.blocks());
      while (!workList.isEmpty()) {
        CfgBlock  block = workList.removeFirst();

        Set<String> blockIn;

        //Collect all predecessors out set
        List<Set<String>> preds = block.predecessors().stream().map(out::get).filter(Objects::nonNull).collect(Collectors.toList());

        if(!preds.isEmpty()){
          Set<String> newBlockIn = new HashSet<>(preds.get(0));
          for (int i = 1; i < preds.size(); i++){
            newBlockIn = Sets.intersection(newBlockIn, preds.get(i));
          }
          blockIn = new HashSet<>(newBlockIn);
        } else {
          blockIn = new HashSet<>();
        }

        // out = gen and (in - kill)
        Set<String> newOut = new HashSet<>(gen.get(block));
        newOut.addAll(Sets.difference(blockIn, kill.get(block)));

        if (newOut.equals(out.get(block))) {
          continue;
        }
        out.put(block, newOut);
        block.successors().forEach(workList::addLast);
      }
    }

    private void processBlockElements(CfgBlock block, Set<String> blockKill, Set<String> blockGen) {
      // process elements from bottom to top: order matter because we are going to remove pointer
      // from gen block (in assign and declaration)
      Set<String> shortCircuited = new HashSet<>();
      for (Tree element : block.elements()) {
        if(element instanceof AssignmentExpressionTree){
          processAssignment((AssignmentExpressionTree) element, blockKill, blockGen);
        } else if(element instanceof FunctionInvocationTree){
          processMethodInvocation((FunctionInvocationTree) element, blockGen);
        } else if(element instanceof VariableDeclarationTree){
          processVariable(((VariableDeclarationTree) element).identifier(), blockKill, blockGen);
        } else if(element instanceof BinaryExpressionTree) {
          String s = processBinaryExpression((BinaryExpressionTree)element, blockGen);
          if(s != null){
            shortCircuited.add(s);
          }
        }
      }
      blockGen.removeAll(shortCircuited);
    }

    private String processBinaryExpression(BinaryExpressionTree element, Set<String> blockGen) {
      //Search for pointer check for null followed by their use to remove them from gen set
      if(element.operator().equals(BinaryExpressionTree.Operator.CONDITIONAL_OR) && element.leftOperand() instanceof BinaryExpressionTree) {
        BinaryExpressionTree leftBinOp = (BinaryExpressionTree) element.leftOperand();
        if(leftBinOp.operator().equals(BinaryExpressionTree.Operator.EQUAL_TO) && leftBinOp.leftOperand() instanceof IdentifierTree && leftBinOp.rightOperand() instanceof LiteralTree) {
          IdentifierTree lhs = (IdentifierTree) leftBinOp.leftOperand();
          LiteralTree rhs = (LiteralTree) leftBinOp.rightOperand();
          if(rhs.value().equals("null")){
            //We found a pointer checked for null
            return lhs.name();
            //TODO: Remove only the one gen in the binop, not the one after the binop
//            p == null || p.toString(); // Compliant
//            p.toString();
//            if(p == null) {} // Noncompliant, FN without this improvement
          }
        }
      }
      return null;
    }

    private void processVariable(IdentifierTree element, Set<String> blockKill, Set<String> blockGen) {
      blockKill.add(element.name());
      blockGen.remove(element.identifier());
    }

    private void processMethodInvocation(FunctionInvocationTree element, Set<String> blockGen) {
      //TODO: Check if it is a identifier here
      processPointerUse(element.methodSelect(), blockGen);
    }

    private void processPointerUse(Tree element, Set<String> blockGen) {
      IdentifierTree id = getIdentifierIfPresent(element);
      if(id != null) {
        String name = id.name();
        //TODO: Eventuelly check for field here
        blockGen.add(name);
      }
    }

    private void processAssignment(AssignmentExpressionTree element, Set<String> blockKill, Set<String> blockGen) {
      IdentifierTree lhs = getIdentifierIfPresent(element.leftHandSide());
      if (lhs != null) {
        String name = lhs.name();
        //TODO: Eventuelly check for field here
        //if we see an assignment, we remove all previously used  pointer (we don't know anything for them anymore)
        blockKill.add(name);
        blockGen.remove(name);
      }
    }

    private static IdentifierTree getIdentifierIfPresent(Tree tree){
      if(tree instanceof IdentifierTree) {
        return (IdentifierTree) tree;
      } else if(tree instanceof NativeTree) {
        //If a native surround the identifier.
        List<Tree> children = tree.children();
        if(children.size() == 1 && children.get(0) instanceof IdentifierTree){
          return (IdentifierTree) children.get(0);
        }
      }
      return null;
    }
  }
}
