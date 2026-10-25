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

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReplaceStringBufferWithStringBuilder extends ReplaceLegacyCollection {

    String displayName = "Replace `java.lang.StringBuffer` with `java.lang.StringBuilder`";

    String description = "`StringBuffer` synchronizes every operation, which adds overhead in the common " +
            "single-threaded case. `StringBuilder` exposes the identical API without the synchronization. This " +
            "recipe replaces a local `StringBuffer` with a `StringBuilder` when data flow analysis can prove the " +
            "`StringBuffer` never escapes its method (it is not returned, assigned to a field, or passed as an " +
            "argument), so no other thread can observe it and the synchronization is redundant. Fields and " +
            "escaping variables are left untouched.";

    Set<String> tags = singleton("RSPEC-S1149");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    String getLegacyType() {
        return "java.lang.StringBuffer";
    }

    @Override
    String getReplacementType() {
        return "java.lang.StringBuilder";
    }
}
