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
package org.sonarsource.slang.impl;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.slang.api.FunctionInvocationTree;
import org.sonarsource.slang.api.IdentifierTree;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.api.TreeMetaData;

public class FunctionInvocationTreeImpl extends BaseTreeImpl implements FunctionInvocationTree {
  private final Tree methodSelect;
  private final List<Tree> arguments;
  private final IdentifierTree methodName;

  public FunctionInvocationTreeImpl(
      TreeMetaData metaData,
      @Nullable Tree methodSelect,
      List<Tree> arguments,
      IdentifierTree methodName) {
    super(metaData);
    this.methodSelect = methodSelect;
    this.arguments = arguments;
    this.methodName = methodName;
  }

  @CheckForNull
  @Override
  public Tree methodSelect() {
    return methodSelect;
  }

  @Override
  public List<Tree> arguments() {
    return arguments;
  }

  @Override
  public IdentifierTree methodName() {
    return methodName;
  }

  @Override
  public List<Tree> children() {
    List<Tree> children = new ArrayList<>();
    if(methodSelect != null){
      children.add(methodSelect);
    }
    children.add(methodName);
    children.addAll(arguments);
    return children;
  }
}
