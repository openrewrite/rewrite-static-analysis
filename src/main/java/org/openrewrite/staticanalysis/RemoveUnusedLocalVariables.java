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

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.DeleteStatement;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.javascript.JavascriptFileChecker;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;
import static org.openrewrite.Preconditions.and;
import static org.openrewrite.Preconditions.not;

@EqualsAndHashCode(callSuper = false)
@SuppressWarnings("ConstantConditions")
@Value
public class RemoveUnusedLocalVariables extends Recipe {
    @Incubating(since = "7.17.2")
    @Option(displayName = "Ignore matching variable names",
            description = "An array of variable identifier names for local variables to ignore, even if the local variable is unused.",
            required = false,
            example = "[unused, notUsed, IGNORE_ME]")
    String @Nullable [] ignoreVariablesNamed;

    @Option(displayName = "Only remove variables of a given type",
            description = "A fully qualified class name. Only unused local variables whose type matches this will be removed. " +
                    "If empty or not set, all unused local variables are considered for removal.",
            required = false,
            example = "java.lang.String")
    @Nullable
    String withType;

    @Option(displayName = "Remove unused local variables with side effects in initializer",
            description = "Whether to remove unused local variables despite side effects in the initializer. Default false.",
            required = false)
    @Nullable
    Boolean withSideEffects;

    @JsonCreator
    public RemoveUnusedLocalVariables(
            String @Nullable [] ignoreVariablesNamed,
            @Nullable String withType,
            @Nullable Boolean withSideEffects) {
        this.ignoreVariablesNamed = ignoreVariablesNamed;
        this.withType = withType;
        this.withSideEffects = withSideEffects;
    }

    @Deprecated
    public RemoveUnusedLocalVariables(
            String @Nullable [] ignoreVariablesNamed,
            @Nullable Boolean withSideEffects) {
        this(ignoreVariablesNamed, null, withSideEffects);
    }

    @Override
    public String getDisplayName() {
        return "Remove unused local variables";
    }

    @Override
    public String getDescription() {
        return "If a local variable is declared but not used, it is dead code and should be removed.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1481");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // All methods that start with 'get' matching this InvocationMatcher will be considered non-side effecting.
        MethodMatcher SAFE_GETTER_METHODS = new MethodMatcher("java.io.File get*(..)");

        Set<String> ignoreVariableNames;
        if (ignoreVariablesNamed == null) {
            ignoreVariableNames = null;
        } else {
            ignoreVariableNames = new HashSet<>(ignoreVariablesNamed.length);
            ignoreVariableNames.addAll(Arrays.asList(ignoreVariablesNamed));
        }

        TreeVisitor<?, ExecutionContext> notJsNorKt = and(not(new JavascriptFileChecker<>()), not(new KotlinFileChecker<>()));
        return Preconditions.check(notJsNorKt, new JavaIsoVisitor<ExecutionContext>() {
            private Cursor getCursorToParentScope(Cursor cursor) {
                return cursor.dropParentUntil(is ->
                        is instanceof J.ClassDeclaration ||
                        is instanceof J.Block ||
                        is instanceof J.MethodDeclaration ||
                        is instanceof J.ForLoop ||
                        is instanceof J.ForEachLoop ||
                        is instanceof J.ForLoop.Control ||
                        is instanceof J.ForEachLoop.Control ||
                        is instanceof J.Case ||
                        is instanceof J.Try ||
                        is instanceof J.Try.Resource ||
                        is instanceof J.Try.Catch ||
                        is instanceof J.MultiCatch ||
                        is instanceof J.Lambda ||
                        is instanceof JavaSourceFile
                );
            }

            @Override
            public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, ExecutionContext ctx) {
                return instanceOf;
            }

            @Override
            public J.DeconstructionPattern visitDeconstructionPattern(J.DeconstructionPattern deconstructionPattern, ExecutionContext ctx) {
                return deconstructionPattern;
            }

            @Override
            public J.VariableDeclarations.@Nullable NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                // skip matching ignored variable names right away
                if (ignoreVariableNames != null && ignoreVariableNames.contains(variable.getSimpleName())) {
                    return variable;
                }

                // skip matching ignored variable types right away
                if (withType != null && !TypeUtils.isOfClassType(variable.getType(), withType)) {
                    return variable;
                }

                Cursor parentScope = getCursorToParentScope(getCursor());
                J parent = parentScope.getValue();
                if (parentScope.getParent() == null ||
                    // skip class instance variables. parentScope.getValue() covers java records.
                    parentScope.getParent().getValue() instanceof J.ClassDeclaration || parentScope.getValue() instanceof J.ClassDeclaration ||
                    // skip anonymous class instance variables
                    parentScope.getParent().getValue() instanceof J.NewClass ||
                    // skip if method declaration parameter
                    parent instanceof J.MethodDeclaration ||
                    // skip if defined in an enhanced or standard for loop, since there isn't much we can do about the semantics at that point
                    parent instanceof J.ForLoop.Control || parent instanceof J.ForEachLoop.Control ||
                    // skip if defined in a switch case
                    parent instanceof J.Case ||
                    // skip if defined in a try's catch clause as an Exception variable declaration
                    parent instanceof J.Try.Resource || parent instanceof J.Try.Catch || parent instanceof J.MultiCatch ||
                    // skip if defined as a parameter to a lambda expression
                    parent instanceof J.Lambda ||
                    // skip if the initializer may have a side effect
                    mightSideEffect(variable.getInitializer())
                ) {
                    return variable;
                }

                List<J> readReferences = VariableReferences.findRhsReferences(parentScope.getValue(), variable.getName());
                if (readReferences.isEmpty()) {
                    List<Statement> assignmentReferences = VariableReferences.findLhsReferences(parentScope.getValue(), variable.getName());
                    for (Statement ref : assignmentReferences) {
                        if (ref instanceof J.Assignment) {
                            if (mightSideEffect(((J.Assignment) ref).getAssignment())) {
                                return variable;
                            }
                            doAfterVisit(new PruneAssignmentExpression((J.Assignment) ref));
                        }
                        doAfterVisit(new DeleteStatement<>(ref));
                    }
                    return null;
                }

                return super.visitVariable(variable, ctx);
            }

            @Override
            public Statement visitStatement(Statement statement, ExecutionContext ctx) {
                List<Comment> comments = getCursor().pollNearestMessage("COMMENTS_KEY");
                if (comments != null) {
                    statement = statement.withComments(ListUtils.concatAll(statement.getComments(), comments));
                }
                return super.visitStatement(statement, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                if (!service(AnnotationService.class).getAllAnnotations(getCursor()).isEmpty()) {
                    return multiVariable;
                }

                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                if (mv.getVariables().isEmpty()) {
                    if (!mv.getPrefix().getComments().isEmpty()) {
                        getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).putMessage("COMMENTS_KEY", mv.getPrefix().getComments());
                    }
                    doAfterVisit(new DeleteStatement<>(mv));
                }
                return mv;
            }

            private boolean mightSideEffect(Expression initializer) {
                return initializer != null && new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, AtomicBoolean result) {
                        if (SAFE_GETTER_METHODS.matches(methodInvocation)) {
                            return methodInvocation;
                        }
                        if (withSideEffects == null || !withSideEffects) {
                            result.set(true);
                        }
                        return methodInvocation;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean result) {
                        result.set(true);
                        return newClass;
                    }

                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean result) {
                        result.set(true);
                        return assignment;
                    }
                }.reduce(initializer, new AtomicBoolean(false)).get();
            }
        });
    }

    /**
     * Take an assignment in a context other than a variable declaration, such as the arguments of a function invocation or if condition,
     * and remove the assignment, leaving behind the value being assigned.
     */
    @EqualsAndHashCode(callSuper = false)
    @Value
    private static class PruneAssignmentExpression extends JavaIsoVisitor<ExecutionContext> {
        J.Assignment assignment;

        @Override
        public <T extends J> J.ControlParentheses<T> visitControlParentheses(J.ControlParentheses<T> c, ExecutionContext ctx) {
            return (J.ControlParentheses<T>) new AssignmentToLiteral(assignment)
                    .visitNonNull(c, ctx, getCursor().getParentOrThrow());
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext ctx) {
            AssignmentToLiteral atl = new AssignmentToLiteral(assignment);
            return m.withArguments(ListUtils.map(m.getArguments(), it -> {
                if (it instanceof J.Assignment) {
                    return (Expression) atl.visitNonNull(it, ctx, getCursor().getParentOrThrow());
                }
                return it;
            }));
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Value
    private static class AssignmentToLiteral extends JavaVisitor<ExecutionContext> {
        J.Assignment assignment;

        @Override
        public J visitAssignment(J.Assignment a, ExecutionContext ctx) {
            if (assignment.isScope(a)) {
                return a.getAssignment().withPrefix(a.getPrefix());
            }
            return a;
        }
    }

}
