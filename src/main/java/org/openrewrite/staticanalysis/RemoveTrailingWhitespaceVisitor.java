/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.jspecify.annotations.Nullable;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.binary.Binary;
import org.openrewrite.quark.Quark;
import org.openrewrite.remote.Remote;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoveTrailingWhitespaceVisitor<P> extends PlainTextVisitor<P> {

    @Nullable
    private final Tree stopAfter;

    @JsonCreator
    public RemoveTrailingWhitespaceVisitor(@Nullable Tree stopAfter) {
        this.stopAfter = stopAfter;
    }

    public RemoveTrailingWhitespaceVisitor() {
        this(null);
    }

    @Override
    public boolean isAcceptable(SourceFile sourceFile, P p) {
        return !(sourceFile instanceof Binary
                || sourceFile instanceof Quark
                || sourceFile instanceof Remote);
    }

    @Override
    public PlainText visitText(PlainText text, P p) {
        PlainText plainText = super.visitText(text, p);
        StringBuilder buf = new StringBuilder();
        stripTrailingWhitespace(plainText.getText(), buf);
        return plainText.withText(buf.toString());
    }

    @Override
    public PlainText.Snippet visitSnippet(PlainText.Snippet snippet, P p) {
        PlainText.Snippet plainTextSnippet = super.visitSnippet(snippet, p);
        StringBuilder buf = new StringBuilder();
        stripTrailingWhitespace(plainTextSnippet.getText(), buf);
        return plainTextSnippet.withText(buf.toString());
    }

    private static final Pattern newline = Pattern.compile("(\\r\\n|\\r|\\n)");

    private void stripTrailingWhitespace(String str, StringBuilder buf) {
        Matcher m = newline.matcher(str);
        int startIndex = 0;
        while (startIndex < str.length()) {
            boolean matchFound = m.find(startIndex);
            int endIndex = matchFound ? m.start() : str.length();
            String substring = str.substring(startIndex, endIndex);
            buf.append(substring.replaceFirst("\\s+$", ""));
            if (matchFound) {
                buf.append(str, m.start(), m.end());
                startIndex = m.end();
            } else {
                startIndex = str.length();
            }
        }
    }

}
