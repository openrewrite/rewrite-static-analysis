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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.NewClass;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class UnnecessaryCatch extends Recipe {

    @Option(displayName = "Include `java.lang.Exception`",
            description = "Whether to include `java.lang.Exception` in the list of checked exceptions to remove. " +
                    "Unlike other checked exceptions, `java.lang.Exception` is also the superclass of unchecked exceptions. " +
                    "So removing `catch(Exception e)` may result in changed runtime behavior in the presence of unchecked exceptions. " +
                    "Default `false`",
            required = false)
    boolean includeJavaLangException;

    @Option(displayName = "Include `java.lang.Throwable`",
            description = "Whether to include `java.lang.Throwable` in the list of exceptions to remove. " +
                    "Unlike other checked exceptions, `java.lang.Throwable` is also the superclass of unchecked exceptions. " +
                    "So removing `catch(Throwable e)` may result in changed runtime behavior in the presence of unchecked exceptions. " +
                    "Default `false`",
            required = false)
    boolean includeJavaLangThrowable;

    @Override
    public String getDisplayName() {
        return "Remove catch for a checked exception if the try block does not throw that exception";
    }

    @Override
    public String getDescription() {
        return "A refactoring operation may result in a checked exception that is no longer thrown from a `try` block. This recipe will find and remove unnecessary catch blocks.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            private static final String JAVA_LANG_EXCEPTION = "java.lang.Exception";
            private static final String JAVA_LANG_ERROR = "java.lang.Error";
            private static final String JAVA_LANG_RUNTIME_EXCEPTION = "java.lang.RuntimeException";
            private static final String JAVA_LANG_THROWABLE = "java.lang.Throwable";

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                return b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                    if (statement instanceof J.Try) {
                        // if a try has no catches, no finally, and no resources get rid of it and merge its statements into the current block
                        J.Try aTry = (J.Try) statement;
                        if (aTry.getCatches().isEmpty() && aTry.getResources() == null && aTry.getFinally() == null) {
                            return ListUtils.map(aTry.getBody().getStatements(), tryStat -> autoFormat(tryStat, ctx, getCursor()));
                        }
                    }
                    return statement;
                }));
            }

            @Override
            public J.Try visitTry(J.Try tryable, ExecutionContext ctx) {
                J.Try t = super.visitTry(tryable, ctx);

                if (t.getResources() != null) {
                    // Hard to determine if `close()` might throw any exceptions, so do not make any changes for now
                    return t;
                }

                List<JavaType> thrownExceptions = new ArrayList<>();
                AtomicBoolean missingTypeInformation = new AtomicBoolean(false);
                //Collect any checked exceptions thrown from the try block.
                new JavaIsoVisitor<Integer>() {
                    @Override
                    public NewClass visitNewClass(NewClass newClass, Integer integer) {
                        JavaType.Method methodType = newClass.getMethodType();
                        if (methodType == null) {
                            //Do not make any changes if there is missing type information.
                            missingTypeInformation.set(true);
                        } else {
                            thrownExceptions.addAll(methodType.getThrownExceptions());
                        }
                        return super.visitNewClass(newClass, integer);
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer integer) {
                        JavaType.Method methodType = method.getMethodType();
                        if (methodType == null) {
                            //Do not make any changes if there is missing type information.
                            missingTypeInformation.set(true);
                        } else {
                            thrownExceptions.addAll(methodType.getThrownExceptions());
                        }
                        return super.visitMethodInvocation(method, integer);
                    }

                    @Override
                    public J.Throw visitThrow(J.Throw thrown, Integer integer) {
                        JavaType type = thrown.getException().getType();
                        if (type == null) {
                            //Do not make any changes if there is missing type information.
                            missingTypeInformation.set(true);
                        } else {
                            thrownExceptions.add(type);
                        }
                        return super.visitThrow(thrown, integer);
                    }
                }.visit(t.getBody(), 0);

                //If there is any missing type information, it is not safe to make any transformations.
                if (missingTypeInformation.get()) {
                    return t;
                }

                Set<JavaType> unnecessaryTypes = getUnnecessaryTypes(t, thrownExceptions);
                if (unnecessaryTypes.isEmpty()) {
                    return t;
                }

                for (JavaType type : unnecessaryTypes) {
                    maybeRemoveImport(TypeUtils.asFullyQualified(type));
                }

                //For any checked exceptions being caught, if the exception is not thrown, remove the catch block.
                return t.withCatches(ListUtils.map(t.getCatches(), (i, aCatch) -> {
                    J.ControlParentheses<J.VariableDeclarations> parameter = aCatch.getParameter();
                    TypeTree typeExpression = aCatch.getParameter().getTree().getTypeExpression();
                    if (typeExpression instanceof J.MultiCatch) {
                        J.MultiCatch multiCatch = (J.MultiCatch) typeExpression;
                        List<NameTree> alternatives = ListUtils.map(multiCatch.getAlternatives(), typeTree ->
                                typeTree != null && unnecessaryTypes.contains(typeTree.getType()) ? null : typeTree
                        );
                        if (alternatives.isEmpty()) {
                            return null;
                        }
                        List<NameTree> leftTrimmed = ListUtils.mapFirst(alternatives, first -> first.withPrefix(multiCatch.getAlternatives().get(0).getPrefix()));
                        J.MultiCatch.Padding padding = multiCatch.withAlternatives(leftTrimmed).getPadding();
                        List<JRightPadded<NameTree>> rightTrimmed = ListUtils.mapLast(padding.getAlternatives(), last -> last.withAfter(Space.EMPTY));
                        return aCatch.withParameter(aCatch.getParameter().withTree(
                                aCatch.getParameter().getTree().withTypeExpression(padding.withAlternatives(rightTrimmed))));
                    }
                    if (unnecessaryTypes.contains(parameter.getType())) {
                        return null;
                    }
                    return aCatch;
                }));
            }

            /**
             * Retrieves a set of unique checked exception types declared within the catch blocks
             * of a given {@link J.Try} statement. This method supports both single and multi-catch
             * declarations.
             *
             * @param aTry The {@link J.Try} statement to analyze.
             * @return A {@link Set} of {@link JavaType} instances representing the caught checked exceptions.
             * An empty set is returned if no checked exceptions are found.
             */
            private Set<JavaType> getUnnecessaryTypes(J.Try aTry, Collection<JavaType> thrownExceptions) {
                Set<JavaType> caughtExceptions = new HashSet<>();

                for (J.Try.Catch c : aTry.getCatches()) {
                    JavaType type = c.getParameter().getType();
                    if (type == null) {
                        continue;
                    }

                    if (type instanceof JavaType.MultiCatch) {
                        for (JavaType throwable : ((JavaType.MultiCatch) type).getThrowableTypes()) {
                            if (isCheckedException(throwable) || isGenericTypeRemovableByOption(throwable)) {
                                caughtExceptions.add(throwable);
                            }
                        }
                    } else { // Single catch
                        if (isCheckedException(type) || isGenericTypeRemovableByOption(type)) {
                            caughtExceptions.add(c.getParameter().getType());
                        }
                    }
                }

                // Filter out caught exceptions that are necessary
                Set<JavaType> unnecessaryExceptions = new HashSet<>(caughtExceptions);
                unnecessaryExceptions.removeAll(thrownExceptions);

                // Also filter out caught exceptions that are subtypes of thrown exceptions
                // For example, if IOException is thrown, don't remove catch for ZipException
                Set<JavaType> toKeep = new HashSet<>();
                for (JavaType caughtException : unnecessaryExceptions) {
                    if (isGenericTypeRemovableByOption(caughtException)) {
                        continue;
                    }
                    for (JavaType thrownException : thrownExceptions) {
                        if (TypeUtils.isAssignableTo(thrownException, caughtException)
                                || TypeUtils.isAssignableTo(caughtException, thrownException)) {
                            toKeep.add(caughtException);
                            break;
                        }
                    }
                }
                unnecessaryExceptions.removeAll(toKeep);

                return unnecessaryExceptions;
            }

            private boolean isGenericTypeRemovableByOption(JavaType type) {
                if (includeJavaLangException && TypeUtils.isOfClassType(type, JAVA_LANG_EXCEPTION)) {
                    return true;
                }
                return includeJavaLangThrowable && TypeUtils.isOfClassType(type, JAVA_LANG_THROWABLE);
            }

            /**
             * Determines if a given {@link JavaType} represents a checked exception.
             * A checked exception is a subclass of {@code java.lang.Throwable} that is not
             * a subclass of {@code java.lang.RuntimeException} or {@code java.lang.Error}.
             * <a href="https://docs.oracle.com/javase/specs/jls/se7/html/jls-11.html#:~:text=The%20checked%20exception%20classes%20are,and%20Error%20and%20its%20subclasses.">Source</a>
             *
             * @param type The {@link JavaType} to evaluate.
             * @return {@code true} if the type is a checked exception; {@code false} otherwise.
             */
            private boolean isCheckedException(JavaType type) {
                if (!(type instanceof JavaType.Class)) {
                    return false;
                }
                JavaType.Class exceptionClass = (JavaType.Class) type;
                return TypeUtils.isAssignableTo(JAVA_LANG_EXCEPTION, exceptionClass) &&
                        !TypeUtils.isAssignableTo(JAVA_LANG_RUNTIME_EXCEPTION, exceptionClass) &&
                        !TypeUtils.isAssignableTo(JAVA_LANG_ERROR, exceptionClass) &&
                        !TypeUtils.isOfClassType(exceptionClass, JAVA_LANG_EXCEPTION) &&
                        !TypeUtils.isOfClassType(exceptionClass, JAVA_LANG_THROWABLE);
            }
        };
    }
}
