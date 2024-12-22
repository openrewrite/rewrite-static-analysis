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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.Set;

public class NewStringBuilderBufferWithCharArgument extends Recipe {

    public static final String STRING_BUILDER = "java.lang.StringBuilder";
    public static final String STRING_BUFFER = "java.lang.StringBuffer";

    @Override
    public String getDisplayName() {
        return "Change `StringBuilder` and `StringBuffer` character constructor argument to `String`";
    }

    @Override
    public String getDescription() {
        return "Instantiating a `StringBuilder` or a `StringBuffer` with a `Character` results in the `int` representation of the character being used for the initial size.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1317");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> condition = Preconditions.or(new UsesType<>(STRING_BUILDER, true), new UsesType<>(STRING_BUFFER, true));
        return Preconditions.check(condition, new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);
                if ((TypeUtils.isOfClassType(nc.getType(), STRING_BUILDER) || TypeUtils.isOfClassType(nc.getType(), STRING_BUFFER))) {
                    nc.getArguments();
                    if (nc.getArguments().get(0).getType() == JavaType.Primitive.Char) {
                        nc = nc.withArguments(ListUtils.mapFirst(nc.getArguments(), arg -> {
                            if (arg instanceof J.Literal) {
                                J.Literal l = (J.Literal) arg;
                                l = l.withType(JavaType.buildType("String"));
                                if (l.getValueSource() != null) {
                                    l = l.withValueSource(l.getValueSource().replace("'", "\""));
                                }
                                return l;
                            } else {
                                return JavaTemplate.builder("String.valueOf(#{any()})").build()
                                        .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace(), arg);
                            }
                        }));
                    }
                }
                return nc;
            }
        });
    }
}
