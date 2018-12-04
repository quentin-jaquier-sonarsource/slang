import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.slang.api.ASTConverter;
import org.sonarsource.slang.api.FunctionDeclarationTree;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.cfg.CfgPrinter;
import org.sonarsource.slang.cfg.ControlFlowGraph;
import org.sonarsource.slang.visitors.TreePrinter;

public class LanguageTester {

  private ASTConverter converter;


  public LanguageTester(ASTConverter converter) {
    this.converter = converter;
  }

  public String getDotCfg(String content) {
    return CfgPrinter.toDot(getCfg(content));
  }

  public ControlFlowGraph getCfg(String content) {
    Tree t = converter.parse(content);

    List<Tree> funs = t.descendants().filter(d -> d instanceof FunctionDeclarationTree).collect(Collectors.toList());


    if(funs.isEmpty()){
      throw new IllegalArgumentException("Can only build a cfg from a function");
    } else {
      return ControlFlowGraph.build((FunctionDeclarationTree) funs.get(0));
    }
  }

  private ControlFlowGraph buildCfg(Tree t) {
    return ControlFlowGraph.build((FunctionDeclarationTree) t);
  }

  public String getTable(String content) {
    return TreePrinter.table(converter.parse(content));
  }
}
