/*
 * SonarSource SLang
 * Copyright (C) 2018-2019 SonarSource SA
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
package org.sonarsource.scala.checks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonarsource.slang.api.IdentifierTree;
import org.sonarsource.slang.checks.UnusedPrivateMethodCheck;

@Rule(key = "S1144")
public class UnusedPrivateMethodScalaCheck extends UnusedPrivateMethodCheck {

  // Serializable method should not raise any issue in Scala.
  private static final Set<String> IGNORED_METHODS = new HashSet<>(Arrays.asList(
    "writeObject",
    "readObject",
    "writeReplace",
    "readResolve",
    "readObjectNoData"));

  @Override
  protected boolean isUnusedMethod(@Nullable IdentifierTree identifier, Set<String> usedIdentifierNames) {
    return identifier != null && super.isUnusedMethod(identifier, usedIdentifierNames) && !IGNORED_METHODS.contains(identifier.name());
  }

}