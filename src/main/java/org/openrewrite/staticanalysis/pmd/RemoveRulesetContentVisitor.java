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

import org.openrewrite.xml.RemoveContentVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

/**
 * Removes an element from a PMD ruleset, collapsing a {@code <rule>} whose last {@code <exclude>} went away to a
 * self-closing tag rather than leaving an empty body behind. The enclosing {@code <ruleset>} is left alone even when it
 * ends up empty, since a ruleset file is expected to keep a body to add rules back into.
 */
class RemoveRulesetContentVisitor<P> extends RemoveContentVisitor<P> {

    RemoveRulesetContentVisitor(Content content) {
        super(content, false, true);
    }

    @Override
    public Xml visitTag(Xml.Tag tag, P p) {
        Xml x = super.visitTag(tag, p);
        // Only a `<rule>` this visitor just emptied is collapsed; one already written with an empty body is left be
        if (x != tag && x instanceof Xml.Tag && "rule".equals(((Xml.Tag) x).getName())) {
            Xml.Tag t = (Xml.Tag) x;
            if (t.getContent() != null && t.getContent().isEmpty()) {
                return t.withContent(null).withClosing(null);
            }
        }
        return x;
    }
}
