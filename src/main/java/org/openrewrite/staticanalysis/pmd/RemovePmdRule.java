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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemovePmdRule extends Recipe {

    @Option(displayName = "Rule",
            description = "The rule to remove, either a fully qualified reference such as " +
                          "`category/java/codestyle.xml/AvoidFinalLocalVariable` or just the rule name, in which case " +
                          "the rule is removed regardless of which ruleset file it is referenced from.",
            example = "category/java/codestyle.xml/AvoidFinalLocalVariable")
    String rule;

    @Override
    public String getDisplayName() {
        return "Remove a PMD rule from a ruleset";
    }

    @Override
    public String getDescription() {
        return "Removes both `<rule ref=\"...\"/>` references to a rule and `<exclude name=\"...\"/>` elements naming it " +
               "from PMD ruleset XML files. Intended for rules that PMD deleted without offering a replacement, since " +
               "PMD fails to load a ruleset that references a rule it does not know.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                // Only PMD rulesets have the `<rule>` and `<exclude>` elements this recipe operates on
                return "ruleset".equals(document.getRoot().getName()) ? super.visitDocument(document, ctx) : document;
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if ("rule".equals(t.getName())) {
                    String ref = PmdRuleRef.attributeValue(t, "ref");
                    if (ref != null && PmdRuleRef.matches(ref, rule)) {
                        doAfterVisit(new RemoveRulesetContentVisitor<>(t));
                    }
                } else if ("exclude".equals(t.getName())) {
                    // An `<exclude>` names a rule within the ruleset file its enclosing `<rule>` refers to
                    Object parent = getCursor().getParentTreeCursor().getValue();
                    String name = PmdRuleRef.attributeValue(t, "name");
                    if (name != null && parent instanceof Xml.Tag && "rule".equals(((Xml.Tag) parent).getName())) {
                        String rulesetFile = PmdRuleRef.attributeValue((Xml.Tag) parent, "ref");
                        if (rulesetFile != null && PmdRuleRef.matches(rulesetFile + '/' + name, rule)) {
                            doAfterVisit(new RemoveRulesetContentVisitor<>(t));
                        }
                    }
                }
                return t;
            }
        };
    }
}
