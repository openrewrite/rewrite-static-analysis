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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static java.util.Collections.singletonList;

public class RedundantFileCreation extends Recipe {
    @Getter
    final String displayName = "Redundant `File` creation";

    @Getter
    final String description = "Removes the unnecessary intermediate `File` from `new FileInputStream(new File(name))`.";

    private static final MethodMatcher FILE_INPUT_STREAM_FROM_FILE = new MethodMatcher("java.io.FileInputStream <constructor>(java.io.File)");
    private static final MethodMatcher FILE_FROM_STRING = new MethodMatcher("java.io.File <constructor>(java.lang.String)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(FILE_INPUT_STREAM_FROM_FILE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (!FILE_INPUT_STREAM_FROM_FILE.matches(n) || !FILE_FROM_STRING.matches(n.getArguments().get(0))) {
                    return n;
                }
                J.NewClass file = (J.NewClass) n.getArguments().get(0);
                if (file.getBody() == null) {
                    maybeRemoveImport("java.io.File");
                    JavaType.Method constructorType = n.getConstructorType();
                    return n
                            .withArguments(file.getArguments())
                            .withConstructorType(constructorType == null ? null :
                                    constructorType.withParameterTypes(singletonList(JavaType.Primitive.String)));
                }
                return n;
            }
        });
    }
}
