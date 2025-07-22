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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypedTree;

import java.time.Duration;
import java.util.List;

public class UseForEachLoop extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use for-each loops instead of manual indexing";
    }

    @Override
    public String getDescription() {
        return "Replace traditional for loops that iterate over collections or arrays with enhanced for-each loops for improved readability.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitForLoop(J.ForLoop forLoop, ExecutionContext ctx) {
                J.ForLoop loop = (J.ForLoop) super.visitForLoop(forLoop, ctx);

                // For now, return unchanged - we'll implement this step by step
                return loop;
            }

        };
    }
}