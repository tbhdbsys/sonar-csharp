﻿/*
 * SonarAnalyzer for .NET
 * Copyright (C) 2015-2017 SonarSource SA
 * mailto: contact AT sonarsource DOT com
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

using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Microsoft.CodeAnalysis.Diagnostics;
using Microsoft.CodeAnalysis.Text;
using SonarAnalyzer.Common;
using SonarAnalyzer.Helpers;

namespace SonarAnalyzer.Rules.CSharp
{
    [DiagnosticAnalyzer(LanguageNames.CSharp)]
    [Rule(DiagnosticId)]
    public class RedundantParentheses : SonarDiagnosticAnalyzer
    {
        internal const string DiagnosticId = "S3235";
        private const string MessageFormat = "Remove these redundant parentheses.";
        private const IdeVisibility ideVisibility = IdeVisibility.Hidden;

        private static readonly DiagnosticDescriptor rule =
            DiagnosticDescriptorBuilder.GetDescriptor(DiagnosticId, MessageFormat, ideVisibility, RspecStrings.ResourceManager);

        protected sealed override DiagnosticDescriptor Rule => rule;

        protected override void Initialize(SonarAnalysisContext context)
        {
            context.RegisterSyntaxNodeActionInNonGenerated(
                c =>
                {
                    var argumentList = (AttributeArgumentListSyntax)c.Node;
                    if (!argumentList.Arguments.Any())
                    {
                        c.ReportDiagnostic(Diagnostic.Create(Rule, argumentList.GetLocation()));
                    }
                },
                SyntaxKind.AttributeArgumentList);

            context.RegisterSyntaxNodeActionInNonGenerated(
                c =>
                {
                    var objectCreation = (ObjectCreationExpressionSyntax)c.Node;
                    var argumentList = objectCreation.ArgumentList;
                    if (argumentList != null &&
                        objectCreation.Initializer != null &&
                        !argumentList.Arguments.Any())
                    {
                        c.ReportDiagnostic(Diagnostic.Create(Rule, argumentList.GetLocation()));
                    }
                },
                SyntaxKind.ObjectCreationExpression);

            context.RegisterSyntaxNodeActionInNonGenerated(
                c =>
                {
                    var expression = (ParenthesizedExpressionSyntax)c.Node;

                    if (!(expression.Parent is ParenthesizedExpressionSyntax) &&
                        (expression.Expression is ParenthesizedExpressionSyntax))
                    {
                        var innermostExpression = GetSelfAndDescendantParenthesizedExpressions(expression)
                            .Reverse()
                            .Skip(1)
                            .First(); // There are always at least two parenthesized expressions

                        var location = GetLocation(expression.SyntaxTree,
                            expression.OpenParenToken.GetLocation(),
                            innermostExpression.OpenParenToken.GetLocation());

                        var secondaryLocation = GetLocation(expression.SyntaxTree,
                            innermostExpression.CloseParenToken.GetLocation(),
                            expression.CloseParenToken.GetLocation());

                        c.ReportDiagnostic(Diagnostic.Create(Rule, location, additionalLocations: new[] { secondaryLocation }));
                    }
                },
                SyntaxKind.ParenthesizedExpression);
        }

        private static Location GetLocation(SyntaxTree syntaxTree, Location firstLocation, Location secondLocation)
        {
            var textSpan = TextSpan.FromBounds(
                firstLocation.SourceSpan.Start, 
                secondLocation.SourceSpan.End);

            return Location.Create(syntaxTree, textSpan);
        }

        private IEnumerable<ParenthesizedExpressionSyntax> GetSelfAndDescendantParenthesizedExpressions(ParenthesizedExpressionSyntax expression)
        {
            var descendant = expression;
            while (descendant != null)
            {
                yield return descendant;
                descendant = descendant.Expression as ParenthesizedExpressionSyntax;
            }
        }
    }
}
