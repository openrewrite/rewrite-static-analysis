package org.openrewrite.staticanalysis.nameconvention;

import org.openrewrite.internal.NameCaseConvention;

import java.util.regex.Pattern;

class JavaNameConvention implements NameConvention {

    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-zA-Z0-9]+_\\w+$");

    @Override
    public String applyNameConvention(String normalizedName) {
        StringBuilder result = new StringBuilder();
        if (SNAKE_CASE.matcher(normalizedName).matches()) {
            result.append(NameCaseConvention.format(NameCaseConvention.LOWER_CAMEL, normalizedName));
        } else {
            int nameLength = normalizedName.length();
            for (int i = 0; i < nameLength; i++) {
                char c = normalizedName.charAt(i);

                if (i == 0) {
                    // the java specification requires identifiers to start with [a-zA-Z$_]
                    if (c != '$' && c != '_') {
                        result.append(Character.toLowerCase(c));
                    }
                } else {
                    if (!Character.isLetterOrDigit(c)) {
                        while (i < nameLength && (!Character.isLetterOrDigit(c) || c > 'z')) {
                            c = normalizedName.charAt(i++);
                        }
                        if (i < nameLength) {
                            result.append(Character.toUpperCase(c));
                        }
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.toString();
    }
}
