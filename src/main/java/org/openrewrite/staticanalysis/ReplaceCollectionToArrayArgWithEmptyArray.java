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

import org.openrewrite.*;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.search.UsesInvocation;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class ReplaceCollectionToArrayArgWithEmptyArray extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use Empty Array for `Collection.toArray()`";
    }

    @Override
    public String getDescription() {
        return "Changes new array creation with `Collection#toArray(T[])` to use an empty array argument, which is better for performance.\n" +
                "\n" +
                "According to the `Collection#toArray(T[])` documentation:\n" +
                "\n" +
                "> If the collection fits in the specified array, it is returned therein.\n" +
                "\n" +
                "However, although it's not intuitive, " +
                "allocating a right-sized array ahead of time to pass to the API appears to be [generally worse for performance](https://shipilev.net/blog/2016/arrays-wisdom-ancients/#_conclusion) " +
                "according to benchmarking and JVM developers due to a number of implementation details in both Java and the virtual machine.\n" +
                "\n" +
                "H2 achieved significant performance gains by [switching to empty arrays instead pre-sized ones](https://github.com/h2database/h2database/issues/311).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesInvocation<>(ReplaceCollectionToArrayArgWithEmptyArrayVisitor.COLLECTION_TO_ARRAY),
                new ReplaceCollectionToArrayArgWithEmptyArrayVisitor<>()
        );
    }

    private static class ReplaceCollectionToArrayArgWithEmptyArrayVisitor<P> extends JavaIsoVisitor<P> {
        private static final InvocationMatcher COLLECTION_TO_ARRAY =
                InvocationMatcher.fromMethodMatcher("java.util.Collection toArray(..)");

        @Override
        public J.NewArray visitNewArray(J.NewArray newArray, P p) {
            if (COLLECTION_TO_ARRAY.advanced().isFirstArgument(getCursor()) && newArray.getDimensions().size() == 1) {
                J.NewArray newArrayZero = newArray.withDimensions(ListUtils.mapFirst(newArray.getDimensions(), d -> {
                    if (d.getIndex() instanceof J.Literal && Integer.valueOf(0).equals(((J.Literal) d.getIndex()).getValue())) {
                        return d;
                    }
                    return d.withIndex(new J.Literal(
                            Tree.randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            0,
                            "0",
                            emptyList(),
                            (JavaType.Primitive) requireNonNull(d.getIndex().getType())
                    ));
                }));
                return maybeAutoFormat(newArray, newArrayZero, p);
            }
            return super.visitNewArray(newArray, p);
        }
    }
}
