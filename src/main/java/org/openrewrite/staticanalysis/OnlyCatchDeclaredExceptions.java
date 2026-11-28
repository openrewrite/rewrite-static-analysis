/*
 * Copyright 2025 the original author or authors.
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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

public class OnlyCatchDeclaredExceptions extends Recipe {

    private static final String JAVA_LANG_EXCEPTION = "java.lang.Exception";
    private static final String JAVA_LANG_RUNTIME_EXCEPTION = "java.lang.RuntimeException";
    private static final String JAVA_LANG_ERROR = "java.lang.Error";
    private static final String JAVA_LANG_THROWABLE = "java.lang.Throwable";
    private static final Pattern LOCAL_CLASS_NAME = Pattern.compile("\\$\\d");

    @Getter
    final String displayName = "Replace `catch(Exception)` with specific declared exceptions thrown in the try block";

    @Getter
    final String description = "Replaces `catch(Exception e)` blocks with a multi-catch block " +
            "(`catch (SpecificException1 | SpecificException2 | RuntimeException e)`) containing the checked exceptions " +
            "declared thrown by method or constructor invocations within the `try` block that are not already caught " +
            "by more specific `catch` clauses. " +
            "Catching a broad `Exception` type can unintentionally swallow runtime exceptions that indicate programming errors, " +
            "making bugs harder to detect and diagnose. As the recipe cannot know whether those runtime exceptions are meant " +
            "to be caught, it lists `RuntimeException` explicitly so that the catch keeps handling them. " +
            "The catch is left alone when the `try` block can throw `Exception` itself or what it throws cannot be determined. " +
            "Calls into Kotlin, Scala or Groovy classes and to Lombok `@SneakyThrows` methods in the same sources are left alone, " +
            "but a checked exception that a called Java method throws without declaring it, as a compiled `@SneakyThrows` " +
            "method can, is no longer caught.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("CWE-396", "RSPEC-S2221"));

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(15);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try visitTry(J.Try aTry, ExecutionContext ctx) {
                J.Try t = super.visitTry(aTry, ctx);
                List<J.Try.Catch> catches = t.getCatches();
                for (int i = 0; i < catches.size(); i++) {
                    if (TypeUtils.isOfClassType(catches.get(i).getParameter().getType(), JAVA_LANG_EXCEPTION)) {
                        // A catch after `catch (Exception e)` can't catch any other exception, so there is at most one
                        int generic = i;
                        return t.withCatches(ListUtils.map(catches, (j, c) -> j == generic ? narrow(t, c, catches.subList(0, j)) : c));
                    }
                }
                return t;
            }

            private J.Try.Catch narrow(J.Try aTry, J.Try.Catch aCatch, List<J.Try.Catch> earlierCatches) {
                List<JavaType> thrown = new ThrownExceptions().ofTryBlock(aTry);
                if (thrown == null) {
                    return aCatch;
                }
                List<JavaType> earlierCaught = caughtTypes(earlierCatches);
                if (earlierCaught == null) {
                    return aCatch;
                }

                boolean throwsUnchecked = false;
                List<JavaType.FullyQualified> checked = new ArrayList<>();
                for (JavaType type : thrown) {
                    if (TypeUtils.isOfClassType(type, JAVA_LANG_RUNTIME_EXCEPTION)) {
                        // Possibly the shallow type this recipe wrote in a nested catch
                        throwsUnchecked = true;
                        continue;
                    }
                    if (!(type instanceof JavaType.FullyQualified) || type instanceof JavaType.Unknown ||
                        !TypeUtils.isAssignableTo(JAVA_LANG_THROWABLE, type)) {
                        // Type variables, missing types or an incomplete type hierarchy
                        return aCatch;
                    }
                    if (TypeUtils.isOfClassType(type, JAVA_LANG_EXCEPTION) || TypeUtils.isOfClassType(type, JAVA_LANG_THROWABLE)) {
                        // `Exception` is really thrown, so it has to be caught
                        return aCatch;
                    }
                    if (TypeUtils.isAssignableTo(JAVA_LANG_RUNTIME_EXCEPTION, type) || TypeUtils.isAssignableTo(JAVA_LANG_ERROR, type)) {
                        throwsUnchecked = true;
                    } else if (TypeUtils.isAssignableTo(JAVA_LANG_EXCEPTION, type) && !isAssignableToAny(type, earlierCaught)) {
                        checked.add((JavaType.FullyQualified) type);
                    }
                }
                if (checked.isEmpty() && !throwsUnchecked) {
                    return aCatch;
                }

                List<JavaType.FullyQualified> alternatives = mostGeneral(checked);
                alternatives.sort(Comparator.comparing(OnlyCatchDeclaredExceptions::simpleName)
                        .thenComparing(JavaType.FullyQualified::getFullyQualifiedName));
                JavaType.FullyQualified runtimeException = runtimeExceptionType(thrown);
                boolean catchesRuntimeExceptions = !isAssignableToAny(runtimeException, earlierCaught);
                if (catchesRuntimeExceptions) {
                    alternatives.add(runtimeException);
                }
                if (alternatives.isEmpty()) {
                    return aCatch;
                }
                for (JavaType.FullyQualified alternative : alternatives) {
                    if (LOCAL_CLASS_NAME.matcher(alternative.getFullyQualifiedName()).find() || !isAccessible(alternative)) {
                        // A local or anonymous class has no name to write, and a type of another package may not be visible
                        return aCatch;
                    }
                }
                // Unless it is `X | RuntimeException`, the parameter's type is no longer `Exception`,
                // which can make a cast, an `instanceof` or a `var` invalid or select another overload
                if (!usesStaySafe(aCatch, alternatives, alternatives.size() == 1 || !catchesRuntimeExceptions)) {
                    return aCatch;
                }
                return withAlternatives(aCatch, alternatives);
            }

            private boolean usesStaySafe(J.Try.Catch aCatch, List<JavaType.FullyQualified> alternatives, boolean typeChanges) {
                JavaType exception = aCatch.getParameter().getType();
                String name = parameterName(aCatch);
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean ok) {
                        // A multi-catch parameter is final
                        Expression variable = assignment.getVariable().unwrap();
                        if (variable instanceof J.Identifier && ((J.Identifier) variable).getSimpleName().equals(name)) {
                            ok.set(false);
                        }
                        return super.visitAssignment(assignment, ok);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean ok) {
                        if (typeChanges && identifier.getSimpleName().equals(name) &&
                            !isSafeUse(identifier, getCursor().getParentTreeCursor().getValue())) {
                            ok.set(false);
                        }
                        return identifier;
                    }

                    private boolean isSafeUse(J.Identifier identifier, Object parent) {
                        if (parent instanceof J.MethodInvocation) {
                            J.MethodInvocation mi = (J.MethodInvocation) parent;
                            if (mi.getSelect() == identifier) {
                                // `getClass()` returns a `Class` of the parameter's static type
                                return !"getClass".equals(mi.getSimpleName());
                            }
                            int index = mi.getArguments().indexOf(identifier);
                            JavaType.Method method = mi.getMethodType();
                            if (index < 0 || method == null) {
                                return false;
                            }
                            List<JavaType.@Nullable FullyQualified> owners = new ArrayList<>();
                            if (mi.getSelect() != null) {
                                owners.add(TypeUtils.asFullyQualified(mi.getSelect().getType()));
                            } else {
                                // An unqualified call may resolve to a method of any enclosing class
                                for (Cursor c = getCursor(); c != null; c = c.getParent()) {
                                    if (c.getValue() instanceof J.ClassDeclaration) {
                                        owners.add(((J.ClassDeclaration) c.getValue()).getType());
                                    } else if (c.getValue() instanceof J.NewClass) {
                                        owners.add(TypeUtils.asFullyQualified(((J.NewClass) c.getValue()).getType()));
                                    }
                                }
                            }
                            List<JavaType.Method> candidates = methodsIn(owners, method.getName());
                            if (candidates != null && candidates.isEmpty() && mi.getSelect() == null) {
                                // Only then can the call resolve to a statically imported method
                                candidates = methodsIn(staticImports(method.getName()), method.getName());
                            }
                            return sameOverload(method, candidates, index);
                        }
                        if (parent instanceof J.NewClass) {
                            J.NewClass nc = (J.NewClass) parent;
                            int index = nc.getArguments().indexOf(identifier);
                            if (index < 0 || nc.getConstructorType() == null) {
                                return false;
                            }
                            // The constructor of an anonymous class passes its arguments on to one of its supertype's
                            List<JavaType.@Nullable FullyQualified> supertypes = new ArrayList<>();
                            if (nc.getBody() != null) {
                                supertypes.add(nc.getConstructorType().getDeclaringType().getSupertype());
                                supertypes.add(TypeUtils.asFullyQualified(nc.getClazz() == null ? null : nc.getClazz().getType()));
                            }
                            return sameOverload(nc.getConstructorType(), methodsIn(supertypes, "<constructor>"), index);
                        }
                        if (parent instanceof J.InstanceOf && ((J.InstanceOf) parent).getExpression() == identifier) {
                            J clazz = ((J.InstanceOf) parent).getClazz();
                            return clazz instanceof TypedTree && isRelated(((TypedTree) clazz).getType());
                        }
                        if (parent instanceof J.TypeCast && ((J.TypeCast) parent).getExpression() == identifier) {
                            return isRelated(((J.TypeCast) parent).getClazz().getTree().getType());
                        }
                        if (parent instanceof J.Throw) {
                            return true;
                        }
                        if (parent instanceof J.Return) {
                            // In a lambda the returned type is inferred
                            return getCursor().dropParentUntil(t -> t instanceof J.Lambda || t instanceof J.MethodDeclaration)
                                    .getValue() instanceof J.MethodDeclaration;
                        }
                        if (parent instanceof J.Binary) {
                            return TypeUtils.isString(((J.Binary) parent).getType());
                        }
                        if (parent instanceof J.VariableDeclarations.NamedVariable) {
                            J.VariableDeclarations.NamedVariable variable = (J.VariableDeclarations.NamedVariable) parent;
                            J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                            return variable.getInitializer() == identifier && declarations != null &&
                                   declarations.getTypeExpression() != null &&
                                   !(declarations.getTypeExpression() instanceof J.Identifier &&
                                     "var".equals(((J.Identifier) declarations.getTypeExpression()).getSimpleName())) &&
                                   TypeUtils.isAssignableTo(declarations.getType(), exception);
                        }
                        if (parent instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) parent;
                            return assignment.getAssignment() == identifier &&
                                   TypeUtils.isAssignableTo(assignment.getVariable().getType(), exception);
                        }
                        return false;
                    }

                    // Whether a cast or `instanceof` to this type still compiles on a parameter of the single class type
                    private boolean isRelated(@Nullable JavaType target) {
                        if (alternatives.size() != 1 || !(target instanceof JavaType.Class)) {
                            return false;
                        }
                        JavaType.FullyQualified narrowed = alternatives.get(0);
                        if (((JavaType.Class) target).getKind() == JavaType.FullyQualified.Kind.Interface) {
                            return !narrowed.hasFlags(Flag.Final) || TypeUtils.isAssignableTo(target, narrowed);
                        }
                        return TypeUtils.isAssignableTo(narrowed, target) || TypeUtils.isAssignableTo(target, narrowed);
                    }

                    private List<JavaType.@Nullable FullyQualified> staticImports(String methodName) {
                        List<JavaType.@Nullable FullyQualified> types = new ArrayList<>();
                        for (J.Import anImport : getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getImports()) {
                            String name = anImport.getQualid().getSimpleName();
                            if (anImport.isStatic() && (name.equals(methodName) || "*".equals(name))) {
                                types.add(TypeUtils.asFullyQualified(anImport.getQualid().getTarget().getType()));
                            }
                        }
                        return types;
                    }

                    private boolean acceptsTheSame(JavaType parameter) {
                        return TypeUtils.isAssignableTo(parameter, exception) ||
                               alternatives.stream().noneMatch(alternative -> TypeUtils.isAssignableTo(parameter, alternative));
                    }

                    /**
                     * Whether no overload among the candidates or in the declaring type takes the narrowed type at this
                     * position (or as a varargs element) but not `Exception`, and no type is inferred from it.
                     */
                    private boolean sameOverload(JavaType.Method method, @Nullable List<JavaType.Method> candidates, int index) {
                        if (candidates == null) {
                            return false;
                        }
                        candidates.addAll(methodsNamed(method.getDeclaringType(), method.getName(), new HashSet<>()));
                        for (JavaType.Method candidate : candidates) {
                            List<JavaType> parameters = candidate.getParameterTypes();
                            if (index >= parameters.size() && !(candidate.hasFlags(Flag.Varargs) && !parameters.isEmpty())) {
                                continue;
                            }
                            JavaType parameter = parameters.get(Math.min(index, parameters.size() - 1));
                            JavaType.Array array = TypeUtils.asArray(parameter);
                            // A type inferred from the parameter changes with it, also for overload resolution
                            if (containsTypeVariable(parameter, new HashSet<>()) || !acceptsTheSame(parameter) ||
                                array != null && !acceptsTheSame(array.getElemType())) {
                                return false;
                            }
                        }
                        return true;
                    }
                }.reduce(aCatch.getBody(), new AtomicBoolean(true), new Cursor(getCursor(), aCatch)).get();
            }

            private boolean isAccessible(JavaType.FullyQualified type) {
                J.CompilationUnit cu = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class);
                String packageName = cu.getPackageDeclaration() == null ? "" : cu.getPackageDeclaration().getPackageName();
                // A private type is visible in the whole top-level class it is declared in
                J.ClassDeclaration enclosingTopLevel = null;
                for (Cursor c = getCursor(); c != null; c = c.getParent()) {
                    if (c.getValue() instanceof J.ClassDeclaration) {
                        enclosingTopLevel = c.getValue();
                    }
                }
                if (enclosingTopLevel != null && enclosingTopLevel.getType() != null &&
                    TypeUtils.isOfClassType(topLevel(type), enclosingTopLevel.getType().getFullyQualifiedName())) {
                    return true;
                }
                boolean samePackage = type.getPackageName().equals(packageName);
                for (JavaType.FullyQualified t = type; t != null; t = t.getOwningClass()) {
                    if (!t.hasFlags(Flag.Public) && !(samePackage && !t.hasFlags(Flag.Private))) {
                        return false;
                    }
                }
                return true;
            }

            private J.Try.Catch withAlternatives(J.Try.Catch aCatch, List<JavaType.FullyQualified> alternatives) {
                J.CompilationUnit cu = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class);
                Map<String, Set<String>> namesInUse = typeNamesInUse(cu);
                Map<JavaType.FullyQualified, String> names = new HashMap<>();
                Set<String> claimed = new HashSet<>();
                // A nested type is written as `Outer.Inner`, importing `Outer`, so that is the type whose name must be free.
                // First the types that need no import, so that one of them keeps its simple name in a clash.
                List<JavaType.FullyQualified> importsLast = new ArrayList<>(alternatives);
                importsLast.sort(Comparator.comparing(type -> !needsNoImport(cu, topLevel(type))));
                for (JavaType.FullyQualified type : importsLast) {
                    JavaType.FullyQualified topLevel = topLevel(type);
                    if (isSimpleNameFree(topLevel, namesInUse, claimed)) {
                        maybeAddImport(topLevel.getFullyQualifiedName());
                        names.put(type, type.getClassName());
                        claimed.add(simpleName(topLevel));
                    } else {
                        names.put(type, sourceName(type));
                    }
                }

                J.VariableDeclarations parameter = aCatch.getParameter().getTree();
                TypeTree typeExpression;
                JavaType parameterType;
                if (alternatives.size() == 1) {
                    typeExpression = typeTree(names.get(alternatives.get(0)), alternatives.get(0));
                    parameterType = alternatives.get(0);
                } else {
                    List<JRightPadded<NameTree>> padded = new ArrayList<>();
                    for (int i = 0; i < alternatives.size(); i++) {
                        JavaType.FullyQualified type = alternatives.get(i);
                        NameTree name = typeTree(names.get(type), type).withPrefix(i == 0 ? Space.EMPTY : Space.SINGLE_SPACE);
                        padded.add(JRightPadded.build(name).withAfter(i == alternatives.size() - 1 ? Space.EMPTY : Space.SINGLE_SPACE));
                    }
                    typeExpression = new J.MultiCatch(Tree.randomId(), Space.EMPTY, Markers.EMPTY, padded);
                    parameterType = new JavaType.MultiCatch(new ArrayList<>(alternatives));
                }
                TypeTree original = parameter.getTypeExpression();
                typeExpression = typeExpression.withPrefix(original == null ? Space.EMPTY : original.getPrefix());
                J.VariableDeclarations newParameter = parameter
                        .withTypeExpression(typeExpression)
                        .withVariables(ListUtils.map(parameter.getVariables(), v -> v
                                .withType(parameterType)
                                .withName(v.getName().withType(parameterType))));
                String name = parameterName(aCatch);
                J.VariableDeclarations.NamedVariable newVariable = newParameter.getVariables().get(0);
                J.Block body = (J.Block) new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                        if (identifier.getSimpleName().equals(name) && TypeUtils.isOfClassType(identifier.getType(), JAVA_LANG_EXCEPTION)) {
                            return identifier.withType(parameterType).withFieldType(newVariable.getVariableType());
                        }
                        return identifier;
                    }
                }.visitNonNull(aCatch.getBody(), 0);
                return aCatch.withParameter(aCatch.getParameter().withTree(newParameter)).withBody(body);
            }

            private <T extends TypeTree & Expression> TypeTree typeTree(String name, JavaType type) {
                T tree = TypeTree.build(name);
                return tree.withType(type);
            }
        };
        // Only Java has checked exceptions
        return Preconditions.check(Preconditions.and(new JavaFileChecker<>(), new UsesType<>(JAVA_LANG_EXCEPTION, false)), visitor);
    }

    private static String parameterName(J.Try.Catch aCatch) {
        return aCatch.getParameter().getTree().getVariables().get(0).getSimpleName();
    }

    /**
     * @return the types the catches catch, or null when one of them is unknown.
     */
    private static @Nullable List<JavaType> caughtTypes(List<J.Try.Catch> catches) {
        List<JavaType> caught = new ArrayList<>();
        for (J.Try.Catch c : catches) {
            JavaType type = c.getParameter().getType();
            if (type instanceof JavaType.MultiCatch) {
                caught.addAll(((JavaType.MultiCatch) type).getThrowableTypes());
            } else if (type instanceof JavaType.FullyQualified && !(type instanceof JavaType.Unknown)) {
                caught.add(type);
            } else {
                return null;
            }
        }
        return caught;
    }

    private static JavaType.FullyQualified runtimeExceptionType(List<JavaType> thrown) {
        for (JavaType type : thrown) {
            for (JavaType.FullyQualified t = TypeUtils.asFullyQualified(type); t != null; t = t.getSupertype()) {
                if (JAVA_LANG_RUNTIME_EXCEPTION.equals(t.getFullyQualifiedName())) {
                    return t;
                }
            }
        }
        return JavaType.ShallowClass.build(JAVA_LANG_RUNTIME_EXCEPTION);
    }

    /**
     * @return the methods of this name in the types and their supertypes, or null when a type is unknown.
     */
    private static @Nullable List<JavaType.Method> methodsIn(List<JavaType.@Nullable FullyQualified> types, String name) {
        Set<String> visited = new HashSet<>();
        List<JavaType.Method> methods = new ArrayList<>();
        for (JavaType.FullyQualified type : types) {
            if (type == null) {
                return null;
            }
            methods.addAll(methodsNamed(type, name, visited));
        }
        return methods;
    }

    private static List<JavaType.Method> methodsNamed(JavaType.@Nullable FullyQualified type, String name, Set<String> visited) {
        List<JavaType.Method> methods = new ArrayList<>();
        if (type == null || !visited.add(type.getFullyQualifiedName())) {
            return methods;
        }
        for (JavaType.Method method : type.getMethods()) {
            if (name.equals(method.getName())) {
                methods.add(method);
            }
        }
        if (!"<constructor>".equals(name)) {
            methods.addAll(methodsNamed(type.getSupertype(), name, visited));
            for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
                methods.addAll(methodsNamed(anInterface, name, visited));
            }
        }
        return methods;
    }

    private static boolean containsTypeVariable(@Nullable JavaType type, Set<JavaType> visited) {
        if (type == null || !visited.add(type)) {
            return false;
        }
        if (type instanceof JavaType.GenericTypeVariable) {
            return true;
        }
        if (type instanceof JavaType.Array) {
            return containsTypeVariable(((JavaType.Array) type).getElemType(), visited);
        }
        if (type instanceof JavaType.Parameterized) {
            for (JavaType parameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                if (containsTypeVariable(parameter, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAssignableToAny(JavaType type, List<JavaType> targets) {
        for (JavaType target : targets) {
            if (TypeUtils.isAssignableTo(target, type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops duplicates and the types that are subtypes of another one, as a multi-catch can't hold related types.
     */
    private static List<JavaType.FullyQualified> mostGeneral(List<JavaType.FullyQualified> types) {
        Map<String, JavaType.FullyQualified> byName = new LinkedHashMap<>();
        for (JavaType.FullyQualified type : types) {
            byName.putIfAbsent(type.getFullyQualifiedName(), type);
        }
        List<JavaType.FullyQualified> result = new ArrayList<>();
        for (JavaType.FullyQualified type : byName.values()) {
            boolean subsumed = false;
            for (JavaType.FullyQualified other : byName.values()) {
                if (other != type && TypeUtils.isAssignableTo(other, type)) {
                    subsumed = true;
                    break;
                }
            }
            if (!subsumed) {
                result.add(type);
            }
        }
        return result;
    }

    private static String simpleName(JavaType.FullyQualified type) {
        String fqn = type.getFullyQualifiedName();
        return fqn.substring(Math.max(fqn.lastIndexOf('.'), fqn.lastIndexOf('$')) + 1);
    }

    private static String sourceName(JavaType.FullyQualified type) {
        return type.getFullyQualifiedName().replace('$', '.');
    }

    private static JavaType.FullyQualified topLevel(JavaType.FullyQualified type) {
        JavaType.FullyQualified topLevel = type;
        while (topLevel.getOwningClass() != null) {
            topLevel = topLevel.getOwningClass();
        }
        return topLevel;
    }

    private static boolean needsNoImport(J.CompilationUnit cu, JavaType.FullyQualified type) {
        String packageName = type.getPackageName();
        if ("java.lang".equals(packageName)) {
            return true;
        }
        J.Package pkg = cu.getPackageDeclaration();
        return packageName.equals(pkg == null ? "" : pkg.getPackageName());
    }

    private static boolean isSimpleNameFree(JavaType.FullyQualified type, Map<String, Set<String>> namesInUse, Set<String> claimed) {
        String simpleName = simpleName(type);
        if (claimed.contains(simpleName)) {
            return false;
        }
        Set<String> fqns = namesInUse.get(simpleName);
        return fqns == null || (fqns.size() == 1 && fqns.contains(sourceName(type)));
    }

    /**
     * The fully qualified names of the types a simple name may already stand for in this compilation unit:
     * its single-type imports, the types it declares and the types it uses.
     */
    private static Map<String, Set<String>> typeNamesInUse(J.CompilationUnit cu) {
        Map<String, Set<String>> names = new HashMap<>();
        for (J.Import anImport : cu.getImports()) {
            if (!anImport.isStatic() && !"*".equals(anImport.getQualid().getSimpleName())) {
                names.computeIfAbsent(anImport.getQualid().getSimpleName(), k -> new HashSet<>())
                        .add(anImport.getTypeName().replace('$', '.'));
            }
        }
        Set<JavaType> types = new HashSet<>(cu.getTypesInUse().getTypesInUse());
        new JavaIsoVisitor<Set<JavaType>>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Set<JavaType> declared) {
                if (classDecl.getType() != null) {
                    declared.add(classDecl.getType());
                }
                return super.visitClassDeclaration(classDecl, declared);
            }
        }.visit(cu, types);
        for (JavaType type : types) {
            if (type instanceof JavaType.GenericTypeVariable) {
                names.computeIfAbsent(((JavaType.GenericTypeVariable) type).getName(), k -> new HashSet<>()).add("<type variable>");
                continue;
            }
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
            if (fq != null && !(fq instanceof JavaType.Unknown)) {
                names.computeIfAbsent(simpleName(fq), k -> new HashSet<>())
                        .add(sourceName(fq));
            }
        }
        return names;
    }

    /**
     * Computes the exceptions that statements can throw to an enclosing `try`, from the exceptions that the methods
     * and constructors they call declare, the `throw` statements and the resources they close. Code in lambdas and
     * local or anonymous classes is not run there, and what a nested `try` catches doesn't reach the enclosing one.
     */
    private static class ThrownExceptions extends JavaIsoVisitor<List<JavaType>> {
        private static final MethodMatcher CLASS_NEW_INSTANCE = new MethodMatcher("java.lang.Class newInstance()");

        private boolean indeterminate;

        /**
         * Whether the method may throw checked exceptions it doesn't declare: a Lombok `@SneakyThrows` method
         * compiled from source, or a method of a Kotlin, Scala or Groovy class, which have no checked exceptions.
         */
        private static boolean isSneaky(JavaType.Method method) {
            for (JavaType.FullyQualified annotation : method.getAnnotations()) {
                if ("lombok.SneakyThrows".equals(annotation.getFullyQualifiedName())) {
                    return true;
                }
            }
            for (JavaType.FullyQualified annotation : method.getDeclaringType().getAnnotations()) {
                String name = annotation.getFullyQualifiedName();
                if ("kotlin.Metadata".equals(name) || "scala.reflect.ScalaSignature".equals(name)) {
                    return true;
                }
            }
            return TypeUtils.isAssignableTo("groovy.lang.GroovyObject", method.getDeclaringType());
        }

        private static boolean completesAbruptly(J.Block block) {
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J preVisit(J tree, AtomicBoolean found) {
                    if (tree instanceof J.Return || tree instanceof J.Throw || tree instanceof J.Break || tree instanceof J.Continue) {
                        found.set(true);
                    }
                    if (tree instanceof J.Lambda || tree instanceof J.ClassDeclaration || tree instanceof J.NewClass && ((J.NewClass) tree).getBody() != null) {
                        stopAfterPreVisit();
                    }
                    return tree;
                }
            }.reduce(block, new AtomicBoolean()).get();
        }

        /**
         * @return the exceptions the block and the resources of the `try` can throw, or null when they can't be determined.
         */
        @Nullable
        List<JavaType> ofTryBlock(J.Try aTry) {
            List<JavaType> thrown = new ArrayList<>();
            addTryBlock(aTry, thrown);
            return indeterminate ? null : thrown;
        }

        private void addTryBlock(J.Try aTry, List<JavaType> thrown) {
            if (aTry.getResources() != null) {
                for (J.Try.Resource resource : aTry.getResources()) {
                    visit(resource, thrown);
                    JavaType.FullyQualified type = TypeUtils.asFullyQualified(resource.getVariableDeclarations().getType());
                    addThrownBy(TypeUtils.findDeclaredMethod(type, "close", emptyList()).orElse(null), thrown);
                }
            }
            visit(aTry.getBody(), thrown);
        }

        private void addThrownBy(JavaType.@Nullable Method method, List<JavaType> thrown) {
            if (method == null) {
                indeterminate = true;
            } else {
                thrown.addAll(method.getThrownExceptions());
            }
        }

        private void addCalled(JavaType.@Nullable Method method, List<JavaType> thrown) {
            if (method != null && isSneaky(method)) {
                indeterminate = true;
            }
            addThrownBy(method, thrown);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<JavaType> thrown) {
            if (CLASS_NEW_INSTANCE.matches(method.getMethodType())) {
                // Throws the checked exceptions of the constructor it calls without declaring them
                indeterminate = true;
            }
            addCalled(method.getMethodType(), thrown);
            return super.visitMethodInvocation(method, thrown);
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, List<JavaType> thrown) {
            addCalled(newClass.getConstructorType(), thrown);
            visit(newClass.getEnclosing(), thrown);
            for (Expression argument : newClass.getArguments()) {
                visit(argument, thrown);
            }
            if (newClass.getBody() != null) {
                // An anonymous class's methods run when they are called, but its initializers run here
                for (Statement statement : newClass.getBody().getStatements()) {
                    if (statement instanceof J.Block && !((J.Block) statement).isStatic() ||
                        statement instanceof J.VariableDeclarations && !((J.VariableDeclarations) statement).hasModifier(J.Modifier.Type.Static)) {
                        visit(statement, thrown);
                    }
                }
            }
            return newClass;
        }

        @Override
        public J.Throw visitThrow(J.Throw thrown, List<JavaType> exceptions) {
            JavaType type = thrown.getException().getType();
            if (type instanceof JavaType.MultiCatch) {
                exceptions.addAll(((JavaType.MultiCatch) type).getThrowableTypes());
            } else if (type == null) {
                indeterminate = true;
            } else {
                exceptions.add(type);
            }
            return super.visitThrow(thrown, exceptions);
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, List<JavaType> thrown) {
            for (J.Annotation annotation : multiVariable.getLeadingAnnotations()) {
                if (TypeUtils.isOfClassType(annotation.getType(), "lombok.Cleanup")) {
                    // Lombok closes the variable in code that isn't in the tree
                    indeterminate = true;
                }
            }
            return super.visitVariableDeclarations(multiVariable, thrown);
        }

        @Override
        public J.Lambda visitLambda(J.Lambda lambda, List<JavaType> thrown) {
            return lambda;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, List<JavaType> thrown) {
            return classDecl;
        }

        @Override
        public J.Try visitTry(J.Try tryable, List<JavaType> thrown) {
            List<JavaType> fromBlock = new ArrayList<>();
            addTryBlock(tryable, fromBlock);
            List<JavaType> caught = caughtTypes(tryable.getCatches());
            if (caught == null) {
                indeterminate = true;
                return tryable;
            }
            for (JavaType exception : fromBlock) {
                if (!isAssignableToAny(exception, caught)) {
                    thrown.add(exception);
                }
            }
            for (J.Try.Catch c : tryable.getCatches()) {
                visit(c.getBody(), thrown);
            }
            if (tryable.getFinally() != null && completesAbruptly(tryable.getFinally())) {
                // A `finally` that returns or jumps discards the exceptions of the block and the catches
                indeterminate = true;
            }
            visit(tryable.getFinally(), thrown);
            return tryable;
        }
    }
}
