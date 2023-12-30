/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyTernaryTest implements RewriteTest {

    @Test
    void test() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyTernaryRecipes()),
          //language=java
          java(
            """
              class Test {
                  boolean trueCondition1 = true ? true : false;
                  boolean trueCondition2 = false ? false : true;
                  boolean trueCondition3 = booleanExpression() ? true : false;
                  boolean falseCondition1 = true ? false : true;
                  boolean falseCondition2 = false ? true : false;
                  boolean falseCondition3 = booleanExpression() ? false : true;
                  
                  boolean unchanged1 = booleanExpression() ? booleanExpression() : !booleanExpression();
                  boolean unchanged2 = booleanExpression() ? true : !booleanExpression();
                  boolean unchanged3 = booleanExpression() ? booleanExpression() : false;
                  
                  boolean booleanExpression() {
                    return true;
                  }
              }
              """,
            """
              class Test {
                  boolean trueCondition1 = true;
                  boolean trueCondition2 = true;
                  boolean trueCondition3 = booleanExpression();
                  boolean falseCondition1 = false;
                  boolean falseCondition2 = false;
                  boolean falseCondition3 = !booleanExpression();
                  
                  boolean unchanged1 = booleanExpression() ? booleanExpression() : !booleanExpression();
                  boolean unchanged2 = booleanExpression() ? true : !booleanExpression();
                  boolean unchanged3 = booleanExpression() ? booleanExpression() : false;
                  
                  boolean booleanExpression() {
                    return true;
                  }
              }
              """
          )
        );
    }
}