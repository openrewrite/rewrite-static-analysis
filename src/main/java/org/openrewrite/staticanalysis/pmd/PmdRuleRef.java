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
package org.openrewrite.staticanalysis.pmd;

import org.jspecify.annotations.Nullable;
import org.openrewrite.xml.tree.Xml;

/**
 * Helpers for the rule references found in PMD ruleset XML, which take the form
 * {@code category/java/bestpractices.xml/UnusedImports}: a ruleset file followed by a rule name.
 * <p>
 * Recipe options accept either the whole reference or just the rule name, so that a ruleset can be
 * migrated without having to spell out which category file the rule used to live in.
 */
final class PmdRuleRef {

    private PmdRuleRef() {
    }

    /**
     * Whether {@code ref}, always a fully qualified reference, is the rule identified by {@code rule},
     * which is either a fully qualified reference or a bare rule name.
     */
    static boolean matches(String ref, String rule) {
        return rule.indexOf('/') == -1 ? name(ref).equals(rule) : ref.equals(rule);
    }

    /** The rule name, dropping any ruleset file that precedes it. */
    static String name(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    /** The ruleset file a rule lives in, or {@code null} when {@code ref} is a bare rule name. */
    static @Nullable String rulesetFile(String ref) {
        int lastSlash = ref.lastIndexOf('/');
        return lastSlash == -1 ? null : ref.substring(0, lastSlash);
    }

    static @Nullable String attributeValue(Xml.Tag tag, String key) {
        for (Xml.Attribute attribute : tag.getAttributes()) {
            if (key.equals(attribute.getKeyAsString())) {
                return attribute.getValueAsString();
            }
        }
        return null;
    }
}
