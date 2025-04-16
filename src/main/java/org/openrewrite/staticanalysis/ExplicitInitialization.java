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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.ExplicitInitializationStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class ExplicitInitialization extends Recipe {

    @Override
    public String getDisplayName() {
        return "Explicit initialization";
    }

    @Override
    public String getDescription() {
        return "Checks if any class or object member is explicitly initialized to default for its type value:\n" +
               " - `null` for object references\n" +
               " - zero for numeric types and `char`\n" +
               " - and `false` for `boolean`\n" +
               "Removes explicit initializations where they aren't necessary.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S3052");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new KotlinFileChecker<>()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                    ExplicitInitializationStyle style = cu.getStyle(ExplicitInitializationStyle.class);
                    if (style == null) {
                        style = Checkstyle.explicitInitialization();
                    }
                    return new ExplicitInitializationVisitor<>(style).visit(cu, ctx);
                }
                return (J) tree;
            }
        });
    }
}
