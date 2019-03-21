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
