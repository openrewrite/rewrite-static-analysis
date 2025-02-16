/*
 * Copyright 2025 the original author or authors.
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

import java.time.Duration;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class ReplaceArraysAsListWithListOf extends Recipe {

  private static final MethodMatcher ARRAYS_AS_LIST = new MethodMatcher("java.util.Arrays asList(..)");

  @Override
  public String getDisplayName() {
    return "Replace Arrays.asList() with List.of()";
  }

  @Override
  public String getDescription() {
    return "Replace `Arrays.asList()` with `List.of()` when the number of arguments is less than 11.";
  }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(1);
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(new UsesMethod<>(ARRAYS_AS_LIST), new JavaVisitor<ExecutionContext>() {

      private final JavaTemplate template = JavaTemplate.builder("List.of(#{any(java.lang.Object[])})")
        .imports("java.util.List")
        .build();

      @Override
      public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation result = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
        if (ARRAYS_AS_LIST.matches(method) && method.getArguments().size() < 11) {
          result = template.apply(updateCursor(result), result.getCoordinates().replace(),
            result.getArguments().toArray());
          maybeAddImport("java.util.List");
          maybeRemoveImport("java.util.Arrays");
        }
        return result;
      }
    });
  }
}
