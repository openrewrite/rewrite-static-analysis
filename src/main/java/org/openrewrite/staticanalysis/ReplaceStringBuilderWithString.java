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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.reverse;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class ReplaceStringBuilderWithString extends Recipe {
    private static final MethodMatcher STRING_BUILDER_APPEND = new MethodMatcher("java.lang.StringBuilder append(..)");
    private static final MethodMatcher STRING_BUILDER_APPEND_CHAR_ARRAY = new MethodMatcher("java.lang.StringBuilder append(char[])");
    private static final MethodMatcher STRING_BUILDER_APPEND_KOTLIN_CHAR_ARRAY = new MethodMatcher("java.lang.StringBuilder append(kotlin.CharArray)");
    private static final MethodMatcher STRING_BUILDER_CAPACITY = new MethodMatcher("java.lang.StringBuilder <constructor>(int)");
    private static final MethodMatcher STRING_BUILDER_TO_STRING = new MethodMatcher("java.lang.StringBuilder toString()");

    @Getter
    final String displayName = "Replace `StringBuilder#append` with `String`";

    @Getter
    final String description = "Replace `StringBuilder.append()` with `String` concatenation if you are only concatenating a small number of strings " +
            "and the code is simple and easy to read, as the compiler can optimize simple string concatenation " +
            "expressions into a single `String` object, which can be more efficient than using `StringBuilder`.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(new UsesMethod<>(STRING_BUILDER_APPEND), new UsesMethod<>(STRING_BUILDER_TO_STRING)), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                if (STRING_BUILDER_TO_STRING.matches(method)) {
                    List<Expression> arguments = new ArrayList<>();
                    boolean isFlattenable = flatMethodInvocationChain(method, arguments);
                    if (!isFlattenable || arguments.isEmpty()) {
                        return m;
                    }
                    SourceFile sourceFile = getCursor().firstEnclosingOrThrow(SourceFile.class);
                    boolean kotlin = new KotlinFileChecker<ExecutionContext>().visit(sourceFile, ctx) != sourceFile;

                    reverse(arguments);
                    arguments = adjustExpressions(method, arguments, kotlin);

                    Expression additive = ChainStringBuilderAppendCalls.additiveExpression(arguments);
                    if (additive == null) {
                        return m;
                    }

                    if (arguments.get(0).getComments().isEmpty() || !arguments.get(0).getPrefix().getWhitespace().startsWith("\n")) {
                        additive = additive.withPrefix(method.getPrefix());
                    }

                    if (isAMethodSelect(method)) {
                        additive = new J.Parentheses<>(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(additive));
                    }

                    return additive;
                }
                return m;
            }

            // Check if a method call is a "select" of another method call
            private boolean isAMethodSelect(J.MethodInvocation method) {
                Cursor parent = getCursor().getParent(2); // 2 means skip right padded cursor
                if (parent == null || !(parent.getValue() instanceof J.MethodInvocation)) {
                    return false;
                }
                return ((J.MethodInvocation) parent.getValue()).getSelect() == method;
            }

            private J.@Nullable Literal toStringLiteral(J.Literal input) {
                if (input.getValue() == null) {
                    return null;
                }
                String value = String.valueOf(input.getValue());
                String valueSource;
                if (input.getType() == JavaType.Primitive.Char) {
                    String charSource = input.getValueSource();
                    if (charSource == null || charSource.length() < 3) {
                        return null;
                    }
                    valueSource = "\"".equals(value) ? "\\\"" : "'".equals(value) ? "'" : charSource.substring(1, charSource.length() - 1);
                } else {
                    valueSource = value;
                }
                return new J.Literal(randomId(), input.getPrefix(), input.getMarkers(), value,
                        "\"" + valueSource + "\"", null, JavaType.Primitive.String);
            }

            private List<Expression> adjustExpressions(J.MethodInvocation method, List<Expression> arguments, boolean kotlin) {
                return ListUtils.map(arguments, (i, arg) -> {
                    if (i == 0) {
                        if (!TypeUtils.isString(arg.getType())) {
                            J.Literal literal = arg instanceof J.Literal ? toStringLiteral((J.Literal) arg) : null;
                            if (literal != null) {
                                return literal;
                            }
                            if (kotlin) {
                                Expression select = arg instanceof J.Identifier || arg instanceof J.FieldAccess || arg instanceof J.MethodInvocation || arg instanceof J.Literal || arg instanceof J.Parentheses ?
                                        arg.withPrefix(Space.EMPTY) :
                                        new J.Parentheses<>(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(arg.withPrefix(Space.EMPTY)));
                                JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(arg.getType());
                                JavaType.Method toString = method.getMethodType() == null ? null : method.getMethodType()
                                        .withDeclaringType(declaringType == null ? JavaType.ShallowClass.build("java.lang.Object") : declaringType);
                                return new J.MethodInvocation(randomId(), arg.getPrefix(), Markers.EMPTY, JRightPadded.build(select), null,
                                        new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "toString", toString, null),
                                        JContainer.build(Space.EMPTY, singletonList(JRightPadded.build(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY))), Markers.EMPTY),
                                        toString);
                            }
                            return JavaTemplate.apply("String.valueOf(#{any()})", getCursor(), method.getCoordinates().replace(), arg)
                                    .withPrefix(arg.getPrefix());
                        }
                    } else if (!(arg instanceof J.Identifier || arg instanceof J.Literal || arg instanceof J.MethodInvocation)) {
                        return new J.Parentheses<>(randomId(), arg.getPrefix(), Markers.EMPTY, JRightPadded.build(arg.withPrefix(Space.EMPTY)));
                    }
                    return arg;
                });
            }

            /**
             * Return true if the method calls chain is like "new StringBuilder().append("A")....append("B");"
             *
             * @param method    a StringBuilder.toString() method call
             * @param arguments output expression list to be chained by '+'.
             */
            private boolean flatMethodInvocationChain(J.MethodInvocation method, List<Expression> arguments) {
                Expression select = method.getSelect();
                while (select != null) {
                    if (!(select instanceof J.MethodInvocation)) {
                        break;
                    }

                    J.MethodInvocation selectMethod = (J.MethodInvocation) select;
                    select = selectMethod.getSelect();

                    if (!STRING_BUILDER_APPEND.matches(selectMethod)) {
                        return false;
                    }

                    List<Expression> args = selectMethod.getArguments();
                    if (args.size() != 1) {
                        return false;
                    }
                    Expression arg = args.get(0);
                    JRightPadded<Expression> jrp = selectMethod.getPadding().getSelect();
                    Space prefix = jrp == null ? arg.getPrefix() : jrp.getAfter();
                    if (STRING_BUILDER_APPEND_KOTLIN_CHAR_ARRAY.matches(selectMethod)) {
                        return false;
                    }
                    if (STRING_BUILDER_APPEND_CHAR_ARRAY.matches(selectMethod)) {
                        // `append(char[])` appends the characters of the array, whereas string concatenation would
                        // render the array's identity instead. `String.valueOf(char[])` is selected here, which keeps
                        // the rendered text and the `NullPointerException` thrown for a null array.
                        arg = JavaTemplate.apply("String.valueOf(#{any()})", getCursor(), method.getCoordinates().replace(), arg.withPrefix(Space.EMPTY));
                    }
                    arguments.add(arg.withPrefix(prefix));
                }

                if (select instanceof J.NewClass &&
                        ((J.NewClass) select).getClazz() != null &&
                        TypeUtils.isOfClassType(((J.NewClass) select).getClazz().getType(), "java.lang.StringBuilder")) {
                    J.NewClass nc = (J.NewClass) select;
                    if (nc.getArguments().size() == 1 && TypeUtils.isString(nc.getArguments().get(0).getType())) {
                        arguments.add(nc.getArguments().get(0));
                    } else if (!(nc.getArguments().get(0) instanceof J.Empty) && !STRING_BUILDER_CAPACITY.matches(nc)) {
                        return false;
                    } else if (!arguments.isEmpty()) {
                        Expression lastArgument = arguments.get(arguments.size() - 1);
                        Space formattedPrefix = lastArgument.getComments().isEmpty() ? Space.EMPTY : lastArgument.getPrefix();
                        arguments.set(arguments.size() - 1, lastArgument.withPrefix(formattedPrefix));
                    }
                    return true;
                }
                return false;
            }
        });
    }
}
