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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class ReplaceStringConcatenationWithStringValueOf extends Recipe {

    private static final MethodMatcher METHOD_MATCHER = new MethodMatcher("java.lang.String#valueOf(..)", false);

    @Getter
    final String displayName = "Replace String concatenation with `String.valueOf()`";

    @Getter
    final String description = "Replace inefficient string concatenation patterns like `\"\" + ...` with " +
            "`String.valueOf(...)`. This improves code readability and may have minor performance " +
            "benefits. The empty string prefix `\"\" +` is an indirect way to convert a value to " +
            "a `String`, while `String.valueOf()` clearly communicates the conversion intent. " +
            "Concatenation with a `char[]` is left unchanged, since `String.valueOf(char[])` renders " +
            "the array's contents while concatenation renders the array like any other `Object`.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1153");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // The equivalence relies on Java's string conversion; Groovy renders an `int[]` as `[1, 2]` through `+`
        // but as a type-hash string through `String.valueOf`
        return Preconditions.check(new JavaFileChecker<>(), new JavaVisitor<ExecutionContext>() {
            @Override
            public <T extends J> J visitParentheses(J.Parentheses<T> parens, ExecutionContext ctx) {
                J p = super.visitParentheses(parens, ctx);
                if (p instanceof J.Parentheses) {
                    J tree = ((J.Parentheses<?>) p).getTree();
                    if (tree instanceof J.MethodInvocation && METHOD_MATCHER.matches((MethodCall) tree)) {
                        return tree.withPrefix(p.getPrefix());
                    }
                }
                return p;
            }

            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                Expression right = binary.getRight();
                while (right instanceof J.Parentheses && ((J.Parentheses<?>) right).getTree() instanceof Expression) {
                    right = (Expression) ((J.Parentheses<?>) right).getTree();
                }
                JavaType rightType = right.getType();
                JavaType.Array arrayType = TypeUtils.asArray(rightType);
                if (J.Literal.isLiteralValue(binary.getLeft(), "") &&
                        binary.getOperator() == J.Binary.Type.Addition &&
                        rightType != null &&
                        !TypeUtils.isString(rightType) &&
                        // `String.valueOf(null)` selects the `char[]` overload and throws, while `"" + null` yields "null"
                        !J.Literal.isLiteralValue(right, null) &&
                        // Concatenation renders a `char[]` like any `Object`; `String.valueOf(char[])` renders its
                        // contents, or throws on null
                        (arrayType == null || arrayType.getElemType() != JavaType.Primitive.Char) &&
                        // Avoid breaking symmetry in chained String concatenations
                        !(binary.getRight() instanceof J.Binary) &&
                        !(getCursor().getParentTreeCursor().getValue() instanceof J.Binary)) {
                    return JavaTemplate.apply("String.valueOf(#{any()})", getCursor(), binary.getCoordinates().replace(), right)
                            .withPrefix(binary.getPrefix());
                }
                return super.visitBinary(binary, ctx);
            }
        });
    }
}
