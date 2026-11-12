/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.staticanalysis.format;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.tree.ParseError;

import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

/**
 * Parsers to read the formatters' output with, kept for the next file since making one costs more than the parse
 * of a typical file: for Java, reusing one makes the parse about three times as fast. A parser is reset between
 * files, and dropped after {@link #MAX_USES} of them, so that what it caches does not grow without bound.
 */
final class ParserPool {
    private static final int MAX_POOLED = 4;
    private static final int MAX_USES = 500;

    private final Supplier<Parser> factory;
    private final Deque<Pooled> pool = new ConcurrentLinkedDeque<>();

    ParserPool(Supplier<Parser> factory) {
        this.factory = factory;
    }

    /**
     * Parses the input with a pooled parser. When that gives a {@link ParseError}, the parser is dropped, since a
     * failure may have left it in a bad state, and the input is parsed once more with a new one.
     */
    @Nullable
    SourceFile parse(Parser.Input input, ExecutionContext ctx) {
        Pooled pooled = pool.pollFirst();
        if (pooled == null) {
            return parse(new Pooled(factory.get()), input, ctx);
        }
        SourceFile parsed = parse(pooled, input, ctx);
        if (parsed instanceof ParseError) {
            parsed = parse(new Pooled(factory.get()), input, ctx);
        }
        return parsed;
    }

    private @Nullable SourceFile parse(Pooled pooled, Parser.Input input, ExecutionContext ctx) {
        boolean reusable = false;
        try {
            pooled.parser.reset();
            SourceFile parsed = pooled.parser.parseInputs(Collections.singletonList(input), null, ctx)
                    .findFirst()
                    .orElse(null);
            reusable = !(parsed instanceof ParseError);
            return parsed;
        } finally {
            if (reusable && ++pooled.uses < MAX_USES && pool.size() < MAX_POOLED) {
                pooled.parser.reset();
                pool.offerFirst(pooled);
            }
        }
    }

    private static final class Pooled {
        final Parser parser;
        int uses;

        Pooled(Parser parser) {
            this.parser = parser;
        }
    }
}
