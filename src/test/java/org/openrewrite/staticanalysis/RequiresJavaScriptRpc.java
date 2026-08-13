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
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that parses JavaScript or TypeScript, which spawns an out-of-process RPC server from the
 * {@code @openrewrite/rewrite} npm package, pinned to the exact version of the resolved
 * {@code rewrite-javascript} jar.
 * <p>
 * The {@code warmJavaScriptRpcCache} build task installs that package up front and sets
 * {@code javaScriptRpcAvailable} to whether it succeeded. It does not when npx is absent, or while a
 * published {@code rewrite-javascript} snapshot is still waiting on its matching npm release; the RPC
 * process then has nothing to run, so these tests skip instead of failing over a gap upstream.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@DisabledIfSystemProperty(named = "javaScriptRpcAvailable", matches = "false",
        disabledReason = "The @openrewrite/rewrite npm package matching the resolved rewrite-javascript version is not installed")
public @interface RequiresJavaScriptRpc {
}
