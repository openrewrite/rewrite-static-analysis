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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddPmdRule extends Recipe {

    @Option(displayName = "Rule",
            description = "The rule to add, as a fully qualified reference naming both the ruleset file and the rule " +
                          "within it. A bare rule name is not enough, since PMD resolves a rule through the ruleset " +
                          "file it lives in.",
            example = "category/java/errorprone.xml/EmptyCatchBlock")
    String rule;

    @Override
    public String getDisplayName() {
        return "Add a PMD rule to a ruleset";
    }

    @Override
    public String getDescription() {
        return "Adds a `<rule ref=\"...\"/>` reference to PMD ruleset XML files that do not have one yet. When the " +
               "ruleset already pulls in the whole ruleset file the rule lives in, the rule is enabled by removing the " +
               "`<exclude name=\"...\"/>` that was keeping it out rather than by adding a second reference to it.";
    }

    @Override
    public Validated<Object> validate() {
        return super.validate().and(Validated.test("rule",
                "Must name both a ruleset file and a rule, such as `category/java/errorprone.xml/EmptyCatchBlock`.",
                rule, r -> r != null && r.indexOf('/') != -1));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Tag root = document.getRoot();
                // Only PMD rulesets have the `<rule>` elements this recipe operates on
                if (!"ruleset".equals(root.getName())) {
                    return document;
                }

                String rulesetFile = PmdRuleRef.rulesetFile(rule);
                for (Xml.Tag existing : root.getChildren("rule")) {
                    String ref = PmdRuleRef.attributeValue(existing, "ref");
                    if (ref == null || !(ref.equals(rule) || ref.equals(rulesetFile))) {
                        continue;
                    }
                    if (ref.equals(rule)) {
                        return document;
                    }
                    // The whole ruleset file is referenced, so the rule is already active unless it is excluded
                    for (Xml.Tag exclude : existing.getChildren("exclude")) {
                        if (PmdRuleRef.name(rule).equals(PmdRuleRef.attributeValue(exclude, "name"))) {
                            doAfterVisit(new RemoveRulesetContentVisitor<>(exclude));
                            return document;
                        }
                    }
                    return document;
                }

                doAfterVisit(new AddToTagVisitor<>(root, Xml.Tag.build("<rule ref=\"" + rule + "\"/>")));
                return document;
            }
        };
    }
}
