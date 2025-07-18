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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;
import java.util.stream.Collectors;


public class SpecifyGenericExceptionCatches extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace `catch(Exception)` with specific exceptions thrown in the try block";
    }

    @Override
    public String getDescription() {
        return "Replaces `catch(Exception e)` blocks with a multi-catch block " +
                "(`catch (SpecificException1 | SpecificException2 e)`) containing only the checked exceptions " +
                "explicitly thrown by method or constructor invocations within the `try` block that are not " +
                "already caught by more specific `catch` clauses. If no checked exceptions are found that " +
                "require catching, the generic `catch` block is removed.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList("CWE-396", "RSPEC-S2221")));
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private static final String JAVA_LANG_EXCEPTION = "java.lang.Exception";
            private static final String MULTI_CATCH_SEPARATOR = "|";
            private static final String TRY_CATCH_TEMPLATE = "try {} catch (%s %s) {}";

            @Override
            public J.Try visitTry(J.Try aTry, ExecutionContext ctx) {
                J.Try t = super.visitTry(aTry, ctx);

                if (hasGenericCatch(t)) {
                    Set<JavaType> caughtExceptions = getCaughtExceptions(t);
                    Set<JavaType> thrownExceptions = getDeclaredThrownExceptions(t);
                    thrownExceptions.removeAll(caughtExceptions); // Remove exceptions that are already specifically caught

                    if (!thrownExceptions.isEmpty()) {
                        return t.withCatches(ListUtils.map(t.getCatches(), c ->
                            updateCatchIfGeneric(c, thrownExceptions)));
                    }
                }

                return t;
            }

            private boolean isGenericCatch(J.Try.Catch aCatch) {
                FullyQualified fq = TypeUtils.asFullyQualified(aCatch.getParameter().getType());
                if (fq != null) {
                    String fqn = fq.getFullyQualifiedName();
                    return TypeUtils.fullyQualifiedNamesAreEqual(JAVA_LANG_EXCEPTION, fqn);
                }
                return false;
            }

            private boolean hasGenericCatch(J.Try aTry) {
                for (J.Try.Catch c : aTry.getCatches()) {
                    if (isGenericCatch(c)) {
                        return true;
                    }
                }
                return false;
            }

            private Set<JavaType> getCaughtExceptions(J.Try aTry) {
                Set<JavaType> caughtExceptions = new HashSet<>();
                for (J.Try.Catch c : aTry.getCatches()) {
                    if (c.getParameter().getType() != null) {
                        caughtExceptions.add(c.getParameter().getType());
                    }
                }
                return caughtExceptions;
            }

            /**
             * Collects all checked exceptions that are explicitly thrown by method invocations
             * and constructor calls within the try block.
             *
             * @param aTry the try block to analyze
             * @return a set of exception types that may be thrown by code in the try block
             */
            private Set<JavaType> getDeclaredThrownExceptions(J.Try aTry) {
                return new JavaIsoVisitor<Set<JavaType>>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass nc, Set<JavaType> set) {
                        if (nc.getConstructorType() != null) {
                            set.addAll(nc.getConstructorType().getThrownExceptions());
                        }
                        return super.visitNewClass(nc, set);
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, Set<JavaType> set) {
                        if (mi.getMethodType() != null) {
                            set.addAll(mi.getMethodType().getThrownExceptions());
                        }
                        return super.visitMethodInvocation(mi, set);
                    }
                }.reduce(aTry.getBody(), new HashSet<>());
            }

            /**
             * Updates a generic catch block (catching java.lang.Exception) to catch only the specific
             * exception types that are actually thrown within the try block.
             *
             * <p>This method transforms generic catch blocks like:
             * <pre>{@code
             * catch (Exception e) { ... }
             * }</pre>
             *
             * into specific single or multi-catch blocks like:
             * <pre>{@code
             * catch (IOException e) { ... }
             * // or
             * catch (IOException | SQLException e) { ... }
             * }</pre>
             *
             * @param aCatch the catch block to potentially update
             * @param thrownExceptions the set of specific exception types thrown in the try block
             *                        that are not already caught by other catch clauses
             * @return the original catch block if it doesn't catch java.lang.Exception,
             *         otherwise a new catch block with specific exception types
             */
            private J.Try.Catch updateCatchIfGeneric(J.Try.Catch aCatch, Set<JavaType> thrownExceptions) {
                if (!isGenericCatch(aCatch)) {
                    return aCatch;
                }

                // Preserve the existing variable name from the original generic catch block
                String variableName = aCatch.getParameter().getTree().getVariables().get(0).getSimpleName();

                String throwableTypes = thrownExceptions.stream()
                        .map(TypeUtils::asFullyQualified)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(FullyQualified::getClassName))
                        .map(FullyQualified::getFullyQualifiedName)
                        .collect(Collectors.joining(MULTI_CATCH_SEPARATOR));

                J.Try aTry = getCursor().firstEnclosing(J.Try.class);
                assert aTry != null;

                J.Try tempTry = JavaTemplate.builder(String.format(TRY_CATCH_TEMPLATE, throwableTypes, variableName))
                        .contextSensitive()
                        .build()
                        .apply(
                                new Cursor(getCursor(), aTry),
                                aTry.getCoordinates().replace()
                        );

                J.ControlParentheses<J.VariableDeclarations> cp = tempTry.getCatches().get(0).getParameter();
                doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(cp.getTree()));
                return aCatch.withParameter(cp);
            }
        };
    }

}
