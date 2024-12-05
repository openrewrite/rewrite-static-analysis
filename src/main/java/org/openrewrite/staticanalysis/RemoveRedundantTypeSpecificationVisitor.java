package org.openrewrite.staticanalysis;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class RemoveRedundantTypeSpecificationVisitor extends JavaIsoVisitor<ExecutionContext> {
    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
        System.out.println("                           >>>> SUPER visitVariableDeclarations: " + multiVariable);
        J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
        final TypedTree varDeclsTypeExpression = varDecls.getTypeExpression();
        if (varDeclsTypeExpression != null &&
                varDecls.getVariables().size() == 1 &&
                varDecls.getVariables().get(0).getInitializer() != null &&
                varDeclsTypeExpression instanceof J.ParameterizedType) {
            varDecls = varDecls.withVariables(ListUtils.map(varDecls.getVariables(), nv -> {
                if (nv.getInitializer() instanceof J.NewClass) {
                    nv = nv.withInitializer(maybeRemoveParams(parameterizedTypes((J.ParameterizedType) varDeclsTypeExpression), (J.NewClass) nv.getInitializer()));
                }
                return nv;
            }));
        }
        return varDecls;
    }

    @Override
    public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
        System.out.println("                           >>>> SUPER visitAssignment: " + assignment);
        J.Assignment asgn = super.visitAssignment(assignment, ctx);
        if (asgn.getAssignment() instanceof J.NewClass) {
            J.NewClass nc = (J.NewClass) asgn.getAssignment();
            if (nc.getClazz() instanceof J.ParameterizedType) {
                JavaType.Parameterized assignmentType = TypeUtils.asParameterized(asgn.getType());
                JavaType.Class assignmentTypeAsClass = TypeUtils.asClass(asgn.getType());
                if (assignmentType != null) {
                    asgn = asgn.withAssignment(maybeRemoveParams(assignmentType.getTypeParameters(), nc));
                } else if (assignmentTypeAsClass != null) {
                    asgn = asgn.withAssignment(maybeRemoveParams(assignmentTypeAsClass.getTypeParameters(), nc));
                }
            }
        }
        return asgn;
    }

    boolean hasEmptyBody(J.NewClass newClass) {
        return newClass.getBody() == null;
    }

    J.NewClass removeRedundantType(J.NewClass newClass) {
        J.ParameterizedType newClassType = (J.ParameterizedType) newClass.getClazz();
        return newClass.withClazz(newClassType.withTypeParameters(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY))));
    }

    @Nullable List<JavaType> parameterizedTypes(J.ParameterizedType parameterizedType) {
        if (parameterizedType.getTypeParameters() == null) {
            return null;
        }
        List<JavaType> types = new ArrayList<>(parameterizedType.getTypeParameters().size());
        for (Expression typeParameter : parameterizedType.getTypeParameters()) {
            types.add(typeParameter.getType());
        }
        return types;
    }


    J.NewClass maybeRemoveParams(@Nullable List<JavaType> paramTypes, J.NewClass newClass) {
        if (paramTypes != null && hasEmptyBody(newClass) && newClass.getClazz() instanceof J.ParameterizedType) {
            J.ParameterizedType newClassType = (J.ParameterizedType) newClass.getClazz();
            if (newClassType.getTypeParameters() != null) {
                if (paramTypes.size() != newClassType.getTypeParameters().size() || hasAnnotations(newClassType)) {
                    return newClass;
                } else {
                    for (int i = 0; i < paramTypes.size(); i++) {
                        if (!TypeUtils.isAssignableTo(paramTypes.get(i), newClassType.getTypeParameters().get(i).getType())) {
                            return newClass;
                        }
                    }
                }
                newClassType.getTypeParameters().stream()
                        .map(e -> TypeUtils.asFullyQualified(e.getType()))
                        .forEach(this::maybeRemoveImport);
                newClass = removeRedundantType(newClass);
            }
        }
        return newClass;
    }

    private boolean hasAnnotations(J type) {
        if (type instanceof J.ParameterizedType) {
            J.ParameterizedType parameterizedType = (J.ParameterizedType) type;
            if (hasAnnotations(parameterizedType.getClazz())) {
                return true;
            } else if (parameterizedType.getTypeParameters() != null) {
                for (Expression typeParameter : parameterizedType.getTypeParameters()) {
                    if (hasAnnotations(typeParameter)) {
                        return true;
                    }
                }
            }
        } else {
            return type instanceof J.AnnotatedType;
        }
        return false;
    }
}
