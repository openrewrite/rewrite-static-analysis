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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class StaticAccessViaInstance extends Recipe {

    String displayName = "Static members should be accessed via the class name";
    String description = "Accessing static fields or calling static methods on an instance reference is misleading. " +
            "Static members should be accessed using the declaring class name instead.";
    Set<String> tags = new LinkedHashSet<>(Arrays.asList("RSPEC-S2209", "RSPEC-S3252"));
    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                JavaType.Variable fieldType = fa.getName().getFieldType();
                if (fieldType == null || !fieldType.hasFlags(Flag.Static)) {
                    return fa;
                }
                Expression select = fa.getTarget();
                if (!isInstanceAccess(select)) {
                    return fa;
                }
                JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(fieldType.getOwner());
                if (declaringType == null) {
                    return fa;
                }
                maybeAddImport(declaringType.getFullyQualifiedName());
                return fa.withTarget(buildClassReference(select, declaringType));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = mi.getMethodType();
                if (methodType == null || !methodType.hasFlags(Flag.Static)) {
                    return mi;
                }
                Expression select = mi.getSelect();
                if (select == null || !isInstanceAccess(select)) {
                    return mi;
                }
                JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(methodType.getDeclaringType());
                if (declaringType == null) {
                    return mi;
                }
                maybeAddImport(declaringType.getFullyQualifiedName());
                return mi.withSelect(buildClassReference(select, declaringType));
            }

            private boolean isInstanceAccess(Expression select) {
                if (select instanceof J.Identifier) {
                    J.Identifier ident = (J.Identifier) select;
                    if ("this".equals(ident.getSimpleName()) || "super".equals(ident.getSimpleName())) {
                        return true;
                    }
                    return ident.getFieldType() != null;
                }
                if (select instanceof J.FieldAccess) {
                    J.FieldAccess fa = (J.FieldAccess) select;
                    // Only safe when the entire chain is side-effect-free
                    return fa.getName().getFieldType() != null && isSideEffectFree(fa.getTarget());
                }
                // Skip method invocations, new expressions, etc. to preserve side effects
                return false;
            }

            private boolean isSideEffectFree(Expression expr) {
                if (expr instanceof J.Identifier) {
                    return true;
                }
                if (expr instanceof J.FieldAccess) {
                    return isSideEffectFree(((J.FieldAccess) expr).getTarget());
                }
                return false;
            }

            private Expression buildClassReference(Expression select, JavaType.FullyQualified declaringType) {
                String className = declaringType.getClassName();
                if (!className.contains(".")) {
                    return new J.Identifier(
                            Tree.randomId(),
                            select.getPrefix(),
                            Markers.EMPTY,
                            emptyList(),
                            className,
                            declaringType,
                            null
                    );
                }
                // Nested class: build Outer.Inner as a J.FieldAccess chain
                String[] parts = className.split("\\.");
                Expression result = new J.Identifier(
                        Tree.randomId(),
                        select.getPrefix(),
                        Markers.EMPTY,
                        emptyList(),
                        parts[0],
                        null,
                        null
                );
                for (int i = 1; i < parts.length; i++) {
                    JavaType type = i == parts.length - 1 ? declaringType : null;
                    result = new J.FieldAccess(
                            Tree.randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            result,
                            new JLeftPadded<>(
                                    Space.EMPTY,
                                    new J.Identifier(
                                            Tree.randomId(),
                                            Space.EMPTY,
                                            Markers.EMPTY,
                                            emptyList(),
                                            parts[i],
                                            type,
                                            null
                                    ),
                                    Markers.EMPTY
                            ),
                            type
                    );
                }
                return result;
            }
        });
    }
}
