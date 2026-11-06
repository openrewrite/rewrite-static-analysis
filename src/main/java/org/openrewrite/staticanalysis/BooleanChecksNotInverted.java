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
import org.openrewrite.groovy.tree.G;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.javascript.JavascriptFileChecker;
import org.openrewrite.staticanalysis.python.PythonFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.openrewrite.java.ParenthesizeVisitor.maybeParenthesize;

public class BooleanChecksNotInverted extends Recipe {

    @Getter
    final String displayName = "Boolean checks should not be inverted";

    @Getter
    final String description = "Ensures that boolean checks are not unnecessarily inverted. " +
            "Also fixes double negative boolean expressions. Negating a comparison and " +
            "then inverting it adds cognitive overhead; using the direct operator " +
            "(e.g., `>=` instead of `!(... < ...)`) is clearer and easier to reason about. " +
            "Ordered floating-point comparisons retain their negation to preserve NaN behavior.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1940");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J preVisit(J tree, ExecutionContext ctx) {
                // Python truthiness, structural patterns and statement rules differ from Java.
                if (PythonFileChecker.isPython(tree) || JavascriptFileChecker.isJavaScript(tree)) {
                    stopAfterPreVisit();
                }
                return tree;
            }

            @Override
            @SuppressWarnings("ConstantConditions")
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                // Groovy's !! is also Spock's condition-rendering opt-out operator.
                // Keep the pair intact, including its comparison parentheses.
                Expression operand = unary.getExpression().unwrap();
                if (getCursor().firstEnclosing(SourceFile.class) instanceof G.CompilationUnit &&
                    unary.getOperator() == J.Unary.Type.Not && operand instanceof J.Unary &&
                    ((J.Unary) operand).getOperator() == J.Unary.Type.Not) {
                    return unary;
                }
                unary = (J.Unary) super.visitUnary(unary, ctx);
                if (unary.getOperator() == J.Unary.Type.Not && unary.getExpression() instanceof J.Parentheses) {
                    J.Parentheses<?> expr = (J.Parentheses<?>) unary.getExpression();
                    if (expr.getTree() instanceof J.Binary) {
                        J.Binary binary = (J.Binary) expr.getTree();
                        if (isComparison(binary.getLeft()) || isComparison(binary.getRight())) {
                            return unary;
                        }
                        switch (binary.getOperator()) {
                            case LessThan:
                            case GreaterThan:
                            case LessThanOrEqual:
                            case GreaterThanOrEqual:
                                // A negated ordered comparison accepts NaN; its complementary operator does not.
                                if (!isKnownIntegral(binary.getLeft().getType()) ||
                                    !isKnownIntegral(binary.getRight().getType())) {
                                    return unary;
                                }
                                break;
                        }
                        switch (binary.getOperator()) {
                            case LessThan:
                                return maybeParenthesize(binary.withOperator(J.Binary.Type.GreaterThanOrEqual)
                                        .withPrefix(unary.getPrefix()), getCursor());
                            case GreaterThan:
                                return maybeParenthesize(binary.withOperator(J.Binary.Type.LessThanOrEqual)
                                        .withPrefix(unary.getPrefix()), getCursor());
                            case LessThanOrEqual:
                                return maybeParenthesize(binary.withOperator(J.Binary.Type.GreaterThan)
                                        .withPrefix(unary.getPrefix()), getCursor());
                            case GreaterThanOrEqual:
                                return maybeParenthesize(binary.withOperator(J.Binary.Type.LessThan)
                                        .withPrefix(unary.getPrefix()), getCursor());
                            case Equal:
                                return maybeParenthesize(binary.withOperator(J.Binary.Type.NotEqual)
                                        .withPrefix(unary.getPrefix()), getCursor());
                            case NotEqual:
                                return maybeParenthesize(binary.withOperator(J.Binary.Type.Equal)
                                        .withPrefix(unary.getPrefix()), getCursor());
                        }
                    } else if (expr.getTree() instanceof J.Unary) {
                        J.Unary nestedUnary = (J.Unary) expr.getTree();
                        // Preserve boxed Boolean unboxing and non-boolean truth coercion.
                        if (nestedUnary.getOperator() == J.Unary.Type.Not &&
                            nestedUnary.getExpression().getType() == JavaType.Primitive.Boolean) {
                            return maybeParenthesize(nestedUnary.getExpression()
                                    .withPrefix(unary.getPrefix()), getCursor());
                        }
                    }
                }
                return unary;
            }

            private boolean isKnownIntegral(@Nullable JavaType type) {
                return type == JavaType.Primitive.Byte ||
                       type == JavaType.Primitive.Short ||
                       type == JavaType.Primitive.Int ||
                       type == JavaType.Primitive.Long ||
                       type == JavaType.Primitive.Char ||
                       TypeUtils.isOfClassType(type, "java.lang.Byte") ||
                       TypeUtils.isOfClassType(type, "java.lang.Short") ||
                       TypeUtils.isOfClassType(type, "java.lang.Integer") ||
                       TypeUtils.isOfClassType(type, "java.lang.Long") ||
                       TypeUtils.isOfClassType(type, "java.lang.Character");
            }

            private boolean isComparison(J expr) {
                if (expr instanceof J.Binary) {
                    switch (((J.Binary) expr).getOperator()) {
                        case LessThan:
                        case GreaterThan:
                        case LessThanOrEqual:
                        case GreaterThanOrEqual:
                        case Equal:
                        case NotEqual:
                            return true;
                    }
                }
                return false;
            }
        };
    }
}
