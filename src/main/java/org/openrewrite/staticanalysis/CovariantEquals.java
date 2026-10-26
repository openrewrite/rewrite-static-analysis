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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Incubating(since = "7.0.0")
public class CovariantEquals extends Recipe {

    private static final MethodMatcher EQUALS_MATCHER = new MethodMatcher("* equals(..)");
    private static final MethodMatcher EQUALS_OBJECT_MATCHER = new MethodMatcher("* equals(java.lang.Object)");
    private static final MethodMatcher HASH_CODE_MATCHER = new MethodMatcher("* hashCode()");

    @Getter
    final String displayName = "Covariant equals";

    @Getter
    final String description = "Checks that classes and records which define a covariant " +
            "`equals()` method also override method `equals(Object)`. Covariant " +
            "`equals()` means a method that is similar to `equals(Object)`, but with a " +
            "covariant parameter type (any subtype of `Object`). Without a proper " +
            "`equals(Object)` override, collections and other framework code that rely " +
            "on the standard signature will silently use `Object.equals`, leading to " +
            "incorrect behavior. Adds an `equals(Object)` that delegates to the covariant " +
            "`equals()`, so overrides and callers of the covariant method keep compiling. " +
            "Classes that do not also declare `hashCode()` are left unchanged.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1201");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> conditions = Preconditions.and(
                new DeclaresMethod<>(EQUALS_MATCHER),
                Preconditions.not(new DeclaresMethod<>(EQUALS_OBJECT_MATCHER)),
                Preconditions.not(new CSharpFileChecker<>()),
                Preconditions.not(new KotlinFileChecker<>())
        );
        return Preconditions.check(conditions, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = cd.getType();
                if (type == null || cd.getKind() == J.ClassDeclaration.Kind.Type.Interface) {
                    return cd;
                }

                // Value-based equality without a matching `hashCode()` would break hash-based collections
                MethodMatcher covariantEquals = new MethodMatcher(type.getFullyQualifiedName() + " equals(" + type.getFullyQualifiedName() + ")");
                J.MethodDeclaration covariant = null;
                boolean declaresHashCode = false;
                for (Statement statement : cd.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration) {
                        J.MethodDeclaration md = (J.MethodDeclaration) statement;
                        if (EQUALS_OBJECT_MATCHER.matches(md.getMethodType())) {
                            return cd;
                        }
                        if (HASH_CODE_MATCHER.matches(md.getMethodType())) {
                            declaresHashCode = true;
                        } else if (covariantEquals.matches(md.getMethodType()) && md.hasModifier(J.Modifier.Type.Public) &&
                                !md.hasModifier(J.Modifier.Type.Static) && md.getMethodType().getReturnType() == JavaType.Primitive.Boolean) {
                            covariant = md;
                        }
                    }
                }
                if (covariant == null || !declaresHashCode) {
                    return cd;
                }

                return JavaTemplate.builder("@Override\npublic boolean equals(Object obj) {\n" +
                                "    return obj instanceof #{} && equals((#{}) obj);\n" +
                                "}")
                        .contextSensitive()
                        .build()
                        .apply(updateCursor(cd), covariant.getCoordinates().after(), cd.getSimpleName(), cd.getSimpleName());
            }
        });
    }
}
