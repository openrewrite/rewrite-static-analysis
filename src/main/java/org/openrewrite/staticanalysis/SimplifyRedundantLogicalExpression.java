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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class SimplifyRedundantLogicalExpression extends Recipe {

    final String displayName = "Identical expressions used with logical operators should be simplified";

    final String description = "When the same expression appears on both sides of `&&`, `||`, `&`, or `|`, " +
            "the result is always equal to that expression. For example, `x && x` is always " +
            "just `x`. This is typically a copy-paste error where one side should have been different.";

    final Set<String> tags = singleton("RSPEC-S1764");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary b = (J.Binary) super.visitBinary(binary, ctx);

                switch (b.getOperator()) {
                    case And:
                    case Or:
                    case BitAnd:
                    case BitOr:
                        if (SemanticallyEqual.areEqual(b.getLeft(), b.getRight())) {
                            return b.getLeft().withPrefix(b.getPrefix());
                        }
                        break;
                    default:
                        break;
                }
                return b;
            }
        };
    }
}
