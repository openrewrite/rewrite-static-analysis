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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class RedundantFileCreation extends Recipe {
    @Getter
    final String displayName = "Redundant file creation";

    @Getter
    final String description = "Remove unnecessary intermediate creations of files.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("java.io.FileInputStream", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (n.getClazz() != null && TypeUtils.isOfClassType(n.getClazz().getType(),
                        "java.io.FileInputStream")) {
                    n = n.withArguments(ListUtils.map(n.getArguments(), arg -> {
                        if (arg instanceof J.NewClass) {
                            J.NewClass newClassArg = (J.NewClass) arg;
                            if (newClassArg.getClazz() != null &&
                                TypeUtils.isOfClassType(newClassArg.getClazz().getType(), "java.io.File")) {
                                if (newClassArg.getArguments().size() == 1 && !(newClassArg.getArguments().get(0) instanceof J.Empty)) {
                                    maybeRemoveImport("java.io.File");
                                    return newClassArg.getArguments().get(0);
                                }
                            }
                        }
                        return arg;
                    }));
                }
                return n;
            }
        });
    }
}
