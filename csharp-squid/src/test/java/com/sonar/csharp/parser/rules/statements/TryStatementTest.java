/*
 * Copyright (C) 2010 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package com.sonar.csharp.parser.rules.statements;

import static com.sonar.sslr.test.parser.ParserMatchers.parse;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.sonar.csharp.parser.CSharpGrammar;
import com.sonar.csharp.parser.CSharpParser;

public class TryStatementTest {

  CSharpParser p = new CSharpParser();
  CSharpGrammar g = p.getGrammar();

  @Before
  public void init() {
    p.setRootRule(g.tryStatement);
    g.block.mock();
    g.catchClauses.mock();
    g.finallyClause.mock();
  }

  @Test
  public void testOk() {
    assertThat(p, parse("try block catchClauses"));
    assertThat(p, parse("try block finallyClause"));
    assertThat(p, parse("try block catchClauses finallyClause"));
  }

}
