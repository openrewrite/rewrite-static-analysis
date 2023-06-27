package org.openrewrite.staticanalysis;

import org.apache.commons.lang3.StringUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.openrewrite.Tree.randomId;

public class ReplaceWeekYearWithYear extends Recipe {
    private static final MethodMatcher DATE_FORMAT_MATCHER = new MethodMatcher("java.text.SimpleDateFormat <constructor>(..)");

    @Override
    public String getDisplayName() {
        return "Week Year (YYYY) should not be used for date formatting";
    }

    @Override
    public String getDescription() {
        return "For most dates Week Year (YYYY) and Year (yyyy) yield the same results. However, on the last week of" +
               " December and first week of January Week Year could produce unexpected results.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3986");
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass nc, ExecutionContext ctx) {
                if (!DATE_FORMAT_MATCHER.matches(nc.getConstructorType())) {
                    return nc;
                }

                Expression argument = nc.getArguments().get(0);
                String format_string = argument.print(getCursor());

                if (format_string.equals("\"YYYY/MM/dd\"")) {
                    format_string = format_string.replace('Y', 'y');
                    // remove surrounding quotes around string
                    format_string = StringUtils.chop(format_string);
                    format_string = format_string.substring(1);

                    J.Literal new_format = new J.Literal(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            format_string,
                            "\"" + format_string + "\"",
                            null,
                            JavaType.Primitive.String
                    );

                    nc = JavaTemplate.builder("new SimpleDateFormat(#{any(java.lang.String)})")
                            .contextSensitive()
                            .build()
                            .apply(getCursor(), nc.getCoordinates().replace(), new_format);
               }

                return nc;
            }
        };
    }
}
