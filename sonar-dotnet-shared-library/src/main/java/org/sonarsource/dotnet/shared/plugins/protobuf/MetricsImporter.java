/*
 * SonarSource :: .NET :: Shared library
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
package org.sonarsource.dotnet.shared.plugins.protobuf;

import java.io.Serializable;
import java.util.HashSet;
import java.util.function.Predicate;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonarsource.dotnet.protobuf.SonarAnalyzer;
import org.sonarsource.dotnet.protobuf.SonarAnalyzer.MetricsInfo;

class MetricsImporter extends ProtobufImporter<SonarAnalyzer.MetricsInfo> {

  private final SensorContext context;
  private final FileLinesContextFactory fileLinesContextFactory;
  private final NoSonarFilter noSonarFilter;

  MetricsImporter(SensorContext context, FileLinesContextFactory fileLinesContextFactory, NoSonarFilter noSonarFilter, Predicate<InputFile> inputFileFilter) {
    super(SonarAnalyzer.MetricsInfo.parser(), context, inputFileFilter, SonarAnalyzer.MetricsInfo::getFilePath);
    this.context = context;
    this.fileLinesContextFactory = fileLinesContextFactory;
    this.noSonarFilter = noSonarFilter;
  }

  @Override
  void consumeFor(InputFile inputFile, MetricsInfo message) {
    saveMetric(context, inputFile, CoreMetrics.CLASSES, message.getClassCount());
    saveMetric(context, inputFile, CoreMetrics.STATEMENTS, message.getStatementCount());
    saveMetric(context, inputFile, CoreMetrics.FUNCTIONS, message.getFunctionCount());
    saveMetric(context, inputFile, CoreMetrics.PUBLIC_API, message.getPublicApiCount());
    saveMetric(context, inputFile, CoreMetrics.PUBLIC_UNDOCUMENTED_API, message.getPublicUndocumentedApiCount());
    saveMetric(context, inputFile, CoreMetrics.COMPLEXITY, message.getComplexity());
    saveMetric(context, inputFile, CoreMetrics.FILE_COMPLEXITY_DISTRIBUTION, message.getFileComplexityDistribution());
    saveMetric(context, inputFile, CoreMetrics.FUNCTION_COMPLEXITY_DISTRIBUTION, message.getFunctionComplexityDistribution());
    saveMetric(context, inputFile, CoreMetrics.COMPLEXITY_IN_CLASSES, message.getComplexityInClasses());
    saveMetric(context, inputFile, CoreMetrics.COMPLEXITY_IN_FUNCTIONS, message.getComplexityInFunctions());

    noSonarFilter.noSonarInFile(inputFile, new HashSet<>(message.getNoSonarCommentList()));

    FileLinesContext fileLinesContext = fileLinesContextFactory.createFor(inputFile);

    for (int line : message.getNonBlankCommentList()) {
      fileLinesContext.setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, line, 1);
    }
    saveMetric(context, inputFile, CoreMetrics.COMMENT_LINES, message.getNonBlankCommentCount());

    for (int line : message.getCodeLineList()) {
      fileLinesContext.setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1);
    }
    saveMetric(context, inputFile, CoreMetrics.NCLOC, message.getCodeLineCount());

    fileLinesContext.save();
  }

  private static <T extends Serializable> void saveMetric(SensorContext context, InputFile inputFile, Metric<T> metric, T value) {
    context.<T>newMeasure()
      .on(inputFile)
      .forMetric(metric)
      .withValue(value)
      .save();
  }

}
