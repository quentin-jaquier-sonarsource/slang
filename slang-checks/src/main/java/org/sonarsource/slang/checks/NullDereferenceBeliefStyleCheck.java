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
import javafx.scene.control.cell.CheckBoxListCell;
import org.sonarsource.slang.api.AssignmentExpressionTree;
import org.sonarsource.slang.api.BinaryExpressionTree;
import org.sonarsource.slang.api.FunctionDeclarationTree;
import org.sonarsource.slang.api.IdentifierTree;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.api.VariableDeclarationTree;
import org.sonarsource.slang.cfg.CfgBlock;
import org.sonarsource.slang.cfg.ControlFlowGraph;
import org.sonarsource.slang.cfg.SlangCfgBlock;
import org.sonarsource.slang.checks.api.CheckContext;
import org.sonarsource.slang.checks.api.InitContext;
import org.sonarsource.slang.checks.api.SlangCheck;

public class NullDereferenceBeliefStyleCheck implements SlangCheck {

  @Override
  public void initialize(InitContext init) {
    init.register(FunctionDeclarationTree.class, (ctx, functionDeclarationTree) -> {
      if(functionDeclarationTree.body() != null){
        ControlFlowGraph cfg = ControlFlowGraph.build(functionDeclarationTree);
        NullTracking nullTracking = NullTracking.analyse(cfg);

        for (CfgBlock block : cfg.blocks()) {
          block.elements().forEach(element -> checkElement(element, nullTracking.getOut(block), ctx));
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
    if(element.rightOperand() instanceof IdentifierTree && element.leftOperand() instanceof IdentifierTree) {

      IdentifierTree lhs = (IdentifierTree) element.leftOperand();
      IdentifierTree rhs = (IdentifierTree) element.rightOperand();

      String pointerChecked;

      if(rhs.name().equals("null")){
        pointerChecked = lhs.name();
      } else if(lhs.name().equals("null")){
        pointerChecked = rhs.name();
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
       // block.exceptions().stream().map(out::get).filter(Objects::nonNull).forEach(preds::add);

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
      // process elements from bottom to top
      for (Tree element : block.elements()) {
        if(element instanceof AssignmentExpressionTree){
          processAssignment((AssignmentExpressionTree) element, blockKill, blockGen);
        }
        //else if(element instanceof MethodInvocationTree) TODO
        else if(element instanceof VariableDeclarationTree){
          processVariable(((VariableDeclarationTree) element).identifier(), blockKill, blockGen);
        }
      }
    }

    private void processVariable(IdentifierTree element, Set<String> blockKill, Set<String> blockGen) {
      blockKill.add(element.name());
      blockGen.remove(element.identifier());
    }

    /* TODO
    private void processMethodInvocation(MethodInvocationTree element, Set<String> blockGen) {
      if(element.methodSelect().is(Tree.Kind.MEMBER_SELECT)) {
        MemberSelectExpressionTree methodSelect = (MemberSelectExpressionTree)element.methodSelect();
        processPointerUse(methodSelect.expression(), blockGen);
      }
    }*/

    private void processPointerUse(Tree element, Set<String> blockGen) {
      if(element instanceof IdentifierTree) {
        String name = ((IdentifierTree)element).name();
        //TODO: Eventuelly check for field here
        blockGen.add(name);
      }
    }

    private void processAssignment(AssignmentExpressionTree element, Set<String> blockKill, Set<String> blockGen) {
      Tree lhs = element.leftHandSide();
      if (lhs instanceof IdentifierTree) {
        String name = ((IdentifierTree) lhs).name();
        //TODO: Eventuelly check for field here
        //if we see an assignment, we remove all previously used  pointer (we don't know anything for them anymore)
        blockKill.add(name);
        blockGen.remove(name);
      }
    }

  }
}
