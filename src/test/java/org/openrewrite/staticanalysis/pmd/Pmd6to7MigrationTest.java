/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis.pmd;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class Pmd6to7MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.staticanalysis")
          .build()
          .activateRecipes("org.openrewrite.staticanalysis.pmd.Pmd6to7Migration"));
    }

    @DocumentExample
    @Test
    void migrateRuleset() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom"
                       xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
                  <description>Our rules</description>
                  <rule ref="category/java/bestpractices.xml/UnusedImports"/>
                  <rule ref="category/java/errorprone.xml/MissingBreakInSwitch"/>
                  <rule ref="category/java/design.xml/StdCyclomaticComplexity"/>
                  <rule ref="category/java/performance.xml/AvoidUsingShortType"/>
                  <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom"
                       xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
                  <description>Our rules</description>
                  <rule ref="category/java/codestyle.xml/UnnecessaryImport"/>
                  <rule ref="category/java/errorprone.xml/ImplicitSwitchFallThrough"/>
                  <rule ref="category/java/design.xml/CyclomaticComplexity"/>
                  <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void migrateExclusions() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/design.xml">
                      <exclude name="NcssMethodCount"/>
                      <exclude name="LawOfDemeter"/>
                  </rule>
                  <rule ref="category/java/codestyle.xml">
                      <exclude name="AvoidFinalLocalVariable"/>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/design.xml">
                      <exclude name="NcssCount"/>
                      <exclude name="LawOfDemeter"/>
                  </rule>
                  <rule ref="category/java/codestyle.xml"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void migrateRulesReplacedByEmptyControlStatement() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/EmptyIfStmt"/>
                  <rule ref="category/java/errorprone.xml/EmptyWhileStmt"/>
                  <rule ref="category/java/errorprone.xml/EmptyStatementNotInLoop"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/EmptyControlStatement"/>
                  <rule ref="category/java/codestyle.xml/EmptyControlStatement"/>
                  <rule ref="category/java/codestyle.xml/UnnecessarySemicolon"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeRulesetThatIsAlreadyOnPmd7() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/UnnecessaryImport"/>
                  <rule ref="category/java/errorprone.xml/ImplicitSwitchFallThrough"/>
                  <rule ref="category/java/design.xml">
                      <exclude name="NcssCount"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotGuessWhenPmdSplitARuleAcrossSeveralSuccessors() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/VariableNamingConventions"/>
                  <rule ref="category/java/performance.xml/IntegerInstantiation"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void noChanges_on_empty_ruleset() {
        //language=java
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset"
                     xmlns="http://sourceforge.net"
                     xmlns:xsi="http://w3.org"
                     xsi:schemaLocation="http://sourceforge.net https://sourceforge.net">
                <description>This is a description</description>
            </ruleset>
            """
          ));
    }

    @Test
    void preserve_custom_rule() {
        //language=java
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset"
                     xmlns="http://sourceforge.net"
                     xmlns:xsi="http://w3.org"
                     xsi:schemaLocation="http://sourceforge.net https://sourceforge.net">
                <description>This is a description</description>
                <rule ref="mycorp/MyCustomRule" />
            </ruleset>
            """
          ));
    }

    @Test
    void leaveRulesetTagThatIsNotTheDocumentRoot() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <configuration>
                <ruleset name="notpmd">
                    <rule ref="category/java/errorprone.xml/DontImportSun" />
                </ruleset>
            </configuration>
            """
          ));
    }

    @Test
    void removeStaleExclusionWhenReplacementMovesToAnotherRuleset() {
        rewriteRun(
          spec -> spec.recipe(new ReplacePmdRule(
            "category/java/errorprone.xml/EmptyIfStmt", "category/java/codestyle.xml/EmptyControlStatement")),
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="EmptyIfStmt"/>
                      <exclude name="EmptyCatchBlock"/>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="EmptyCatchBlock"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }
}
