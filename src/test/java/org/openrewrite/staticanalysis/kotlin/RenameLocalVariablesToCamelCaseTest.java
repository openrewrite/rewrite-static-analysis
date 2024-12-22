/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.staticanalysis.kotlin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.staticanalysis.RenameLocalVariablesToCamelCase;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class RenameLocalVariablesToCamelCaseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RenameLocalVariablesToCamelCase());
    }

    @DocumentExample
    @Test
    void regular() {
        rewriteRun(
          kotlin(
            """
              fun foo() {
                  var EMPTY_METAS = HashMap<String, Any>()
                  EMPTY_METAS.isEmpty()
              }
              """,
            """
              fun foo() {
                  var emptyMetas = HashMap<String, Any>()
                  emptyMetas.isEmpty()
              }
              """
          )
        );
    }

    @Disabled("A bug to be fixed")
    @Test
    void renameBothVariableAndUsage() {
        rewriteRun(
          kotlin(
            """
              class MqttRegex (val topic : String)
              """
          ),
          kotlin(
            """
              fun foo() {
                  val MQTT = MqttRegex("topic1")
                  val x = listOf("", MQTT.topic)
              }
              """,
            """
              fun foo() {
                  val mqtt = MqttRegex("topic1")
                  val x = listOf("", mqtt.topic)
              }
              """
          )
        );
    }

    // `internal` modifier means package-private in Kotlin, so it's not a local variable
    @Test
    void doNotChangeIfHasInternalModifier() {
        rewriteRun(
          kotlin(
            """
              internal val EMPTY_METAS = HashMap<String, Any>()
              """
          )
        );
    }


}
