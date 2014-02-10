/*
 * Sonar .NET Plugin :: Tests
 * Copyright (C) 2010 Jose Chillan, Alexandre Victoor and SonarSource
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.csharp.tests;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.sonar.api.config.PropertyDefinition;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class TestsPluginTest {

  @Test
  public void number_of_extensions() {
    assertThat(new TestsPlugin().getExtensions()).hasSize(2 + TestsPlugin.getPropertyDefinitions().size());
  }

  @Test
  public void number_of_properties() {
    assertThat(keys(TestsPlugin.getPropertyDefinitions())).containsExactly(
      "sonar.dotnet.tests.ncover3.reportPath",
      "sonar.dotnet.tests.opencover.reportPath");
  }

  private static List<String> keys(List<PropertyDefinition> properties) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (PropertyDefinition property : properties) {
      builder.add(property.key());
    }
    return builder.build();
  }

}
