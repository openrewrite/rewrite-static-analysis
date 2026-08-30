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

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.xml.Assertions.xml;

class AddPmdRuleTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddPmdRule("category/java/errorprone.xml/EmptyCatchBlock"));
    }

    @DocumentExample
    @Test
    void addRuleReference() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/AvoidBranchingStatementAsLastInLoop"/>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/AvoidBranchingStatementAsLastInLoop"/>
                  <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void addRuleToRulesetWithoutRules() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <description>My rules</description>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <description>My rules</description>
                  <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void enableRuleByRemovingExclusion() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="EmptyCatchBlock"/>
                      <exclude name="MissingSerialVersionUID"/>
                  </rule>
              </ruleset>
              """,
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="MissingSerialVersionUID"/>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeRulesetThatAlreadyReferencesRule() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml/EmptyCatchBlock">
                      <priority>2</priority>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeRulesetThatReferencesWholeRulesetFile() {
        rewriteRun(
          //language=xml
          xml(
            """
              <?xml version="1.0"?>
              <ruleset name="custom">
                  <rule ref="category/java/errorprone.xml">
                      <exclude name="MissingSerialVersionUID"/>
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
                  <rule ref="category/java/errorprone.xml/AvoidBranchingStatementAsLastInLoop"/>
              </project>
              """
          )
        );
    }

    @Test
    void bareRuleNameIsInvalid() {
        assertThat(new AddPmdRule("EmptyCatchBlock").validate().isValid()).isFalse();
    }
}
