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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class ReplacePmdRuleTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplacePmdRule(
          "category/java/errorprone.xml/MissingBreakInSwitch", "ImplicitSwitchFallThrough"));
    }

    @DocumentExample
    @Test
    void replaceRuleReference() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/MissingBreakInSwitch"/>
                  <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/ImplicitSwitchFallThrough"/>
                  <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void replaceRuleReferenceKeepingNestedProperties() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/MissingBreakInSwitch">
                      <priority>2</priority>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/ImplicitSwitchFallThrough">
                      <priority>2</priority>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void replaceExclusion() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="MissingBreakInSwitch"/>
                      <exclude name="EmptyCatchBlock"/>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="ImplicitSwitchFallThrough"/>
                      <exclude name="EmptyCatchBlock"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void moveRuleToAnotherRuleset() {
        rewriteRun(
          spec -> spec.recipe(new ReplacePmdRule(
            "category/java/errorprone.xml/EmptyIfStmt", "category/java/codestyle.xml/EmptyControlStatement")),
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/EmptyIfStmt"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/EmptyControlStatement"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void replaceByBareRuleNameRegardlessOfRuleset() {
        rewriteRun(
          spec -> spec.recipe(new ReplacePmdRule("MissingBreakInSwitch", "ImplicitSwitchFallThrough")),
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="rulesets/java/mine.xml/MissingBreakInSwitch"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="rulesets/java/mine.xml/ImplicitSwitchFallThrough"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void removeExclusionWhenReplacementIsInAnotherRuleset() {
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
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeRuleReferencedFromAnotherRuleset() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="rulesets/java/mine.xml/MissingBreakInSwitch"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeCustomRuleDefinedInPlace() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule name="MissingBreakInSwitch" language="java" class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
                      <description>My own take on it</description>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeNonRulesetXml() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <project>
                  <rule ref="category/java/errorprone.xml/MissingBreakInSwitch"/>
              </project>
              """
          )
        );
    }
}
