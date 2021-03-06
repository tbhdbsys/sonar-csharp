/*
 * SonarQube .NET Tests Library
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
package org.sonar.plugins.dotnet.tests;

import java.io.File;
import java.io.IOException;
import javax.annotation.CheckForNull;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class NUnitTestResultsFileParser implements UnitTestResultsParser {

  private static final Logger LOG = Loggers.get(NUnitTestResultsFileParser.class);

  @Override
  public void parse(File file, UnitTestResults unitTestResults) {
    LOG.info("Parsing the NUnit Test Results file " + file.getAbsolutePath());
    new Parser(file, unitTestResults).parse();
  }

  private static class Parser {

    private final File file;
    private final UnitTestResults unitTestResults;

    public Parser(File file, UnitTestResults unitTestResults) {
      this.file = file;
      this.unitTestResults = unitTestResults;
    }

    public void parse() {
      try (XmlParserHelper xmlParserHelper = new XmlParserHelper(file)) {
        checkRootTag(xmlParserHelper);
        handleTestResultsTag(xmlParserHelper);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to close report", e);
      }
    }

    private static void checkRootTag(XmlParserHelper xmlParserHelper) {
      xmlParserHelper.checkRootTag("test-results");
    }

    private void handleTestResultsTag(XmlParserHelper xmlParserHelper) {
      int total = xmlParserHelper.getRequiredIntAttribute("total");
      int errors = xmlParserHelper.getRequiredIntAttribute("errors");
      int failures = xmlParserHelper.getRequiredIntAttribute("failures");
      int inconclusive = xmlParserHelper.getRequiredIntAttribute("inconclusive");
      int ignored = xmlParserHelper.getRequiredIntAttribute("ignored");

      int tests = total - inconclusive;
      int passed = total - errors - failures - inconclusive;
      int skipped = inconclusive + ignored;

      Double executionTime = readExecutionTimeFromDirectlyNestedTestSuiteTags(xmlParserHelper);

      unitTestResults.add(tests, passed, skipped, failures, errors, executionTime != null ? (long) executionTime.doubleValue() : null);
    }

    @CheckForNull
    private static Double readExecutionTimeFromDirectlyNestedTestSuiteTags(XmlParserHelper xmlParserHelper) {
      Double executionTime = null;

      String tag;
      int level = 0;
      while ((tag = xmlParserHelper.nextStartOrEndTag()) != null) {
        if ("<test-suite>".equals(tag)) {
          level++;
          Double time = xmlParserHelper.getDoubleAttribute("time");

          if (level == 1 && time != null) {
            if (executionTime == null) {
              executionTime = 0d;
            }
            executionTime += time * 1000;
          }
        } else if ("</test-suite>".equals(tag)) {
          level--;
        }
      }

      return executionTime;
    }
  }

}
