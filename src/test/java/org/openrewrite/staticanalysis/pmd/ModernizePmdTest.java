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

class ModernizePmdTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.staticanalysis")
          .build()
          .activateRecipes("org.openrewrite.staticanalysis.pmd.ModernizePmd"));
    }

    @DocumentExample
    @Test
    void modernizeRulesetWrittenForPmd6() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom"
                       xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
                  <description>Our rules</description>
                  <rule ref="category/java/bestpractices.xml/UnusedImports"/>
                  <rule ref="category/java/bestpractices.xml/JUnitTestsShouldIncludeAssert"/>
                  <rule ref="category/java/design.xml/NcssTypeCount"/>
                  <rule ref="category/java/design.xml/UseUtilityClass"/>
                  <rule ref="category/java/errorprone.xml/CloneThrowsCloneNotSupportedException"/>
                  <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom"
                       xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
                  <description>Our rules</description>
                  <rule ref="category/java/codestyle.xml/UnnecessaryImport"/>
                  <rule ref="category/java/bestpractices.xml/UnitTestShouldIncludeAssert"/>
                  <rule ref="category/java/design.xml/NcssCount"/>
                  <rule ref="category/java/design.xml/InstantiableUtilityClass"/>
                  <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void modernizeExclusions() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/bestpractices.xml">
                      <exclude name="PositionLiteralsFirstInComparisons"/>
                      <exclude name="DefaultLabelNotLastInSwitchStmt"/>
                      <exclude name="AvoidReassigningParameters"/>
                  </rule>
                  <rule ref="category/java/performance.xml">
                      <exclude name="SimplifyStartsWith"/>
                      <exclude name="TooFewBranchesForASwitchStatement"/>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/bestpractices.xml">
                      <exclude name="LiteralsFirstInComparisons"/>
                      <exclude name="DefaultLabelNotLastInSwitch"/>
                      <exclude name="AvoidReassigningParameters"/>
                  </rule>
                  <rule ref="category/java/performance.xml">
                      <exclude name="TooFewBranchesForSwitch"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeRulesetThatIsAlreadyCurrent() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/UnnecessaryImport"/>
                  <rule ref="category/java/design.xml/InstantiableUtilityClass"/>
                  <rule ref="category/java/design.xml/NcssCount"/>
                  <rule ref="category/java/errorprone.xml/AvoidCatchingGenericException"/>
                  <rule ref="category/java/bestpractices.xml">
                      <exclude name="UnitTestShouldIncludeAssert"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeRulesThatRequireAJudgementCall() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/VariableNamingConventions"/>
                  <rule ref="category/java/performance.xml/IntegerInstantiation"/>
                  <rule ref="category/java/errorprone.xml/AvoidCatchingNPE"/>
                  <rule ref="category/java/codestyle.xml/GenericsNaming"/>
                  <rule ref="category/java/design.xml/UseObjectForClearerAPI"/>
              </ruleset>
              """
          )
        );
    }
}
