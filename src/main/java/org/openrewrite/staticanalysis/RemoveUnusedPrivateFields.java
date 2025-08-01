/*
 * Copyright 2024 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.NoMissingTypes;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveUnusedPrivateFields extends Recipe {
    private static final AnnotationMatcher LOMBOK_ANNOTATION = new AnnotationMatcher("@lombok.*");

    @Override
    public String getDisplayName() {
        return "Remove unused private fields";
    }

    @Override
    public String getDescription() {
        return "If a private field is declared but not used in the program, it can be considered dead code and should therefore be removed.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1068");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Value
            class CheckField {
                J.VariableDeclarations declarations;

                @Nullable
                Statement nextStatement;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                // Do not remove fields if class has Lombok @Data annotation
                Iterator<Cursor> clz = getCursor().getPathAsCursors(c -> c.getValue() instanceof J.ClassDeclaration);
                if (clz.hasNext() && service(AnnotationService.class).matches(clz.next(), LOMBOK_ANNOTATION)) {
                    return cd;
                }

                List<CheckField> checkFields = new ArrayList<>();
                // Do not remove fields with `serialVersionUID` name.
                boolean skipSerialVersionUID = cd.getType() == null ||
                        cd.getType().isAssignableTo("java.io.Serializable");

                List<Statement> statements = cd.getBody().getStatements();
                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) statement;
                        // RSPEC-S1068 does not apply serialVersionUID of Serializable classes, or fields with annotations.
                        if (!(skipSerialVersionUID && isSerialVersionUid(vd)) &&
                                vd.getLeadingAnnotations().isEmpty() &&
                                vd.hasModifier(J.Modifier.Type.Private)) {
                            Statement nextStatement = i < statements.size() - 1 ? statements.get(i + 1) : null;
                            checkFields.add(new CheckField(vd, nextStatement));
                        }
                    } else if (statement instanceof J.MethodDeclaration) {
                        // RSPEC-S1068 does not apply fields from classes with native methods.
                        J.MethodDeclaration md = (J.MethodDeclaration) statement;
                        if (md.hasModifier(J.Modifier.Type.Native)) {
                            return cd;
                        }
                    }
                }

                if (checkFields.isEmpty()) {
                    return cd;
                }

                J.ClassDeclaration outer = cd;
                for (Cursor parent = getCursor().getParent(); parent != null; parent = parent.getParent()) {
                    if (parent.getValue() instanceof J.ClassDeclaration) {
                        outer = parent.getValue();
                    }
                }
                for (CheckField checkField : checkFields) {
                    // Find variable uses.
                    Map<J.VariableDeclarations.NamedVariable, List<J.Identifier>> inUse =
                            VariableUses.find(checkField.declarations, outer);
                    for (Map.Entry<J.VariableDeclarations.NamedVariable, List<J.Identifier>> entry : inUse.entrySet()) {
                        if (entry.getValue().isEmpty()) {
                            AtomicBoolean declarationDeleted = new AtomicBoolean();
                            J.VariableDeclarations.NamedVariable fieldToRemove = entry.getKey();
                            cd = (J.ClassDeclaration) new RemoveUnusedField(fieldToRemove).visitNonNull(cd, declarationDeleted);
                            if (fieldToRemove.getType() != null) {
                                maybeRemoveImport(fieldToRemove.getType().toString());
                            }
                            // Maybe remove next statement comment if variable declarations is removed
                            if (declarationDeleted.get()) {
                                cd = (J.ClassDeclaration) new MaybeRemoveComment(checkField.nextStatement, cd).visitNonNull(cd, ctx);
                            }
                        }
                    }
                }

                return cd;
            }

            private boolean isSerialVersionUid(J.VariableDeclarations vd) {
                return vd.hasModifier(J.Modifier.Type.Private) &&
                        vd.hasModifier(J.Modifier.Type.Static) &&
                        vd.hasModifier(J.Modifier.Type.Final) &&
                        TypeUtils.isOfClassType(vd.getType(), "long") &&
                        vd.getVariables().stream().anyMatch(it -> "serialVersionUID".equals(it.getSimpleName()));
            }
        };
        return Preconditions.check(new NoMissingTypes(), Repeat.repeatUntilStable(visitor));
    }

    private static class VariableUses {
        public static Map<J.VariableDeclarations.NamedVariable, List<J.Identifier>> find(J.VariableDeclarations declarations, J.ClassDeclaration parent) {
            Map<J.VariableDeclarations.NamedVariable, List<J.Identifier>> found = new IdentityHashMap<>(declarations.getVariables().size());
            Map<String, J.VariableDeclarations.NamedVariable> signatureMap = new HashMap<>();
            Map<String, J.VariableDeclarations.NamedVariable> nameMap = new HashMap<>();

            for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
                if (variable.getVariableType() != null) {
                    found.computeIfAbsent(variable, k -> new ArrayList<>());
                    // Note: Using a variable type signature is only safe to find uses of class fields.
                    signatureMap.put(variable.getVariableType().toString(), variable);
                    // Also map by name for fallback matching
                    nameMap.put(variable.getSimpleName(), variable);
                }
            }

            JavaIsoVisitor<Map<J.VariableDeclarations.NamedVariable, List<J.Identifier>>> visitor =
                    new JavaIsoVisitor<Map<J.VariableDeclarations.NamedVariable, List<J.Identifier>>>() {

                        @Override
                        public J.Identifier visitIdentifier(J.Identifier identifier,
                                                            Map<J.VariableDeclarations.NamedVariable, List<J.Identifier>> identifiers) {
                            if (identifier.getFieldType() != null) {
                                J.VariableDeclarations.NamedVariable match = null;

                                // First try exact type signature match
                                String fieldTypeSignature = identifier.getFieldType().toString();
                                if (signatureMap.containsKey(fieldTypeSignature)) {
                                    match = signatureMap.get(fieldTypeSignature);
                                } else if (identifier.getFieldType().getOwner() != null) {
                                    // Fallback: match by name if it's a field reference from the same class
                                    J.VariableDeclarations.NamedVariable nameMatch = nameMap.get(identifier.getSimpleName());
                                    if (nameMatch != null && nameMatch.getVariableType() != null) {
                                        // Check if the owner type matches the parent class type
                                        JavaType.FullyQualified ownerType = TypeUtils.asFullyQualified(identifier.getFieldType().getOwner());
                                        JavaType.FullyQualified parentType = parent.getType();
                                        if (ownerType != null && parentType != null &&
                                                TypeUtils.fullyQualifiedNamesAreEqual(ownerType.getFullyQualifiedName(), parentType.getFullyQualifiedName())) {
                                            match = nameMatch;
                                        }
                                    }
                                }

                                if (match != null) {
                                    Cursor parentCursor = getCursor().dropParentUntil(is ->
                                            is instanceof J.VariableDeclarations || is instanceof J.ClassDeclaration);

                                    if (!(parentCursor.getValue() instanceof J.VariableDeclarations && parentCursor.getValue() == declarations)) {
                                        if (declarations.getVariables().contains(match)) {
                                            identifiers.computeIfAbsent(match, k -> new ArrayList<>())
                                                    .add(identifier);
                                        }
                                    }
                                }
                            }
                            return super.visitIdentifier(identifier, identifiers);
                        }
                    };

            visitor.visit(parent, found);
            return found;
        }
    }

    private static class RemoveUnusedField extends JavaVisitor<AtomicBoolean> {
        private final J.VariableDeclarations.NamedVariable namedVariable;

        public RemoveUnusedField(J.VariableDeclarations.NamedVariable namedVariable) {
            this.namedVariable = namedVariable;
        }

        @Override
        public @Nullable J visitVariableDeclarations(J.VariableDeclarations multiVariable, AtomicBoolean declarationDeleted) {
            if (multiVariable.getVariables().size() == 1 && multiVariable.getVariables().contains(namedVariable)) {
                declarationDeleted.set(true);
                //noinspection ConstantConditions
                return null;
            }
            return super.visitVariableDeclarations(multiVariable, declarationDeleted);
        }

        @Override
        public @Nullable J visitVariable(J.VariableDeclarations.NamedVariable variable, AtomicBoolean declarationDeleted) {
            if (variable == namedVariable) {
                //noinspection ConstantConditions
                return null;
            }
            return super.visitVariable(variable, declarationDeleted);
        }
    }

    private static class MaybeRemoveComment extends JavaVisitor<ExecutionContext> {
        @Nullable
        private final Statement statement;

        private final J.ClassDeclaration classDeclaration;

        public MaybeRemoveComment(@Nullable Statement statement, J.ClassDeclaration classDeclaration) {
            this.statement = statement;
            this.classDeclaration = classDeclaration;
        }

        @Override
        public J visitStatement(Statement s, ExecutionContext ctx) {
            if (s == statement) {
                Space prefix = s.getPrefix();
                // If we have at least one comment and there is no newline
                if (!prefix.getComments().isEmpty() && !prefix.getWhitespace().contains("\n")) {
                    return s.withPrefix(prefix
                            // Copy suffix to prefix
                            .withWhitespace(prefix.getComments().get(0).getSuffix())
                            // Remove the first comment
                            .withComments(prefix.getComments().subList(1, prefix.getComments().size())
                            ));

                }
            }
            return super.visitStatement(s, ctx);
        }

        @Override
        public J visitClassDeclaration(J.ClassDeclaration c, ExecutionContext ctx) {
            // We also need to remove comments attached to end of classDeclaration if it's the last statement
            if (statement == null && c == classDeclaration) {
                Space end = c.getBody().getEnd();
                // If we have at least one comment and there is no newline
                if (!end.getComments().isEmpty() && !end.getWhitespace().contains("\n")) {
                    return c.withBody(c.getBody().withEnd(end
                            .withWhitespace(end.getComments().get(0).getSuffix())
                            .withComments(end.getComments().subList(1, end.getComments().size()))
                    ));
                }
            }
            return super.visitClassDeclaration(c, ctx);
        }
    }

}
