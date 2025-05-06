package org.openrewrite.staticanalysis;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReorderAnnotations extends Recipe {
    private static final Comparator<J.Annotation> defaultComparator = Comparator.comparing(J.Annotation::getSimpleName);
    // TODO: Take in optional Comparator<J.Annotation>, defaulted to alphabetical
    @Override
    public String getDisplayName() {
        return "Reorder annotations in a consistent order";
    }

    @Override
    public String getDescription() {
        return "Reorder annotations based on a provided Comparator class.";
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class ReorderAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        Comparator<J.Annotation> comparator;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            List<J.Annotation> annotations = m.getLeadingAnnotations();
            if (annotations.isEmpty()) {
                return m;
            }
            List<J.Annotation> sortedAnnotations = new ArrayList<>(annotations);
            sortedAnnotations.sort(comparator);
            if (sortedAnnotations.equals(annotations)) {
                return m;
            }
            for (int i = 0; i < annotations.size(); i++) {
                sortedAnnotations.set(i, sortedAnnotations.get(i).withPrefix(annotations.get(i).getPrefix()));
            }

            return m.withLeadingAnnotations(sortedAnnotations);
        }
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new ReorderAnnotationVisitor(defaultComparator);
    }
}
