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
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindCloneableWithoutClone extends Recipe {

    private static final String CLONEABLE = "java.lang.Cloneable";

    private static final String OBJECT = "java.lang.Object";

    private static final String MESSAGE = "`Cloneable` is a marker interface that promises a working `clone()`, but " +
            "this class inherits `Object.clone()`, which copies fields one by one. Override `clone()` and call " +
            "`super.clone()` from it.";

    String displayName = "Find `Cloneable` types that do not override `clone()`";

    String description = "Marks classes and records that are `java.lang.Cloneable`, directly or through a " +
            "supertype, and that would still call `Object.clone()`. `Cloneable` only tells `Object.clone()` not " +
            "to throw; it declares no method of its own, so the compiler cannot enforce the contract and the type " +
            "is left with a shallow field copy it never asked for. A type is not marked when it or an ancestor " +
            "declares `clone()`, since that override is what it would call.";

    Set<String> tags = singleton("RSPEC-S2157");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(30);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(CLONEABLE, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                // A record inherits `Object.clone()` like a class does; an enum's `clone()` is `final`, so
                // there is nothing it could override
                if ((cd.getKind() != J.ClassDeclaration.Kind.Type.Class &&
                        cd.getKind() != J.ClassDeclaration.Kind.Type.Record) ||
                        !TypeUtils.isAssignableTo(CLONEABLE, cd.getType())) {
                    return cd;
                }
                // The `clone()` this class would call is `Object`'s unless it or an ancestor declares one
                JavaType.Method clone = TypeUtils.findDeclaredMethod(cd.getType(), "clone", emptyList()).orElse(null);
                if (clone != null && !TypeUtils.isOfClassType(clone.getDeclaringType(), OBJECT)) {
                    return cd;
                }
                return cd.withName(SearchResult.found(cd.getName(), MESSAGE));
            }
        });
    }
}
