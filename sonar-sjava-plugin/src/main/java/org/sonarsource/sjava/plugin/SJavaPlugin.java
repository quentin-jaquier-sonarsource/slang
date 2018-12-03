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

import org.sonar.api.Plugin;
import org.sonar.api.SonarProduct;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;

public class SJavaPlugin implements Plugin {

  public static final String SJAVA_LANGUAGE_KEY = "sjava";
  static final String SJAVA_LANGUAGE_NAME = "SJava";

  static final String SJAVA_FILE_SUFFIXES_DEFAULT_VALUE = ".java";
  static final String SJAVA_FILE_SUFFIXES_KEY = "sonar.sjava.file.suffixes";

  static final String SJAVA_REPOSITORY_KEY = "sjava";
  static final String REPOSITORY_NAME = "SonarAnalyzer";
  static final String PROFILE_NAME = "Sonar way";

  private static final String GENERAL = "General";
  private static final String SJAVA_CATEGORY = "SJava";

  @Override
  public void define(Plugin.Context context) {

    context.addExtensions(
        SJavaLanguage.class,
        SJavaSensor.class,
        SJavaRulesDefinition.class,
        SJavaProfileDefinition.class,
        PropertyDefinition.builder(SJAVA_FILE_SUFFIXES_KEY)
            .defaultValue(SJAVA_FILE_SUFFIXES_DEFAULT_VALUE)
            .name("File Suffixes")
            .description("List of suffixes for files to analyze.")
            .subCategory(GENERAL)
            .category(SJAVA_CATEGORY)
            .multiValues(true)
            .onQualifiers(Qualifiers.PROJECT)
            .build());
  }
}
