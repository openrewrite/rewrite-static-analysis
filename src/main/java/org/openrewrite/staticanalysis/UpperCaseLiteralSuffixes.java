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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import static java.util.Collections.singleton;

public class UpperCaseLiteralSuffixes extends Recipe {
    @Getter
    final String displayName = "Upper case literal suffixes";

    @Getter
    final String description = "Using upper case literal suffixes for declaring literals is less ambiguous, e.g., `1l` versus `1L`. " +
            "A lowercase `l` is easily mistaken for the digit `1` in many fonts, which can lead to incorrect assumptions about the value. " +
            "Hexadecimal digits are upper cased as well, e.g., `0Xabc` versus `0xABC`, " +
            "such that they stand out from the lower case `0x` prefix and `p` exponent.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S818");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new JavaFileChecker<>(),
                        Preconditions.or(
                                new UsesType<>("int", false),
                                new UsesType<>("java.lang.Integer", false),
                                new UsesType<>("long", false),
                                new UsesType<>("java.lang.Long", false),
                                new UsesType<>("double", false),
                                new UsesType<>("java.lang.Double", false),
                                new UsesType<>("float", false),
                                new UsesType<>("java.lang.Float", false)
                        )
                ), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                        String valueSource = literal.getValueSource();
                        if (valueSource == null || valueSource.length() < 2 ||
                            (literal.getType() != JavaType.Primitive.Int &&
                             literal.getType() != JavaType.Primitive.Long &&
                             literal.getType() != JavaType.Primitive.Double &&
                             literal.getType() != JavaType.Primitive.Float)) {
                            return literal;
                        }

                        String upperValueSource;
                        if (valueSource.length() > 2 && valueSource.charAt(0) == '0' &&
                            (valueSource.charAt(1) == 'x' || valueSource.charAt(1) == 'X')) {
                            upperValueSource = "0x" + valueSource.substring(2).toUpperCase(Locale.ROOT).replace('P', 'p');
                        } else {
                            upperValueSource = valueSource.substring(0, valueSource.length() - 1) +
                                               valueSource.substring(valueSource.length() - 1).toUpperCase(Locale.ROOT);
                        }
                        if (upperValueSource.equals(valueSource)) {
                            return literal;
                        }
                        return literal.withValueSource(upperValueSource);
                    }
                });
    }
}
