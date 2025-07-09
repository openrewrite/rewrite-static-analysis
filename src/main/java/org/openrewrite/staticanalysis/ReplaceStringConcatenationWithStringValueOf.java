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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class ReplaceStringConcatenationWithStringValueOf extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace String concatenation with `String.valueOf()`";
    }

    @Override
    public String getDescription() {
        return "Replace inefficient string concatenation patterns like `\"\" + ...` with `String.valueOf(...)`. " +
                "This improves code readability and may have minor performance benefits.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1153");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                if (J.Literal.isLiteralValue(binary.getLeft(), "") &&
                        binary.getOperator() == J.Binary.Type.Addition &&
                        !TypeUtils.isString(binary.getRight().getType()) &&
                        !J.Literal.isLiteralValue(binary.getRight(), null)) {
                    return JavaTemplate.builder("String.valueOf(#{any()})")
                            .build()
                            .apply(getCursor(), 
                                   binary.getCoordinates().replace(),
                                   binary.getRight())
                            .withPrefix(binary.getPrefix());
                }
                return super.visitBinary(binary, ctx);
            }
        };
    }
}
