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

class RemovePmdRuleTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemovePmdRule("category/java/codestyle.xml/AvoidFinalLocalVariable"));
    }

    @DocumentExample
    @Test
    void removeRuleReference() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/AvoidFinalLocalVariable"/>
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void removeRuleReferenceWithNestedProperties() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/AvoidFinalLocalVariable">
                      <priority>2</priority>
                  </rule>
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void removeExclusion() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml">
                      <exclude name="AvoidFinalLocalVariable"/>
                      <exclude name="ShortVariable"/>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml">
                      <exclude name="ShortVariable"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void removeEveryReferenceInOneRuleset() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <!-- Local variables are better off final -->
                  <rule ref="category/java/codestyle.xml/AvoidFinalLocalVariable"/>
                  <rule ref="category/java/codestyle.xml">
                      <exclude name="AvoidFinalLocalVariable"/>
                  </rule>
                  <rule ref="category/java/codestyle.xml/AvoidFinalLocalVariable">
                      <priority>1</priority>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void removeByBareRuleName() {
        rewriteRun(
          spec -> spec.recipe(new RemovePmdRule("AvoidFinalLocalVariable")),
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="rulesets/java/mine.xml/AvoidFinalLocalVariable"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotRemoveWhenReferencedFromAnotherRuleset() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="rulesets/java/mine.xml/AvoidFinalLocalVariable"/>
                  <rule ref="rulesets/java/mine.xml">
                      <exclude name="AvoidFinalLocalVariable"/>
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
                  <rule ref="category/java/codestyle.xml/AvoidFinalLocalVariable"/>
              </project>
              """
          )
        );
    }
}
