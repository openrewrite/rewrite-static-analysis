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

import lombok.Getter;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;

public class ReplaceHashtableWithHashMap extends ReplaceLegacyCollection {

    // Hashtable (Dictionary) methods that HashMap does not provide; if any is used the type cannot be swapped.
    private static final Set<String> INCOMPATIBLE_METHODS = new HashSet<>(Arrays.asList(
            "contains", "elements", "keys"));

    @Getter
    final String displayName = "Replace `java.util.Hashtable` with `java.util.HashMap`";

    @Getter
    final String description = "`Hashtable` synchronizes every operation, which adds overhead in the common " +
            "single-threaded case. This recipe replaces a local `Hashtable` with a `HashMap` when data flow " +
            "analysis can prove the `Hashtable` never escapes its method (it is not returned, assigned to a field, " +
            "or passed as an argument), so no other thread can observe it and the synchronization is redundant. " +
            "Fields, escaping variables, and `Hashtable`-specific method usages (`contains`, `elements`, `keys`) " +
            "are left untouched. `HashMap` permits `null` keys and values, so it accepts every input `Hashtable` " +
            "did.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1149");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    String getLegacyType() {
        return "java.util.Hashtable";
    }

    @Override
    String getReplacementType() {
        return "java.util.HashMap";
    }

    @Override
    Set<String> getIncompatibleMethods() {
        return INCOMPATIBLE_METHODS;
    }

    @Override
    Set<String> getIncompatibleSupertypes() {
        // HashMap does not extend Dictionary, so a value flowing into a Dictionary-typed target cannot be converted.
        return singleton("java.util.Dictionary");
    }
}
