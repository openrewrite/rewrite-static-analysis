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

class Pmd7RuleRenamesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.staticanalysis")
          .build()
          .activateRecipes("org.openrewrite.staticanalysis.pmd.Pmd7RuleRenames"));
    }

    @DocumentExample
    @Test
    void renameRulesThatPmd7Renamed() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom"
                       xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
                  <description>Our rules</description>
                  <rule ref="category/java/bestpractices.xml/JUnit5TestShouldBePackagePrivate"/>
                  <rule ref="category/java/bestpractices.xml/JUnitTestsShouldIncludeAssert"/>
                  <rule ref="category/java/bestpractices.xml/SwitchStmtsShouldHaveDefault"/>
                  <rule ref="category/java/design.xml/UseUtilityClass"/>
                  <rule ref="category/java/performance.xml/TooFewBranchesForASwitchStatement"/>
                  <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom"
                       xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
                  <description>Our rules</description>
                  <rule ref="category/java/bestpractices.xml/JUnitJupiterTestShouldBePackagePrivate"/>
                  <rule ref="category/java/bestpractices.xml/UnitTestShouldIncludeAssert"/>
                  <rule ref="category/java/bestpractices.xml/NonExhaustiveSwitch"/>
                  <rule ref="category/java/design.xml/InstantiableUtilityClass"/>
                  <rule ref="category/java/performance.xml/TooFewBranchesForSwitch"/>
                  <rule ref="category/java/bestpractices.xml/AvoidReassigningParameters"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void renameJUnit4RulesToTheirUnitTestNames() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/bestpractices.xml/JUnit4TestShouldUseAfterAnnotation"/>
                  <rule ref="category/java/bestpractices.xml/JUnit4TestShouldUseBeforeAnnotation"/>
                  <rule ref="category/java/bestpractices.xml/JUnit4TestShouldUseTestAnnotation"/>
                  <rule ref="category/java/bestpractices.xml/JUnitAssertionsShouldIncludeMessage"/>
                  <rule ref="category/java/bestpractices.xml/JUnitTestContainsTooManyAsserts"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/bestpractices.xml/UnitTestShouldUseAfterAnnotation"/>
                  <rule ref="category/java/bestpractices.xml/UnitTestShouldUseBeforeAnnotation"/>
                  <rule ref="category/java/bestpractices.xml/UnitTestShouldUseTestAnnotation"/>
                  <rule ref="category/java/bestpractices.xml/UnitTestAssertionsShouldIncludeMessage"/>
                  <rule ref="category/java/bestpractices.xml/UnitTestContainsTooManyAsserts"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void renameExclusions() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/bestpractices.xml">
                      <exclude name="DefaultLabelNotLastInSwitchStmt"/>
                      <exclude name="AvoidReassigningParameters"/>
                  </rule>
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="NonCaseLabelInSwitchStatement"/>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/bestpractices.xml">
                      <exclude name="DefaultLabelNotLastInSwitch"/>
                      <exclude name="AvoidReassigningParameters"/>
                  </rule>
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="NonCaseLabelInSwitch"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void moveRuleThatChangedCategory() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/design.xml/AvoidCatchingGenericException"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/AvoidCatchingGenericException"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeRulesetThatAlreadyUsesTheCurrentNames() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/bestpractices.xml/JUnitJupiterTestShouldBePackagePrivate"/>
                  <rule ref="category/java/bestpractices.xml/NonExhaustiveSwitch"/>
                  <rule ref="category/java/design.xml/InstantiableUtilityClass"/>
                  <rule ref="category/java/errorprone.xml/AvoidCatchingGenericException"/>
                  <rule ref="category/java/bestpractices.xml">
                      <exclude name="DefaultLabelNotLastInSwitch"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotAdoptASuccessorRuleThatReportsSomethingElse() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/AvoidCatchingNPE"/>
                  <rule ref="category/java/errorprone.xml/CheckSkipResult"/>
                  <rule ref="category/java/codestyle.xml/GenericsNaming"/>
                  <rule ref="category/java/codestyle.xml/UnnecessaryLocalBeforeReturn"/>
                  <rule ref="category/java/design.xml/UseObjectForClearerAPI"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotRenameARuleThatOnlyLooksLikeAPmdRule() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="mycorp/rules.xml/UseUtilityClass"/>
              </ruleset>
              """
          )
        );
    }
}
