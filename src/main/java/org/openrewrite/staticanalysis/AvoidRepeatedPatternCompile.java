package org.openrewrite.staticanalysis;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class AvoidRepeatedPatternCompile extends Recipe {

    private static final MethodMatcher PATTERN_COMPILE =
            new MethodMatcher(
                    "java.util.regex.Pattern compile(java.lang.String)"
            );

    private static final MethodMatcher PATTERN_COMPILE_WITH_FLAGS =
            new MethodMatcher(
                    "java.util.regex.Pattern compile(java.lang.String, int)"
            );

    private static final String METHOD_CANDIDATES =
            "avoidRepeatedPatternCompile.methodCandidates";

    private static final String CLASS_CANDIDATES =
            "avoidRepeatedPatternCompile.classCandidates";

    private static final String USED_FIELD_NAMES =
            "avoidRepeatedPatternCompile.usedFieldNames";

    @Override
    public String getDisplayName() {
        return "Avoid repeated `Pattern.compile()` calls";
    }

    @Override
    public String getDescription() {
        return "Moves `Pattern.compile()` calls with constant regular expressions " +
                "from method-local variables into private static final fields.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(
                    J.CompilationUnit compilationUnit,
                    ExecutionContext ctx) {

                J.CompilationUnit cu =
                        super.visitCompilationUnit(compilationUnit, ctx);

                if (cu != compilationUnit) {
                    maybeAddImport("java.util.regex.Pattern");
                }

                return cu;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration classDeclaration,
                    ExecutionContext ctx) {

                J.ClassDeclaration cd =
                        super.visitClassDeclaration(classDeclaration, ctx);

                List<Candidate> candidates =
                        getCursor().getMessage(CLASS_CANDIDATES);

                if (candidates == null || candidates.isEmpty()) {
                    return cd;
                }

                /*
                 * Add in reverse order because firstStatement()
                 * always inserts at the beginning.
                 */
                for (int i = candidates.size() - 1; i >= 0; i--) {

                    Candidate candidate = candidates.get(i);

                    JavaTemplate template =
                            JavaTemplate.builder(
                                                "private static final Pattern " +
                                                        candidate.constantName +
                                                        " = #{any(java.util.regex.Pattern)};"
                                        )
                                        .imports("java.util.regex.Pattern")
                                        .build();

                    cd = template.apply(
                            updateCursor(cd),
                            cd.getBody()
                              .getCoordinates()
                              .firstStatement(),
                            candidate.initializer
                    );
                }

                /*
                 * FIX:
                 *
                 * JavaTemplate may leave:
                 *
                 * private static final Pattern EMAIL_PATTERN = ...;
                 *
                 * private static final Pattern PHONE_PATTERN = ...;
                 *
                 * Remove the extra blank line between consecutive
                 * generated Pattern constants.
                 */
                if (candidates.size() > 1) {

                    List<Statement> statements =
                            new ArrayList<>(
                                    cd.getBody().getStatements()
                            );

                    String firstWhitespace =
                            statements.get(0)
                                      .getPrefix()
                                      .getWhitespace();

                    int lastNewline =
                            firstWhitespace.lastIndexOf('\n');

                    String indentation =
                            lastNewline >= 0
                                    ? firstWhitespace.substring(
                                    lastNewline + 1
                            )
                                    : "";

                    /*
                     * The first N statements are the generated
                     * Pattern constants.
                     *
                     * Keep only one newline between them.
                     */
                    for (int i = 1;
                         i < candidates.size();
                         i++) {

                        statements.set(
                                i,
                                statements.get(i)
                                          .withPrefix(
                                                  Space.format(
                                                          "\n" +
                                                                  indentation
                                                  )
                                          )
                        );
                    }

                    cd = cd.withBody(
                            cd.getBody()
                              .withStatements(statements)
                    );
                }

                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(
                    J.MethodDeclaration method,
                    ExecutionContext ctx) {

                if (method.getBody() == null) {
                    return super.visitMethodDeclaration(method, ctx);
                }

                J.ClassDeclaration enclosingClass =
                        getCursor()
                                .firstEnclosing(
                                        J.ClassDeclaration.class
                                );

                if (enclosingClass == null ||
                        enclosingClass.getType() == null) {

                    return super.visitMethodDeclaration(method, ctx);
                }

                /*
                 * Keep the first version conservative.
                 * Don't process inner/nested classes.
                 */
                if (enclosingClass
                        .getType()
                        .getOwningClass() != null) {

                    return super.visitMethodDeclaration(method, ctx);
                }

                /*
                 * Only normal classes for now.
                 */
                if (enclosingClass.getKind() !=
                        J.ClassDeclaration.Kind.Type.Class) {

                    return super.visitMethodDeclaration(method, ctx);
                }

                Cursor classCursor =
                        getCursor().dropParentUntil(
                                J.ClassDeclaration.class::isInstance
                        );

                Set<String> usedFieldNames =
                        classCursor.computeMessageIfAbsent(
                                USED_FIELD_NAMES,
                                key ->
                                        collectExistingFieldNames(
                                                enclosingClass
                                        )
                        );

                List<Candidate> candidates =
                        new ArrayList<>();

                /*
                 * Version 1 only handles direct method statements.
                 *
                 * Example:
                 *
                 * void validate() {
                 *     Pattern p = Pattern.compile("...");
                 * }
                 */
                for (Statement statement :
                        method.getBody().getStatements()) {

                    if (!(statement instanceof
                            J.VariableDeclarations)) {
                        continue;
                    }

                    J.VariableDeclarations declarations =
                            (J.VariableDeclarations) statement;

                    /*
                     * Don't handle:
                     *
                     * Pattern a = ..., b = ...;
                     */
                    if (declarations
                            .getVariables()
                            .size() != 1) {

                        continue;
                    }

                    J.VariableDeclarations.NamedVariable variable =
                            declarations
                                    .getVariables()
                                    .get(0);

                    Expression initializer =
                            variable.getInitializer();

                    if (!(initializer instanceof
                            J.MethodInvocation)) {

                        continue;
                    }

                    J.MethodInvocation compile =
                            (J.MethodInvocation) initializer;

                    boolean oneArgument =
                            PATTERN_COMPILE.matches(compile);

                    boolean twoArguments =
                            PATTERN_COMPILE_WITH_FLAGS.matches(
                                    compile
                            );

                    if (!oneArgument && !twoArguments) {
                        continue;
                    }

                    List<Expression> arguments =
                            compile.getArguments();

                    if (arguments.isEmpty()) {
                        continue;
                    }

                    /*
                     * Version 1 only accepts literal regexes.
                     *
                     * YES:
                     *
                     * Pattern.compile("[0-9]+")
                     *
                     * NO:
                     *
                     * Pattern.compile(regex)
                     *
                     * NO:
                     *
                     * Pattern.compile(createRegex())
                     */
                    Expression regex =
                            arguments.get(0);

                    if (!(regex instanceof J.Literal)) {
                        continue;
                    }

                    Object literalValue =
                            ((J.Literal) regex).getValue();

                    if (!(literalValue instanceof String)) {
                        continue;
                    }

                    /*
                     * For:
                     *
                     * Pattern.compile(regex, flags)
                     *
                     * make sure flags are safe to move
                     * to class scope.
                     */
                    if (twoArguments) {

                        if (arguments.size() != 2 ||
                                !isConstantExpression(
                                        arguments.get(1)
                                )) {

                            continue;
                        }
                    }

                    JavaType.Variable variableType =
                            variable.getVariableType();

                    /*
                     * Need type attribution so identifier
                     * replacement is safe.
                     */
                    if (variableType == null) {
                        continue;
                    }

                    String constantName =
                            createConstantName(
                                    variable.getSimpleName(),
                                    method.getSimpleName()
                            );

                    constantName =
                            makeUnique(
                                    constantName,
                                    usedFieldNames
                            );

                    usedFieldNames.add(constantName);

                    JavaType.Variable newFieldType =
                            variableType
                                    .withName(constantName)
                                    .withOwner(
                                            enclosingClass.getType()
                                    );

                    Set<Flag> flags =
                            new HashSet<>(
                                    newFieldType.getFlags()
                            );

                    flags.add(Flag.Private);
                    flags.add(Flag.Static);
                    flags.add(Flag.Final);

                    newFieldType =
                            newFieldType.withFlags(flags);

                    Candidate candidate =
                            new Candidate(
                                    declarations.getId(),
                                    variableType,
                                    newFieldType,
                                    constantName,
                                    compile.withPrefix(
                                            Space.EMPTY
                                    )
                            );

                    candidates.add(candidate);
                }

                if (candidates.isEmpty()) {
                    return super.visitMethodDeclaration(
                            method,
                            ctx
                    );
                }

                /*
                 * Child visitors use this to:
                 *
                 * 1. replace identifiers
                 * 2. remove local declarations
                 */
                getCursor().putMessage(
                        METHOD_CANDIDATES,
                        candidates
                );

                /*
                 * Tell enclosing class which static fields
                 * need to be generated.
                 */
                List<Candidate> classCandidates =
                        classCursor.computeMessageIfAbsent(
                                CLASS_CANDIDATES,
                                key -> new ArrayList<>()
                        );

                classCandidates.addAll(candidates);

                return super.visitMethodDeclaration(
                        method,
                        ctx
                );
            }

            /*
             * Replace:
             *
             * emailPattern.matcher(...)
             *
             * with:
             *
             * EMAIL_PATTERN.matcher(...)
             */
            @Override
            public J.Identifier visitIdentifier(
                    J.Identifier identifier,
                    ExecutionContext ctx) {

                J.Identifier id =
                        super.visitIdentifier(
                                identifier,
                                ctx
                        );

                List<Candidate> candidates =
                        getCursor().getNearestMessage(
                                METHOD_CANDIDATES
                        );

                if (candidates == null) {
                    return id;
                }

                for (Candidate candidate : candidates) {

                    /*
                     * Match the actual variable metadata,
                     * not just its String name.
                     */
                    if (Objects.equals(
                            id.getFieldType(),
                            candidate.originalVariableType)) {

                        return id
                                .withSimpleName(
                                        candidate.constantName
                                )
                                .withFieldType(
                                        candidate.newFieldType
                                );
                    }
                }

                return id;
            }

            /*
             * Remove:
             *
             * Pattern emailPattern =
             *     Pattern.compile(...);
             */
            @Override
            public J.VariableDeclarations
            visitVariableDeclarations(
                    J.VariableDeclarations declarations,
                    ExecutionContext ctx) {

                List<Candidate> candidates =
                        getCursor().getNearestMessage(
                                METHOD_CANDIDATES
                        );

                if (candidates != null) {

                    for (Candidate candidate :
                            candidates) {

                        if (declarations
                                .getId()
                                .equals(
                                        candidate.declarationId
                                )) {

                            return null;
                        }
                    }
                }

                return super.visitVariableDeclarations(
                        declarations,
                        ctx
                );
            }
        };
    }

    /*
     * Checks whether Pattern.compile(..., flags)
     * uses flags that are safe to move to a
     * static field.
     */
    private static boolean isConstantExpression(
            Expression expression) {

        if (expression instanceof J.Literal) {
            return true;
        }

        if (expression instanceof J.Identifier) {

            JavaType.Variable variable =
                    ((J.Identifier) expression)
                            .getFieldType();

            return variable != null &&
                    variable.hasFlags(
                            Flag.Static,
                            Flag.Final
                    );
        }

        if (expression instanceof J.FieldAccess) {

            JavaType.Variable variable =
                    ((J.FieldAccess) expression)
                            .getName()
                            .getFieldType();

            return variable != null &&
                    variable.hasFlags(
                            Flag.Static,
                            Flag.Final
                    );
        }

        /*
         * Supports:
         *
         * Pattern.CASE_INSENSITIVE |
         * Pattern.UNICODE_CASE
         */
        if (expression instanceof J.Binary) {

            J.Binary binary =
                    (J.Binary) expression;

            return isConstantExpression(
                    binary.getLeft()
            ) &&
                    isConstantExpression(
                            binary.getRight()
                    );
        }

        return false;
    }

    /*
     * emailPattern -> EMAIL_PATTERN
     * phonePattern -> PHONE_PATTERN
     *
     * If the variable is simply:
     *
     * Pattern pattern
     *
     * use the method name where possible.
     *
     * isValidEmail -> EMAIL_PATTERN
     */
    private static String createConstantName(
            String variableName,
            String methodName) {

        if (!"pattern".equals(variableName)) {
            return toUpperSnakeCase(variableName);
        }

        String subject = methodName;

        String[] prefixes = {
                "isValid",
                "validate",
                "valid",
                "matches",
                "match"
        };

        for (String prefix : prefixes) {

            if (subject.startsWith(prefix) &&
                    subject.length() > prefix.length()) {

                subject =
                        subject.substring(
                                prefix.length()
                        );

                break;
            }
        }

        if (subject.isEmpty()) {
            return "PATTERN";
        }

        String result =
                toUpperSnakeCase(subject);

        if (!result.endsWith("_PATTERN")) {
            result += "_PATTERN";
        }

        return result;
    }

    /*
     * emailPattern -> EMAIL_PATTERN
     * phonePattern -> PHONE_PATTERN
     */
    private static String toUpperSnakeCase(
            String name) {

        StringBuilder result =
                new StringBuilder();

        for (int i = 0;
             i < name.length();
             i++) {

            char current =
                    name.charAt(i);

            if (!Character.isLetterOrDigit(current)) {

                if (result.length() > 0 &&
                        result.charAt(
                                result.length() - 1
                        ) != '_') {

                    result.append('_');
                }

                continue;
            }

            if (Character.isUpperCase(current) &&
                    i > 0) {

                char previous =
                        name.charAt(i - 1);

                if (Character.isLowerCase(previous) ||
                        Character.isDigit(previous)) {

                    result.append('_');
                }
            }

            result.append(
                    Character.toUpperCase(current)
            );
        }

        return result.toString();
    }

    /*
     * Avoid duplicate generated field names.
     *
     * EMAIL_PATTERN
     * EMAIL_PATTERN_2
     * EMAIL_PATTERN_3
     */
    private static String makeUnique(
            String desiredName,
            Set<String> usedNames) {

        if (!usedNames.contains(desiredName)) {
            return desiredName;
        }

        int number = 2;

        while (usedNames.contains(
                desiredName + "_" + number)) {

            number++;
        }

        return desiredName + "_" + number;
    }

    /*
     * Collect fields that already exist in the class
     * so generated constants don't collide with them.
     */
    private static Set<String>
    collectExistingFieldNames(
            J.ClassDeclaration classDeclaration) {

        Set<String> names =
                new HashSet<>();

        for (Statement statement :
                classDeclaration
                        .getBody()
                        .getStatements()) {

            if (!(statement instanceof
                    J.VariableDeclarations)) {

                continue;
            }

            J.VariableDeclarations declarations =
                    (J.VariableDeclarations) statement;

            for (J.VariableDeclarations.NamedVariable variable :
                    declarations.getVariables()) {

                names.add(
                        variable.getSimpleName()
                );
            }
        }

        return names;
    }

    private static class Candidate {

        private final UUID declarationId;

        private final JavaType.Variable
                originalVariableType;

        private final JavaType.Variable
                newFieldType;

        private final String constantName;

        private final J.MethodInvocation
                initializer;

        private Candidate(
                UUID declarationId,
                JavaType.Variable originalVariableType,
                JavaType.Variable newFieldType,
                String constantName,
                J.MethodInvocation initializer) {

            this.declarationId =
                    declarationId;

            this.originalVariableType =
                    originalVariableType;

            this.newFieldType =
                    newFieldType;

            this.constantName =
                    constantName;

            this.initializer =
                    initializer;
        }
    }
}
