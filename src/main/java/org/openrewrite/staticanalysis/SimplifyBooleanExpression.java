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
package org.openrewrite.staticanalysis;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.cleanup.SimplifyBooleanExpressionVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.kotlin.marker.IsNullSafe;

import java.util.Set;

import static java.util.Collections.singleton;

public class SimplifyBooleanExpression extends Recipe {

    @Getter
    final String displayName = "Simplify boolean expression";

    @Getter
    final String description = "Checks for overly complicated boolean expressions, such as `if (b == true)`, `b || true`, `!false`, etc.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1125");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyBooleanExpressionVisitor() {

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                // NOTE: This method is required here for the `TreeVisitorAdapter` to work
                return super.visit(tree, ctx);
            }

            // Comparing Kotlin nullable type `?` with true/false can not be simplified,
            // e.g. `X?.fun() == true` is not equivalent to `X?.fun()`
            // and `nullableBoolean == true` is not equivalent to `nullableBoolean`
            @Override
            protected boolean shouldSimplifyEqualsOn(@Nullable J j) {
                if (j instanceof J.MethodInvocation) {
                    return !j.getMarkers().findFirst(IsNullSafe.class).isPresent();
                }
                // For non-Java source files (e.g. Kotlin), only simplify when the
                // expression type is primitive boolean to avoid changing semantics
                // of nullable Boolean comparisons like Boolean? == true
                if (!(getCursor().firstEnclosing(SourceFile.class) instanceof J.CompilationUnit)) {
                    return j instanceof Expression && ((Expression) j).getType() == JavaType.Primitive.Boolean;
                }
                return true;
            }
        };
    }
}
