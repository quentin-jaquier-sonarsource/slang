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

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonarsource.analyzer.commons.BuiltInQualityProfileJsonLoader;

public class SJavaProfileDefinition implements BuiltInQualityProfilesDefinition {

  static final String PATH_TO_JSON = "org/sonar/l10n/sjava/rules/sjava/Sonar_way_profile.json";
  @Override
  public void define(BuiltInQualityProfilesDefinition.Context context) {
    NewBuiltInQualityProfile profile = context.createBuiltInQualityProfile(SJavaPlugin.PROFILE_NAME, SJavaPlugin.SJAVA_LANGUAGE_KEY);
    BuiltInQualityProfileJsonLoader.load(profile, SJavaPlugin.SJAVA_REPOSITORY_KEY, PATH_TO_JSON);
    profile.done();
  }

}
