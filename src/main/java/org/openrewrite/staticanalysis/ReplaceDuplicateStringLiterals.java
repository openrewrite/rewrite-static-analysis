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
import lombok.experimental.NonFinal;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceDuplicateStringLiterals extends Recipe {

    @Option(displayName = "Apply recipe to test source set",
            description = "Changes only apply to main by default. `includeTestSources` will apply the recipe to `test` source files.",
            required = false)
    @Nullable
    Boolean includeTestSources;

    @Override
    public String getDisplayName() {
        return "Replace duplicate `String` literals";
    }

    @Override
    public String getDescription() {
        return "Replaces `String` literals with a length of 5 or greater repeated a minimum of 3 times. " +
               "Qualified `String` literals include final Strings, method invocations, and new class invocations. " +
               "Adds a new `private static final String` or uses an existing equivalent class field. " +
               "A new variable name will be generated based on the literal value if an existing field does not exist. " +
               "The generated name will append a numeric value to the variable name if a name already exists in the compilation unit.";
    }

    @Override
    public Set<String> getTags() {
        return new LinkedHashSet<>(asList("RSPEC-1192", "RSPEC-1889"));
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    int maxVariableLength = 40;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("java.lang.String", false), new JavaVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) tree;
                    Optional<JavaSourceSet> sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class);
                    if (!Boolean.TRUE.equals(includeTestSources) && !(sourceSet.isPresent() && "main".equals(sourceSet.get().getName()))) {
                        return cu;
                    }
                }
                return super.visit(tree, ctx);
            }

            @SuppressWarnings("UnusedAssignment")
            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (classDecl.getType() == null) {
                    return classDecl;
                }

                DuplicateLiteralInfo duplicateLiteralInfo = DuplicateLiteralInfo.find(classDecl);
                Map<String, List<J.Literal>> duplicateLiteralsMap = duplicateLiteralInfo.getDuplicateLiterals();
                if (duplicateLiteralsMap.isEmpty()) {
                    return classDecl;
                }
                Map<String, String> fieldValueToFieldName = duplicateLiteralInfo.getFieldValueToFieldName();
                Set<String> variableNames = VariableNameUtils.findNamesInScope(getCursor()).stream()
                        .filter(i -> !fieldValueToFieldName.containsValue(i)).collect(Collectors.toSet());
                String classFqn = classDecl.getType().getFullyQualifiedName();
                Map<J.Literal, String> replacements = new HashMap<>();
                for (Map.Entry<String, List<J.Literal>> entry : duplicateLiteralsMap.entrySet()) {
                    String valueOfLiteral = entry.getKey();
                    List<J.Literal> duplicateLiterals = duplicateLiteralsMap.get(valueOfLiteral);
                    String classFieldName = fieldValueToFieldName.get(valueOfLiteral);
                    String variableName;
                    if (classFieldName != null) {
                        String maybeVariableName = getNameWithoutShadow(classFieldName, variableNames);
                        if (duplicateLiteralInfo.existingFieldValueToFieldName.get(maybeVariableName) != null) {
                            variableNames.add(maybeVariableName);
                            maybeVariableName = getNameWithoutShadow(classFieldName, variableNames);
                        }

                        variableName = maybeVariableName;
                        if (StringUtils.isBlank(variableName)) {
                            continue;
                        }
                        if (!classFieldName.equals(variableName)) {
                            doAfterVisit(new ChangeFieldName<>(classFqn, classFieldName, variableName));
                        }
                    } else {
                        variableName = getNameWithoutShadow(transformToVariableName(valueOfLiteral), variableNames);
                        if (StringUtils.isBlank(variableName)) {
                            continue;
                        }
                        J.Literal replaceLiteral = duplicateLiterals.get(0).withId(randomId());
                        String modifiers = (classDecl.getKind() == J.ClassDeclaration.Kind.Type.Interface) ? "" : "private static final ";
                        JavaTemplate template = JavaTemplate.builder(modifiers + "String " + variableName + " = #{any(String)};").build();
                        if (classDecl.getKind() == J.ClassDeclaration.Kind.Type.Enum) {
                            J.Block applied = template
                                    .apply(new Cursor(getCursor(), classDecl.getBody()), classDecl.getBody().getCoordinates().lastStatement(), replaceLiteral);
                            List<Statement> statements = applied.getStatements();
                            statements.add(1, statements.remove(statements.size() - 1));
                            classDecl = classDecl.withBody(applied.withStatements(statements));
                        } else {
                            classDecl = classDecl.withBody(
                                    template
                                            .apply(new Cursor(getCursor(), classDecl.getBody()), classDecl.getBody().getCoordinates().firstStatement(), replaceLiteral));
                        }
                    }
                    variableNames.add(variableName);
                    entry.getValue().forEach(v -> replacements.put(v, variableName));
                }
                duplicateLiteralInfo = null;
                duplicateLiteralsMap = null;
                return replacements.isEmpty() ? classDecl :
                        new ReplaceStringLiterals(classDecl, replacements).visitNonNull(classDecl, ctx, requireNonNull(getCursor().getParent()));
            }

            /**
             * Generate a variable name that does not create a name space conflict.
             * @param name variable name to replace duplicate literals with.
             * @param variableNames variable names that exist in the compilation unit.
             * @return unique variable name.
             */
            private String getNameWithoutShadow(String name, Set<String> variableNames) {
                String transformedName = transformToVariableName(name);
                String newName = transformedName;
                int append = 0;
                while (variableNames.contains(newName)) {
                    append++;
                    newName = transformedName + "_" + append;
                }
                return newName;
            }

            /**
             * Convert a `String` value to a variable name with naming convention of all caps delimited by `_`.
             * Special characters are filtered out to meet regex convention: ^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$
             */
            private String transformToVariableName(String valueOfLiteral) {
                boolean prevIsLower = false;
                boolean prevIsCharacter = false;
                StringBuilder newName = new StringBuilder();
                for (int i = 0; i < valueOfLiteral.length(); i++) {
                    char c = valueOfLiteral.charAt(i);
                    if (i > 0 && (Character.isUpperCase(c) && prevIsLower || !prevIsCharacter) &&
                        newName.length() > 0 && newName.charAt(newName.length() - 1) != '_') {
                        newName.append('_');
                    }
                    prevIsCharacter = Character.isLetterOrDigit(c);
                    if (prevIsCharacter) {
                        if (newName.length() == 0 && Character.isDigit(c)) {
                            newName.append("A_");
                        }
                        newName.append(Character.toUpperCase(c));
                        prevIsLower = Character.isLowerCase(c);
                    }
                }
                String newNameString = newName.toString();
                while (newNameString.length() > maxVariableLength){
                    int indexOf = newNameString.lastIndexOf("_");
                    newNameString = newNameString.substring(0, indexOf > -1 ? indexOf : maxVariableLength);
                }
                return VariableNameUtils.normalizeName(newNameString);
            }
        });
    }

    private static boolean isPrivateStaticFinalVariable(J.VariableDeclarations.NamedVariable variable) {
        return variable.getVariableType() != null && variable.getVariableType().hasFlags(Flag.Private, Flag.Static, Flag.Final);
    }

    @Value
    private static class DuplicateLiteralInfo {
        Map<String, String> fieldValueToFieldName;
        Map<String, String> existingFieldValueToFieldName;

        @NonFinal
        Map<String, List<J.Literal>> duplicateLiterals;

        public static DuplicateLiteralInfo find(J.ClassDeclaration inClass) {
            DuplicateLiteralInfo result = new DuplicateLiteralInfo(new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>());
            new JavaIsoVisitor<Integer>() {

                @Override
                public J.Annotation visitAnnotation(J.Annotation annotation, Integer integer) {
                    // Literals in annotations cannot be replaced with variables and should be ignored
                    return annotation;
                }

                @Override
                public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Integer integer) {
                    J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, integer);
                    Cursor parentScope = getCursor().dropParentUntil(is -> is instanceof J.ClassDeclaration || is instanceof J.MethodDeclaration);
                    boolean privateStaticFinalVariable = isPrivateStaticFinalVariable(variable);
                    // `private static final String`(s) are handled separately by `FindExistingPrivateStaticFinalFields`.
                    if (v.getInitializer() instanceof J.Literal &&
                        (parentScope.getValue() instanceof J.MethodDeclaration || parentScope.getValue() instanceof J.ClassDeclaration) &&
                            !(privateStaticFinalVariable && ((J.Literal) v.getInitializer()).getValue() instanceof String)) {
                        String value = (((J.Literal) v.getInitializer()).getValue()).toString();
                        result.existingFieldValueToFieldName.put(v.getSimpleName(), value);
                    }
                    if (parentScope.getValue() instanceof J.ClassDeclaration &&
                        privateStaticFinalVariable && v.getInitializer() instanceof J.Literal &&
                        ((J.Literal) v.getInitializer()).getValue() instanceof String) {
                        String value = (String) (((J.Literal) v.getInitializer()).getValue());
                        result.fieldValueToFieldName.putIfAbsent(value, v.getSimpleName());
                    }
                    return v;
                }

                @Override
                public J.Literal visitLiteral(J.Literal literal, Integer integer) {
                    if (JavaType.Primitive.String == literal.getType() &&
                        literal.getValue() instanceof String &&
                        ((String) literal.getValue()).length() >= 5) {

                        Cursor parent = getCursor().dropParentUntil(is -> is instanceof J.ClassDeclaration ||
                                                                          is instanceof J.Annotation ||
                                                                          is instanceof J.VariableDeclarations.NamedVariable ||
                                                                          is instanceof J.NewClass ||
                                                                          is instanceof J.MethodInvocation);
                        // EnumValue can accept constructor arguments, including string literals
                        // But the static field can't be placed before them, so these literals are ineligible for replacement
                        if (parent.getValue() instanceof J.NewClass && parent.firstEnclosing(J.EnumValueSet.class) != null) {
                            return literal;
                        }

                        if ((parent.getValue() instanceof J.VariableDeclarations.NamedVariable && !isPrivateStaticFinalVariable(parent.getValue())) ||
                             parent.getValue() instanceof J.NewClass ||
                             parent.getValue() instanceof J.MethodInvocation) {

                            result.duplicateLiterals.computeIfAbsent(((String) literal.getValue()), k -> new ArrayList<>(1)).add(literal);
                        }
                    }
                    return literal;
                }

            }.visit(inClass, 0);
            Map<String, List<J.Literal>> filteredMap = new TreeMap<>(Comparator.reverseOrder());
            for (Map.Entry<String, List<J.Literal>> entry : result.duplicateLiterals.entrySet()) {
                if (entry.getValue().size() >= 3) {
                    filteredMap.put(entry.getKey(), entry.getValue());
                }
            }
            result.duplicateLiterals = filteredMap;

            return result;
        }
    }

    /**
     * ReplaceStringLiterals in a class with a reference to a `private static final String` with the provided variable name.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class ReplaceStringLiterals extends JavaVisitor<ExecutionContext> {
        J.ClassDeclaration isClass;
        Map<J.Literal, String> replacements;

        @Override
        public J visitLiteral(J.Literal literal, ExecutionContext ctx) {
            String variableName = replacements.get(literal);
            if (variableName != null) {
                assert isClass.getType() != null;
                return new J.Identifier(
                        randomId(),
                        literal.getPrefix(),
                        literal.getMarkers(),
                        emptyList(),
                        variableName,
                        JavaType.Primitive.String,
                        new JavaType.Variable(
                                null,
                                Flag.flagsToBitMap(EnumSet.of(Flag.Private, Flag.Static, Flag.Final)),
                                variableName,
                                isClass.getType(),
                                JavaType.Primitive.String,
                                emptyList()
                        )
                );
            }
            return literal;
        }
    }
}
