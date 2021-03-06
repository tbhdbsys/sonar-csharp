/*
 * SonarC#
 * Copyright (C) 2014-2017 SonarSource SA
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
package org.sonar.plugins.csharp;

import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.Settings;
import org.sonar.api.profiles.ProfileExporter;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.ActiveRuleParam;
import org.sonar.api.rules.RuleParam;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class RoslynProfileExporter extends ProfileExporter {
  private static final Logger LOG = Loggers.get(RoslynProfileExporter.class);
  static final String SONARANALYZER_CSHARP_NAME = "SonarAnalyzer.CSharp";
  private static final String SONARANALYZER_PARTIAL_REPO_KEY = "sonaranalyzer-cs";

  private static final String ROSLYN_REPOSITORY_PREFIX = "roslyn.";

  private final Settings settings;
  private final RulesDefinition[] rulesDefinitions;

  public RoslynProfileExporter(Settings settings, RulesDefinition[] rulesDefinitions) {
    super("roslyn-cs", "Technical exporter for the MSBuild SonarQube Scanner");
    this.settings = settings;
    this.rulesDefinitions = rulesDefinitions;
    setSupportedLanguages(CSharpPlugin.LANGUAGE_KEY);
  }

  public static List<PropertyDefinition> sonarLintRepositoryProperties() {
    String analyzerVersion = getAnalyzerVersion();
    return Arrays.asList(
      PropertyDefinition.builder(pluginKeyPropertyKey(SONARANALYZER_PARTIAL_REPO_KEY))
        .defaultValue("csharp")
        .hidden()
        .build(),
      PropertyDefinition.builder(pluginVersionPropertyKey(SONARANALYZER_PARTIAL_REPO_KEY))
        .defaultValue(analyzerVersion)
        .hidden()
        .build(),
      PropertyDefinition.builder(staticResourceNamePropertyKey(SONARANALYZER_PARTIAL_REPO_KEY))
        .defaultValue("SonarAnalyzer-" + analyzerVersion + ".zip")
        .hidden()
        .build(),
      PropertyDefinition.builder(analyzerIdPropertyKey(SONARANALYZER_PARTIAL_REPO_KEY))
        .defaultValue(SONARANALYZER_CSHARP_NAME)
        .hidden()
        .build(),
      PropertyDefinition.builder(ruleNamespacePropertyKey(SONARANALYZER_PARTIAL_REPO_KEY))
        .defaultValue(SONARANALYZER_CSHARP_NAME)
        .hidden()
        .build(),
      PropertyDefinition.builder(nugetPackageIdPropertyKey(SONARANALYZER_PARTIAL_REPO_KEY))
        .defaultValue(SONARANALYZER_CSHARP_NAME)
        .hidden()
        .build(),
      PropertyDefinition.builder(nugetPackageVersionPropertyKey(SONARANALYZER_PARTIAL_REPO_KEY))
        .defaultValue(analyzerVersion)
        .hidden()
        .build());
  }

  private static String getAnalyzerVersion() {
    try {
      return new BufferedReader(new InputStreamReader(RoslynProfileExporter.class.getResourceAsStream("/static/version.txt"), StandardCharsets.UTF_8)).readLine();
    } catch (IOException e) {
      throw new IllegalStateException("Couldn't read C# analyzer version number from '/static/version.txt'", e);
    }
  }

  @Override
  public void exportProfile(RulesProfile rulesProfile, Writer writer) {
    try {
      ImmutableMultimap<String, RuleKey> activeRoslynRulesByPartialRepoKey = activeRoslynRulesByPartialRepoKey(rulesProfile.getActiveRules()
        .stream()
        .map(r -> RuleKey.of(r.getRepositoryKey(), r.getRuleKey()))
        .collect(toList()));

      appendLine(writer, "<?xml version=\"1.0\" encoding=\"utf-8\"?>");
      appendLine(writer, "<RoslynExportProfile Version=\"1.0\">");

      appendLine(writer, "  <Configuration>");
      appendLine(writer, "    <RuleSet Name=\"Rules for SonarQube\" Description=\"This rule set was automatically generated from SonarQube.\" ToolsVersion=\"14.0\">");
      for (String partialRepoKey : activeRoslynRulesByPartialRepoKey.keySet()) {
        writeRepoRuleSet(partialRepoKey, activeRoslynRulesByPartialRepoKey.get(partialRepoKey), writer);
      }
      appendLine(writer, "    </RuleSet>");

      appendLine(writer, "    <AdditionalFiles>");

      String sonarlintParameters = analysisSettings(false, false, true, rulesProfile);
      java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
      String base64 = new String(encoder.encode(sonarlintParameters.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
      appendLine(writer, "      <AdditionalFile FileName=\"SonarLint.xml\">" + base64 + "</AdditionalFile>");
      appendLine(writer, "    </AdditionalFiles>");

      appendLine(writer, "  </Configuration>");

      appendLine(writer, "  <Deployment>");

      appendLine(writer, "    <Plugins>");
      for (String partialRepoKey : activeRoslynRulesByPartialRepoKey.keySet()) {
        String pluginKey = mandatoryPropertyValue(pluginKeyPropertyKey(partialRepoKey));
        String pluginVersion = mandatoryPropertyValue(pluginVersionPropertyKey(partialRepoKey));
        String staticResourceName = mandatoryPropertyValue(staticResourceNamePropertyKey(partialRepoKey));

        appendLine(writer,
          "      <Plugin Key=\"" + escapeXml(pluginKey) + "\" Version=\"" + escapeXml(pluginVersion) + "\" StaticResourceName=\"" + escapeXml(staticResourceName) + "\" />");
      }
      appendLine(writer, "    </Plugins>");

      appendLine(writer, "    <NuGetPackages>");
      for (String partialRepoKey : activeRoslynRulesByPartialRepoKey.keySet()) {
        String packageId = mandatoryPropertyValue(nugetPackageIdPropertyKey(partialRepoKey));
        String packageVersion = mandatoryPropertyValue(nugetPackageVersionPropertyKey(partialRepoKey));

        appendLine(writer, "      <NuGetPackage Id=\"" + escapeXml(packageId) + "\" Version=\"" + escapeXml(packageVersion) + "\" />");
      }
      appendLine(writer, "    </NuGetPackages>");

      appendLine(writer, "  </Deployment>");

      appendLine(writer, "</RoslynExportProfile>");
    } catch (Exception e) {
      LOG.error("Error exporting profile '{}' for language '{}'", rulesProfile.getName(), rulesProfile.getLanguage(), e);
      throw e;
    }
  }

  private void writeRepoRuleSet(String partialRepoKey, ImmutableCollection<RuleKey> ruleKeys, Writer writer) {
    String analyzerId = mandatoryPropertyValue(analyzerIdPropertyKey(partialRepoKey));
    String ruleNamespace = mandatoryPropertyValue(ruleNamespacePropertyKey(partialRepoKey));

    appendLine(writer, "      <Rules AnalyzerId=\"" + escapeXml(analyzerId) + "\" RuleNamespace=\"" + escapeXml(ruleNamespace) + "\">");

    Set<String> activeRules = Sets.newHashSet();
    String repositoryKey = null;
    for (RuleKey activeRuleKey : ruleKeys) {
      if (repositoryKey == null) {
        repositoryKey = activeRuleKey.repository();
      }

      String ruleKey = activeRuleKey.rule();
      activeRules.add(ruleKey);
      appendLine(writer, "        <Rule Id=\"" + escapeXml(ruleKey) + "\" Action=\"Warning\" />");
    }

    List<String> allRuleKeys = allRuleKeysByRepositoryKey(repositoryKey);
    for (String ruleKey : allRuleKeys) {
      if (!activeRules.contains(ruleKey)) {
        appendLine(writer, "        <Rule Id=\"" + escapeXml(ruleKey) + "\" Action=\"None\" />");
      }
    }

    appendLine(writer, "      </Rules>");
  }

  private static String analysisSettings(boolean includeSettings, boolean ignoreHeaderComments, boolean includeRules, RulesProfile ruleProfile) {
    StringBuilder sb = new StringBuilder();

    appendLine(sb, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    appendLine(sb, "<AnalysisInput>");

    if (includeSettings) {
      appendLine(sb, "  <Settings>");
      appendLine(sb, "    <Setting>");
      appendLine(sb, "      <Key>" + CSharpPlugin.IGNORE_HEADER_COMMENTS + "</Key>");
      appendLine(sb, "      <Value>" + (ignoreHeaderComments ? "true" : "false") + "</Value>");
      appendLine(sb, "    </Setting>");
      appendLine(sb, "  </Settings>");
    }

    appendLine(sb, "  <Rules>");
    if (includeRules) {
      for (ActiveRule activeRule : ruleProfile.getActiveRulesByRepository(CSharpSonarRulesDefinition.REPOSITORY_KEY)) {
        appendLine(sb, "    <Rule>");
        appendLine(sb, "      <Key>" + escapeXml(activeRule.getRuleKey()) + "</Key>");
        appendParameters(sb, effectiveParameters(activeRule));
        appendLine(sb, "    </Rule>");
      }
    }
    appendLine(sb, "  </Rules>");

    appendLine(sb, "  <Files>");
    appendLine(sb, "  </Files>");

    appendLine(sb, "</AnalysisInput>");

    return sb.toString();
  }

  private static void appendParameters(StringBuilder sb, Map<String, String> parameters) {
    if (!parameters.isEmpty()) {
      appendLine(sb, "      <Parameters>");
      for (Map.Entry<String, String> parameter : parameters.entrySet()) {
        appendLine(sb, "        <Parameter>");
        appendLine(sb, "          <Key>" + escapeXml(parameter.getKey()) + "</Key>");
        appendLine(sb, "          <Value>" + escapeXml(parameter.getValue()) + "</Value>");
        appendLine(sb, "        </Parameter>");
      }
      appendLine(sb, "      </Parameters>");
    }
  }

  private static Map<String, String> effectiveParameters(ActiveRule activeRule) {
    Map<String, String> builder = Maps.newHashMap();

    if (activeRule.getRule().getTemplate() != null) {
      builder.put("RuleKey", activeRule.getRuleKey());
    }

    for (ActiveRuleParam param : activeRule.getActiveRuleParams()) {
      checkValueNotNull(param.getValue(), activeRule.getRuleKey(), param.getKey());
      builder.put(param.getKey(), param.getValue());
    }

    for (RuleParam param : activeRule.getRule().getParams()) {
      if (!builder.containsKey(param.getKey())) {
        checkValueNotNull(param.getDefaultValue(), activeRule.getRuleKey(), param.getKey());
        builder.put(param.getKey(), param.getDefaultValue());
      }
    }

    return ImmutableMap.copyOf(builder);
  }

  private static void checkValueNotNull(@Nullable String value, String ruleKey, String paramKey) {
    if (value == null) {
      throw new IllegalStateException(String.format("Rule '%s' has parameter '%s' with a null value", ruleKey, paramKey));
    }
  }

  public static ImmutableMultimap<String, RuleKey> activeRoslynRulesByPartialRepoKey(Iterable<RuleKey> activeRules) {
    ImmutableMultimap.Builder<String, RuleKey> builder = ImmutableMultimap.builder();

    for (RuleKey activeRule : activeRules) {
      if (activeRule.repository().startsWith(ROSLYN_REPOSITORY_PREFIX)) {
        String pluginKey = activeRule.repository().substring(ROSLYN_REPOSITORY_PREFIX.length());
        builder.put(pluginKey, activeRule);
      } else if (CSharpSonarRulesDefinition.REPOSITORY_KEY.equals(activeRule.repository())) {
        builder.put(SONARANALYZER_PARTIAL_REPO_KEY, activeRule);
      }
    }

    return builder.build();
  }

  private List<String> allRuleKeysByRepositoryKey(String repositoryKey) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (RulesDefinition rulesDefinition : rulesDefinitions) {
      Context context = new Context();
      rulesDefinition.define(context);
      Repository repo = context.repository(repositoryKey);
      if (repo != null) {
        for (Rule rule : repo.rules()) {
          builder.add(rule.key());
        }
      }
    }
    return builder.build();
  }

  private String mandatoryPropertyValue(String propertyKey) {
    return Preconditions.checkNotNull(settings.getDefaultValue(propertyKey), "The mandatory property \"" + propertyKey + "\" must be set by the Roslyn plugin.");
  }

  private static String pluginKeyPropertyKey(String partialRepoKey) {
    return partialRepoKey + ".pluginKey";
  }

  private static String pluginVersionPropertyKey(String partialRepoKey) {
    return partialRepoKey + ".pluginVersion";
  }

  private static String staticResourceNamePropertyKey(String partialRepoKey) {
    return partialRepoKey + ".staticResourceName";
  }

  private static String analyzerIdPropertyKey(String partialRepoKey) {
    return partialRepoKey + ".analyzerId";
  }

  private static String ruleNamespacePropertyKey(String partialRepoKey) {
    return partialRepoKey + ".ruleNamespace";
  }

  private static String nugetPackageIdPropertyKey(String partialRepoKey) {
    return partialRepoKey + ".nuget.packageId";
  }

  private static String nugetPackageVersionPropertyKey(String partialRepoKey) {
    return partialRepoKey + ".nuget.packageVersion";
  }

  private static String escapeXml(String str) {
    return str.replace("&", "&amp;").replace("\"", "&quot;").replace("'", "&apos;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static void appendLine(Writer writer, String line) {
    try {
      writer.write(line);
      writer.write("\r\n");
    } catch (IOException e) {
      Throwables.propagate(e);
    }
  }

  private static void appendLine(StringBuilder sb, String line) {
    sb.append(line);
    sb.append("\r\n");
  }
}
