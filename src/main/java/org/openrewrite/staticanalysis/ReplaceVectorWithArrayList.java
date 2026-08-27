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
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;

public class ReplaceVectorWithArrayList extends ReplaceLegacyCollection {

    // Vector methods that ArrayList does not provide; if any is used the type cannot be swapped.
    // Note: `ensureCapacity` and `trimToSize` exist on both, so they are intentionally omitted.
    private static final Set<String> INCOMPATIBLE_METHODS = new HashSet<>(Arrays.asList(
            "addElement", "capacity", "copyInto", "elementAt", "elements", "firstElement",
            "insertElementAt", "lastElement", "removeAllElements", "removeElement",
            "removeElementAt", "setElementAt", "setSize"));

    @Getter
    final String displayName = "Replace `java.util.Vector` with `java.util.ArrayList`";

    @Getter
    final String description = "`Vector` synchronizes every operation, which adds overhead in the common " +
            "single-threaded case. This recipe replaces a local `Vector` with an `ArrayList` when data flow " +
            "analysis can prove the `Vector` never escapes its method (it is not returned, assigned to a field, " +
            "or passed as an argument), so no other thread can observe it and the synchronization is redundant. " +
            "Fields, escaping variables, `Vector`-specific method usages (like `elementAt` or `addElement`), and " +
            "the `Vector(int, int)` constructor are left untouched.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1149");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    String getLegacyType() {
        return "java.util.Vector";
    }

    @Override
    String getReplacementType() {
        return "java.util.ArrayList";
    }

    @Override
    Set<String> getIncompatibleMethods() {
        return INCOMPATIBLE_METHODS;
    }

    @Override
    boolean isIncompatibleConstructor(J.NewClass newClass) {
        // Vector(int initialCapacity, int capacityIncrement) has no ArrayList equivalent.
        return newClass.getArguments().stream().filter(a -> !(a instanceof J.Empty)).count() >= 2;
    }
}
