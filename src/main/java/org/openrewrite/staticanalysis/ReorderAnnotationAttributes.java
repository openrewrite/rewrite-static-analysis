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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReorderAnnotationAttributes extends Recipe {

    @Override
    public String getDisplayName() {
        return "Reorder annotation attributes alphabetically";
    }

    @Override
    public String getDescription() {
        return "Reorder annotation attributes to be alphabetical. " +
               "Positional arguments (those without explicit attribute names) are left in their original position.";
    }

    private static final Comparator<Expression> attributeComparator = (e1, e2) -> {
        if (e1 instanceof J.Assignment && e2 instanceof J.Assignment) {
            J.Assignment a1 = (J.Assignment) e1;
            J.Assignment a2 = (J.Assignment) e2;
            if (a1.getVariable() instanceof J.Identifier && a2.getVariable() instanceof J.Identifier) {
                String name1 = ((J.Identifier) a1.getVariable()).getSimpleName();
                String name2 = ((J.Identifier) a2.getVariable()).getSimpleName();
                return name1.compareTo(name2);
            }
        }
        return 0;
    };

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                List<Expression> arguments = a.getArguments();

                if (arguments == null || arguments.size() <= 1) {
                    return a;
                }

                // Separate positional arguments from named arguments
                List<Expression> positional = new ArrayList<>();
                List<Expression> named = new ArrayList<>();

                for (Expression arg : arguments) {
                    if (arg instanceof J.Assignment) {
                        named.add(arg);
                    } else {
                        positional.add(arg);
                    }
                }

                // If there are no named arguments or only one, no need to sort
                if (named.size() <= 1) {
                    return a;
                }

                // Sort named arguments alphabetically
                List<Expression> sortedNamed = new ArrayList<>(named);
                sortedNamed.sort(attributeComparator);

                // Check if the order actually changed
                if (sortedNamed.equals(named)) {
                    return a;
                }

                // Combine: positional arguments first, then sorted named arguments
                List<Expression> reordered = new ArrayList<>(positional);
                reordered.addAll(sortedNamed);

                // Preserve the original prefixes (spacing/formatting)
                List<Expression> finalArguments = ListUtils.map(reordered, (i, arg) ->
                    arg.withPrefix(arguments.get(i).getPrefix())
                );

                return a.withArguments(finalArguments);
            }
        };
    }
}
