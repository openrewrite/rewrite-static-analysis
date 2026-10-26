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
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ModernizeCollectionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.staticanalysis.ModernizeCollections");
    }

    @DocumentExample
    @Test
    void replaceConfinedLegacyCollections() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Hashtable;
              import java.util.Stack;
              import java.util.Vector;

              class Test {
                  String test() {
                      Hashtable<String, Integer> table = new Hashtable<>();
                      table.put("a", 1);
                      Vector<Integer> vector = new Vector<>();
                      vector.add(table.size());
                      Stack<Integer> stack = new Stack<>();
                      stack.add(vector.size());
                      StringBuffer sb = new StringBuffer();
                      sb.append(stack.size());
                      return sb.toString();
                  }
              }
              """,
            """
              import java.util.*;

              class Test {
                  String test() {
                      HashMap<String, Integer> table = new HashMap<>();
                      table.put("a", 1);
                      ArrayList<Integer> vector = new ArrayList<>();
                      vector.add(table.size());
                      Deque<Integer> stack = new ArrayDeque<>();
                      stack.add(vector.size());
                      StringBuilder sb = new StringBuilder();
                      sb.append(stack.size());
                      return sb.toString();
                  }
              }
              """
          )
        );
    }
}
