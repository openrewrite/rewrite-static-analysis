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
package org.openrewrite.staticanalysis.format;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class FormatAsSpotlessConfiguredTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FormatAsSpotlessConfigured());
    }

    @DocumentExample
    @Test
    void formatsEachProjectAsItsBuildDoes() {
        rewriteRun(
          mavenProject("aosp",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>aosp</artifactId>
                  <version>1</version>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>com.diffplug.spotless</groupId>
                        <artifactId>spotless-maven-plugin</artifactId>
                        <version>3.11.0</version>
                        <configuration>
                          <java>
                            <googleJavaFormat>
                              <version>2.0.0</version>
                              <style>AOSP</style>
                            </googleJavaFormat>
                          </java>
                        </configuration>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  class A {
                    void m() { int x = 1; }
                  }
                  """,
                """
                  class A {
                      void m() {
                          int x = 1;
                      }
                  }
                  """,
                SourceSpec::noTrim
              )
            )
          ),
          mavenProject("unformatted",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>unformatted</artifactId>
                  <version>1</version>
                </project>
                """
            ),
            srcMainJava(
              //language=java
              java(
                """
                  class B {
                    void m() { int x = 1; }
                  }
                  """
              )
            )
          )
        );
    }
}
