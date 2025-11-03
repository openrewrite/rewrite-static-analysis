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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class OnlyCatchDeclaredExceptions extends Recipe {

    private static final String JAVA_LANG_EXCEPTION = "java.lang.Exception";

    @Override
    public String getDisplayName() {
        return "Replace `catch(Exception)` with specific declared exceptions thrown in the try block";
    }

    @Override
    public String getDescription() {
        return "Replaces `catch(Exception e)` blocks with a multi-catch block " +
                "(`catch (SpecificException1 | SpecificException2 e)`) containing only the exceptions declared " +
                "thrown by method or constructor invocations within the `try` block that are not already caught " +
                "by more specific `catch` clauses.";
    }

    @Override
    public Set<String> getTags() {
        return new HashSet<>(Arrays.asList("CWE-396", "RSPEC-S2221"));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try visitTry(J.Try aTry, ExecutionContext ctx) {
                J.Try t = super.visitTry(aTry, ctx);
                return t.withCatches(ListUtils.map(t.getCatches(), c -> {
                    if (isGenericCatch(c)) {
                        // Find declared thrown exceptions that are not already specifically caught
                        Set<JavaType> declaredThrown = getDeclaredThrownExceptions(t);
                        declaredThrown.removeAll(getCaughtExceptions(t));
                        if (!declaredThrown.isEmpty() && !containsGenericTypeVariable(declaredThrown)) {
                            return multiCatchWithDeclaredExceptions(c, declaredThrown);
                        }
                    }
                    return c;
                }));
            }

            private boolean isGenericCatch(J.Try.Catch aCatch) {
                FullyQualified fq = TypeUtils.asFullyQualified(aCatch.getParameter().getType());
                if (fq != null) {
                    String fqn = fq.getFullyQualifiedName();
                    return TypeUtils.fullyQualifiedNamesAreEqual(JAVA_LANG_EXCEPTION, fqn);
                }
                return false;
            }

            private boolean containsGenericTypeVariable(Set<JavaType> types) {
                for (JavaType type : types) {
                    if (type instanceof JavaType.GenericTypeVariable) {
                        return true;
                    }
                }
                return false;
            }

            private Set<JavaType> getCaughtExceptions(J.Try aTry) {
                Set<JavaType> caughtExceptions = new HashSet<>();
                for (J.Try.Catch c : aTry.getCatches()) {
                    JavaType type = c.getParameter().getType();
                    if (type instanceof JavaType.MultiCatch) {
                        caughtExceptions.addAll(((JavaType.MultiCatch) type).getThrowableTypes());
                    } else if (type != null) {
                        caughtExceptions.add(type);
                    }
                }
                return caughtExceptions;
            }

            private Set<JavaType> getDeclaredThrownExceptions(J.Try aTry) {
                return new JavaIsoVisitor<Set<JavaType>>() {
                    @Override
                    public @Nullable JavaType visitType(@Nullable JavaType javaType, Set<JavaType> javaTypes) {
                        if (javaType instanceof JavaType.Method) {
                            javaTypes.addAll(((JavaType.Method) javaType).getThrownExceptions());
                        }
                        return super.visitType(javaType, javaTypes);
                    }
                }.reduce(aTry.getBody(), new HashSet<>());
            }

            private J.Try.Catch multiCatchWithDeclaredExceptions(J.Try.Catch aCatch, Set<JavaType> thrownExceptions) {
                List<FullyQualified> fqs = thrownExceptions.stream()
                        .map(TypeUtils::asFullyQualified)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(FullyQualified::getClassName))
                        .collect(toList());
                String throwableTypes = fqs
                        .stream()
                        .map(FullyQualified::getClassName)
                        .collect(joining("|"));
                String[] imports = fqs.stream().map(FullyQualified::getFullyQualifiedName).toArray(String[]::new);
                for (String s : imports) {
                    maybeAddImport(s, false);
                }

                J.Try surroundingTry = getCursor().firstEnclosing(J.Try.class);
                assert surroundingTry != null;

                // Preserve the existing variable name from the original generic catch block
                String variableName = aCatch.getParameter().getTree().getVariables().get(0).getSimpleName();
                J.Try generatedTry = JavaTemplate.builder(String.format("try {} catch (%s %s) {}", throwableTypes, variableName))
                        .imports(imports)
                        .build()
                        .apply(new Cursor(getCursor(), surroundingTry), surroundingTry.getCoordinates().replace());
                return aCatch.withParameter(generatedTry.getCatches().get(0).getParameter());
            }
        };
        return Preconditions.check(new UsesType<>(JAVA_LANG_EXCEPTION, false), visitor);
    }
}
