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
import org.openrewrite.staticanalysis.table.SpotlessFormatters;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.maven.Assertions.pomXml;

class FindSpotlessFormattersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpotlessFormatters());
    }

    @DocumentExample
    @Test
    void maven() {
        rewriteRun(
          spec -> spec.dataTable(SpotlessFormatters.Row.class, rows -> assertThat(rows).containsExactlyInAnyOrder(
            new SpotlessFormatters.Row("pom.xml", "Maven", "java", "palantirJavaFormat", "2.103.0", "style=AOSP",
              "2.103.0", true, null),
            new SpotlessFormatters.Row("pom.xml", "Maven", "kotlin", "ktfmt", "0.51", "style=GOOGLE",
              "0.65", false, null))),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1</version>
                <properties>
                  <palantir.version>2.103.0</palantir.version>
                </properties>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>com.diffplug.spotless</groupId>
                      <artifactId>spotless-maven-plugin</artifactId>
                      <version>3.11.0</version>
                      <configuration>
                        <java>
                          <palantirJavaFormat>
                            <version>${palantir.version}</version>
                            <style>AOSP</style>
                          </palantirJavaFormat>
                          <removeUnusedImports/>
                        </java>
                        <kotlin>
                          <ktfmt>
                            <version>0.51</version>
                            <style>GOOGLE</style>
                          </ktfmt>
                        </kotlin>
                      </configuration>
                    </plugin>
                  </plugins>
                </build>
              </project>
              """,
            """
              <!--~~(java: palantirJavaFormat 2.103.0, kotlin: ktfmt 0.51)~~>--><project>
                <groupId>com.example</groupId>
                <artifactId>app</artifactId>
                <version>1</version>
                <properties>
                  <palantir.version>2.103.0</palantir.version>
                </properties>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>com.diffplug.spotless</groupId>
                      <artifactId>spotless-maven-plugin</artifactId>
                      <version>3.11.0</version>
                      <configuration>
                        <java>
                          <palantirJavaFormat>
                            <version>${palantir.version}</version>
                            <style>AOSP</style>
                          </palantirJavaFormat>
                          <removeUnusedImports/>
                        </java>
                        <kotlin>
                          <ktfmt>
                            <version>0.51</version>
                            <style>GOOGLE</style>
                          </ktfmt>
                        </kotlin>
                      </configuration>
                    </plugin>
                  </plugins>
                </build>
              </project>
              """
          )
        );
    }

    @Test
    void gradleGroovy() {
        rewriteRun(
          spec -> spec.dataTable(SpotlessFormatters.Row.class, rows -> assertThat(rows).containsExactly(
            new SpotlessFormatters.Row("build.gradle", "Gradle", "java", "googleJavaFormat", "1.17.0",
              "reflowLongStrings=true, style=AOSP", "2.0.0", false, null))),
          groovy(
            """
              spotless {
                  java {
                      googleJavaFormat('1.17.0').aosp().reflowLongStrings()
                      licenseHeader '/* (C) $YEAR */'
                  }
              }
              """,
            spec -> spec.path("build.gradle").after(actual -> {
                assertThat(actual).startsWith("/*~~(java: googleJavaFormat 1.17.0)~~>*/");
                return actual;
            })
          )
        );
    }

    @Test
    void gradleKotlin() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()).dataTable(SpotlessFormatters.Row.class, rows -> assertThat(rows).containsExactly(
            new SpotlessFormatters.Row("build.gradle.kts", "Gradle", "kotlin", "ktfmt", "0.65",
              "maxWidth=120, style=KOTLINLANG", "0.65", true, null),
            new SpotlessFormatters.Row("build.gradle.kts", "Gradle", "kotlinGradle", "ktfmt", "libs.ktfmt.get().version",
              "style=GOOGLE", "0.65", false, null))),
          kotlin(
            """
              spotless {
                  kotlin {
                      ktfmt("0.65").kotlinlangStyle().configure { it.setMaxWidth(120) }
                  }
                  kotlinGradle {
                      ktfmt(libs.ktfmt.get().version).googleStyle()
                  }
              }
              """,
            spec -> spec.path("build.gradle.kts").after(actual -> {
                assertThat(actual).startsWith("/*~~(kotlin: ktfmt 0.65, kotlinGradle: ktfmt libs.ktfmt.get().version)~~>*/");
                return actual;
            })
          )
        );
    }

    @Test
    void formatterTheRecipesDoNotRun() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()).dataTable(SpotlessFormatters.Row.class, rows -> assertThat(rows).singleElement()
            .satisfies(row -> assertThat(row.getUnsupported()).isEqualTo("The recipes do not run ktlint."))),
          kotlin(
            """
              spotless {
                  kotlin {
                      ktlint("1.5.0")
                  }
              }
              """,
            spec -> spec.path("build.gradle.kts").after(actual -> actual)
          )
        );
    }
}
