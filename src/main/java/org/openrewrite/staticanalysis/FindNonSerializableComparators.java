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
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindNonSerializableComparators extends Recipe {

    private static final String COMPARATOR = "java.util.Comparator";
    private static final String SERIALIZABLE = "java.io.Serializable";

    private static final String MESSAGE = "A `Comparator` that is not `Serializable` makes every `TreeMap`, " +
            "`TreeSet` or `PriorityQueue` built with it unserializable. Implement `Serializable` as well.";

    String displayName = "Find `Comparator` implementations that are not `Serializable`";

    String description = "Marks classes and records that implement `java.util.Comparator` without also being " +
            "`Serializable`. An ordered collection keeps a reference to its comparator, so a non-serializable comparator makes an " +
            "otherwise serializable `TreeMap`, `TreeSet` or `PriorityQueue` fail to serialize. Anonymous classes and " +
            "lambdas are not marked.";

    Set<String> tags = singleton("RSPEC-S2063");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(COMPARATOR, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                // A record is not `Serializable` either, unlike an enum, so it is reported the same way
                if ((cd.getKind() == J.ClassDeclaration.Kind.Type.Class ||
                        cd.getKind() == J.ClassDeclaration.Kind.Type.Record) &&
                        TypeUtils.isAssignableTo(COMPARATOR, cd.getType()) &&
                        !TypeUtils.isAssignableTo(SERIALIZABLE, cd.getType())) {
                    return cd.withName(SearchResult.found(cd.getName(), MESSAGE));
                }
                return cd;
            }
        });
    }
}
