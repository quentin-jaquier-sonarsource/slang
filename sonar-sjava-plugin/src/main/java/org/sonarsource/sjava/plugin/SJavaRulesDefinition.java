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
package org.sonarsource.sjava.plugin;

import java.util.ArrayList;
import java.util.List;
import org.sonar.api.SonarRuntime;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;
import org.sonarsource.slang.checks.utils.Language;
import org.sonarsource.slang.plugin.RulesDefinitionUtils;

public class SJavaRulesDefinition implements RulesDefinition {

  private static final String RESOURCE_FOLDER = "org/sonar/l10n/sjava/rules/sjava";

  private final SonarRuntime sonarRuntime;

  public SJavaRulesDefinition(SonarRuntime sonarRuntime) {
    this.sonarRuntime = sonarRuntime;
  }

  @Override
  public void define(RulesDefinition.Context context) {
    NewRepository repository = context
        .createRepository(SJavaPlugin.SJAVA_REPOSITORY_KEY, SJavaPlugin.SJAVA_LANGUAGE_KEY)
        .setName(SJavaPlugin.REPOSITORY_NAME);
    RuleMetadataLoader ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, SJavaProfileDefinition.PATH_TO_JSON, sonarRuntime);

    List<Class> checks = new ArrayList<>(SJavaCheckList.checks());

    ruleMetadataLoader.addRulesByAnnotatedClass(repository, checks);

    RulesDefinitionUtils.setDefaultValuesForParameters(repository, checks, Language.SJAVA);

    repository.done();
  }
}
