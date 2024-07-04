/*
 * Copyright 2020 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("java.lang.String", false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) tree;
                    Optional<JavaSourceSet> sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class);
                    if (!Boolean.TRUE.equals(includeTestSources) && !(sourceSet.isPresent() && "main".equals(sourceSet.get().getName()))) {
                        return cu;
                    }
                }
                return super.visit(tree, ctx);
            }

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
                Set<String> variableNames = duplicateLiteralInfo.getVariableNames();
                Map<String, String> fieldValueToFieldName = duplicateLiteralInfo.getFieldValueToFieldName();
                String classFqn = classDecl.getType().getFullyQualifiedName();
                Map<J.Literal, String> replacements = new HashMap<>();
                for (Map.Entry<String, List<J.Literal>> entry : duplicateLiteralsMap.entrySet()) {
                    String valueOfLiteral = entry.getKey();
                    List<J.Literal> duplicateLiterals = duplicateLiteralsMap.get(valueOfLiteral);
                    String classFieldName = fieldValueToFieldName.get(valueOfLiteral);
                    String variableName;
                    if (classFieldName != null) {
                        variableName = getNameWithoutShadow(classFieldName, variableNames);
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
                        String insertStatement = "private static final String " + variableName + " = #{any(String)};";
                        if (classDecl.getKind() == J.ClassDeclaration.Kind.Type.Enum) {
                            J.EnumValueSet enumValueSet = classDecl.getBody().getStatements().stream()
                                    .filter(J.EnumValueSet.class::isInstance)
                                    .map(J.EnumValueSet.class::cast)
                                    .findFirst()
                                    .orElse(null);

                            if (enumValueSet != null) {
                                // "Temporary" work around due to an issue in the JavaTemplate related to BlockStatementTemplateGenerator#enumClassDeclaration.
                                Space singleSpace = Space.build(" ", emptyList());
                                Expression literal = duplicateLiterals.get(0).withId(randomId());
                                J.Modifier privateModifier = new J.Modifier(randomId(), Space.build("\n", emptyList()), Markers.EMPTY, null, J.Modifier.Type.Private, emptyList());
                                J.Modifier staticModifier = new J.Modifier(randomId(), singleSpace, Markers.EMPTY, null, J.Modifier.Type.Static, emptyList());
                                J.Modifier finalModifier = new J.Modifier(randomId(), singleSpace, Markers.EMPTY, null, J.Modifier.Type.Final, emptyList());
                                J.VariableDeclarations variableDeclarations = autoFormat(new J.VariableDeclarations(
                                        randomId(),
                                        Space.EMPTY,
                                        Markers.EMPTY,
                                        emptyList(),
                                        Arrays.asList(privateModifier, staticModifier, finalModifier),
                                        new J.Identifier(
                                                randomId(),
                                                singleSpace,
                                                Markers.EMPTY,
                                                emptyList(),
                                                "String",
                                                JavaType.ShallowClass.build("java.lang.String"),
                                                null),
                                        null,
                                        emptyList(),
                                        singletonList(JRightPadded.build(new J.VariableDeclarations.NamedVariable(
                                                randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                new J.Identifier(
                                                        randomId(),
                                                        Space.EMPTY,
                                                        Markers.EMPTY,
                                                        emptyList(),
                                                        variableName,
                                                        JavaType.ShallowClass.build("java.lang.String"),
                                                        null),
                                                emptyList(),
                                                JLeftPadded.build(literal).withBefore(singleSpace),
                                                null)))
                                ), ctx, new Cursor(getCursor(), classDecl.getBody()));

                                // Insert the new statement after the EnumValueSet.
                                List<Statement> statements = new ArrayList<>(classDecl.getBody().getStatements().size() + 1);
                                boolean addedNewStatement = false;
                                for (Statement statement : classDecl.getBody().getStatements()) {
                                    if (!(statement instanceof J.EnumValueSet) && !addedNewStatement) {
                                        statements.add(variableDeclarations);
                                        addedNewStatement = true;
                                    }
                                    statements.add(statement);
                                }
                                classDecl = classDecl.withBody(classDecl.getBody().withStatements(statements));
                            }
                        } else {
                            classDecl = classDecl.withBody(
                                    JavaTemplate.builder(insertStatement).build()
                                            .apply(new Cursor(getCursor(), classDecl.getBody()), classDecl.getBody().getCoordinates().firstStatement(), replaceLiteral));
                        }
                    }
                    variableNames.add(variableName);
                    entry.getValue().forEach(v -> replacements.put(v, variableName));
                }
                return replacements.isEmpty() ? classDecl :
                        new ReplaceStringLiterals(classDecl, replacements).visitNonNull(classDecl, ctx, getCursor().getParent());
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
                    if (i > 0 && newName.lastIndexOf("_") != newName.length() - 1 &&
                        (Character.isUpperCase(c) && prevIsLower || !prevIsCharacter)) {
                        newName.append("_");
                    }
                    prevIsCharacter = Character.isLetterOrDigit(c);
                    if (!prevIsCharacter) {
                        continue;
                    }
                    if (newName.length() == 0 && Character.isDigit(c)) {
                        newName.append("A_");
                    }
                    newName.append(Character.toUpperCase(c));
                    prevIsLower = Character.isLowerCase(c);
                }
                return VariableNameUtils.normalizeName(newName.toString());
            }
        });
    }

    private static boolean isPrivateStaticFinalVariable(J.VariableDeclarations declaration) {
        return declaration.hasModifier(J.Modifier.Type.Private) &&
               declaration.hasModifier(J.Modifier.Type.Static) &&
               declaration.hasModifier(J.Modifier.Type.Final);
    }

    @Value
    private static class DuplicateLiteralInfo {
        Set<String> variableNames;
        Map<String, String> fieldValueToFieldName;
        @NonFinal
        Map<String, List<J.Literal>> duplicateLiterals;

        public static DuplicateLiteralInfo find(J.ClassDeclaration inClass) {
            DuplicateLiteralInfo result = new DuplicateLiteralInfo(new LinkedHashSet<>(), new LinkedHashMap<>(), new HashMap<>());
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
                    J.VariableDeclarations declaration = getCursor().firstEnclosing(J.VariableDeclarations.class);
                    if (parentScope.getValue() instanceof J.MethodDeclaration ||
                        (parentScope.getValue() instanceof J.ClassDeclaration && declaration != null &&
                         // `private static final String`(s) are handled separately by `FindExistingPrivateStaticFinalFields`.
                         !(isPrivateStaticFinalVariable(declaration) && v.getInitializer() instanceof J.Literal &&
                           ((J.Literal) v.getInitializer()).getValue() instanceof String))) {
                        result.variableNames.add(v.getSimpleName());
                    }
                    if (parentScope.getValue() instanceof J.ClassDeclaration &&
                        declaration != null && isPrivateStaticFinalVariable(declaration) &&
                        v.getInitializer() instanceof J.Literal &&
                        ((J.Literal) v.getInitializer()).getValue() instanceof String) {
                        String value = (String) (((J.Literal) v.getInitializer()).getValue());
                        result.fieldValueToFieldName.putIfAbsent(value, v.getSimpleName());
                    }
                    return v;
                }

                @Override
                public J.Literal visitLiteral(J.Literal literal, Integer integer) {
                    if (JavaType.Primitive.String.equals(literal.getType()) &&
                        literal.getValue() instanceof String &&
                        ((String) literal.getValue()).length() >= 5) {

                        Cursor parent = getCursor().dropParentUntil(is -> is instanceof J.ClassDeclaration ||
                                                                          is instanceof J.Annotation ||
                                                                          is instanceof J.VariableDeclarations ||
                                                                          is instanceof J.NewClass ||
                                                                          is instanceof J.MethodInvocation);
                        // EnumValue can accept constructor arguments, including string literals
                        // But the static field can't be placed before them, so these literals are ineligible for replacement
                        if (parent.getValue() instanceof J.NewClass && parent.firstEnclosing(J.EnumValueSet.class) != null) {
                            return literal;
                        }

                        if ((parent.getValue() instanceof J.VariableDeclarations &&
                             ((J.VariableDeclarations) parent.getValue()).hasModifier(J.Modifier.Type.Final) &&
                             !(((J.VariableDeclarations) parent.getValue()).hasModifier(J.Modifier.Type.Private) && ((J.VariableDeclarations) parent.getValue()).hasModifier(J.Modifier.Type.Static))) ||
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
