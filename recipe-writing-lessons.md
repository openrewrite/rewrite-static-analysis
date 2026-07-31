# Recipe Writing Lessons

This document captures key lessons learned while developing OpenRewrite recipes.

## TypeUtils Best Practices

### Use TypeUtils methods instead of instanceof checks

When working with JavaType objects, prefer using TypeUtils helper methods over direct instanceof checks:

**Good:**
```java
JavaType.Array arrayType = TypeUtils.asArray(targetType);
if (arrayType != null) {
    JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(componentType);
    if (fqType != null) {
        // Use fqType
    }
}
```

**Avoid:**
```java
if (targetType instanceof JavaType.Array) {
    JavaType.Array arrayType = (JavaType.Array) targetType;
    if (componentType instanceof JavaType.FullyQualified) {
        JavaType.FullyQualified fqType = (JavaType.FullyQualified) componentType;
        // Use fqType
    }
}
```

### Benefits of TypeUtils
- Safer type checking and casting
- More idiomatic OpenRewrite code
- Handles null cases gracefully
- Consistent with the framework's patterns

## JavaType Hierarchy

### JavaType.Class extends JavaType.FullyQualified
Since `JavaType.Class` is a subtype of `JavaType.FullyQualified`, you only need to check for the parent type:

**Simplified:**
```java
JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(componentType);
if (fqType != null) {
    // This handles both JavaType.Class and JavaType.FullyQualified
}
```

## MethodMatcher for Reliable Method Matching

When checking for specific method calls, use `MethodMatcher` instead of manual checks:

**Good:**
```java
private static final MethodMatcher TO_ARRAY = new MethodMatcher("java.util.Collection toArray()", true);

if (TO_ARRAY.matches(methodInvocation)) {
    // Handle toArray() call
}
```

**Avoid:**
```java
if (methodInvocation.getName().getSimpleName().equals("toArray") &&
    methodInvocation.getArguments().size() == 0 &&
    methodInvocation.getMethodType() != null &&
    TypeUtils.isAssignableTo("java.util.Collection", methodInvocation.getMethodType().getDeclaringType())) {
    // Handle toArray() call
}
```

## JavaTemplate Usage

### Import handling
Always declare imports when the template introduces types:
```java
JavaTemplate template = JavaTemplate.builder("#{any()}.toArray(new #{}[0])")
        .imports(fqn)  // Important: declare the import
        .build();
```

### Don't forget maybeAddImport()
After applying a template that uses a type, add the import to the compilation unit:
```java
Expression result = template.apply(...);
maybeAddImport(fqn);  // Add the import to the source file
```

## Testing Best Practices

### Type Validation in Tests
For tests involving custom types that may have incomplete type information, consider disabling type validation.
But only use this as an absolute last resort. Prefer to specify types that exist.

```java
@Test
void withCustomType() {
    rewriteRun(
      spec -> spec.typeValidationOptions(TypeValidation.none()),
      java(
        // test code
      )
    );
}
```

### Suppressing IDE Warnings in Tests
When testing code that contains intentional issues (that your recipe will fix), suppress IDE warnings:

```java
// For a single test method
@SuppressWarnings("RedundantCast")
@Test
void testRedundantCast() {
    rewriteRun(
      java(
        """
          String[] array = (String[]) list.toArray(); // IntelliJ warns about redundant cast
          """
      )
    );
}

// For multiple tests with the same warning, move to class level
@SuppressWarnings({"ConstantConditions", "RedundantCast"})
class MyRecipeTest implements RewriteTest {
    // All test methods inherit these suppressions
}
```

Common suppressions for recipe tests:
- `"RedundantCast"` - for recipes fixing unnecessary casts
- `"ConstantConditions"` - for null checks that will be removed
- `"unused"` - for unused variables/methods that will be removed
- `"unchecked"` - for unchecked operations that will be fixed

### Test Coverage
Ensure comprehensive test coverage including:
- Basic cases
- Edge cases (custom types, fully qualified types)
- Cases where the recipe should NOT make changes
- Import handling scenarios
- Formatting preservation

### IDE Support with Language Comments
Add language comments before string templates containing code to enable IDE syntax highlighting.

When using `java("before", "after")` with no customization, place the comment before `java`:
```java
@Test
void testMethod() {
    rewriteRun(
      //language=java
      java(
        """
          public class Before {
              // Both strings get syntax highlighting
          }
          """,
        """
          public class After {
              // Both strings get syntax highlighting
          }
          """
      )
    );
}
```

When there's customization or multiple `java()` calls, place comments on individual strings:
```java
@Test
void testWithCustomization() {
    rewriteRun(
      spec -> spec.typeValidationOptions(TypeValidation.none()),
      //language=java
      java(
        """
          public class Test {
              // Java code with syntax highlighting
          }
          """
      )
    );
}
```

**Important**: Do NOT add `//language=java` to JavaTemplate strings that contain parameters like `#{any()}` or `#{}`, as these are not valid Java syntax and will cause IDE errors:
```java
// DON'T DO THIS - parameters make it invalid Java
JavaTemplate template = JavaTemplate.builder(
    //language=java  // ❌ Wrong!
    "#{any()}.toArray(new #{}[0])"
).build();

// Only use language comments for valid Java code
JavaTemplate template = JavaTemplate.builder(
    //language=java  // ✅ OK - valid Java
    "System.out.println(\"Hello\")"
).build();
```

## Recipe Metadata

### Always include RSPEC tags
When implementing SonarQube rules, include the RSPEC identifier:
```java
@Override
public Set<String> getTags() {
    return Collections.singleton("RSPEC-S3020");
}
```

### Accurate time estimates
Provide realistic time estimates for manual fixes. When implementing SonarQube rules, use the same time estimate that is on the SonarQube definition.
```java
@Override
public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(2);
}
```

## Visitor Patterns

### JavaVisitor vs JavaIsoVisitor

Use `JavaIsoVisitor` when:
- You're returning the same type of LST element that you're visiting
- You want to avoid casting the visited element
- Most common for simple transformations

Use `JavaVisitor` when:
- You need to return a different type of LST element than what you're visiting
- Example: Visiting a `J.Parentheses` but returning an `Expression` after unwrapping/replacing it
- This is necessary when the transformation fundamentally changes the tree structure

```java
// JavaVisitor example - returning different type
@Override
public J visitParentheses(J.Parentheses parentheses, ExecutionContext ctx) {
    // ... some logic ...
    return someExpression;  // Not a J.Parentheses
}

// JavaIsoVisitor example - returning same type
@Override
public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
    J.TypeCast tc = super.visitTypeCast(typeCast, ctx);
    // ... some logic ...
    return tc;  // Still a J.TypeCast
}
```

### Handle parentheses explicitly
When dealing with expressions that might be parenthesized, consider visiting parentheses nodes:
```java
@Override
public J visitParentheses(J.Parentheses parentheses, ExecutionContext ctx) {
    // Handle parenthesized expressions
}
```

### Preserve formatting
When replacing expressions, be mindful of preserving prefixes and formatting:
```java
return visitedParentheses.withTree(result);  // Preserves parentheses structure
```

## YAML Configuration

### Add recipes to appropriate recipe collections
Don't forget to add new recipes to relevant YAML files:
```yaml
recipeList:
  - org.openrewrite.staticanalysis.CollectionToArrayShouldHaveProperType
```

Common collections:
- `common-static-analysis.yml` - General static analysis fixes
- `java-best-practices.yml` - Java-specific best practices
- `static-analysis.yml` - Broader static analysis recipes

## Data-flow-guarded local type replacement (ModernizeCollections)

When replacing a legacy synchronized type with an unsynchronized one (`Hashtable`→`HashMap`,
`Vector`→`ArrayList`, `Stack`→`Deque`, `StringBuffer`→`StringBuilder`), the transformation is only
sound when the instance is a local variable that never escapes its method. Model this after
`ReplaceStackWithDeque`: a `DataFlowSpec` whose source is the variable initializer and whose sinks
mark escape. Key learnings:

- **Escape = the reference itself leaves, not a derived value.** Check the sink node's *direct*
  parent, not `firstEnclosing(J.Return.class)`. `return v.size()` puts `v` inside a `J.Return`, but
  only `size()` is returned — `v` is the method-call receiver and does not escape. The escape sinks
  that matter: `parent instanceof J.Return`, `v` in a `J.MethodInvocation`/`J.NewClass` argument list,
  and `v` as the RHS of a `J.Assignment` whose target is a field.
- **The rewrite-analysis flow models already handle closure capture precisely.** A buffer captured by
  `items.forEach(x -> sb.append(x))` stays confined (converted), while `Runnable r = () -> sb.append(x);
  new Thread(r).start()` is correctly detected as escaping (not converted). No hand-rolled lambda guard
  is needed.
- **Restrict to local variables.** Use `variable.getVariableType().getOwner() instanceof JavaType.Method`
  to exclude fields (which are shared state). Also skip multi-variable declarations
  (`Vector a = ..., b = ...`) — the shared type expression cannot be changed for just one variable.
- **Guard type-specific methods.** `Hashtable` (`contains`/`elements`/`keys`) and `Vector`
  (`addElement`/`elementAt`/…) expose methods absent from `HashMap`/`ArrayList`; skip when used.
  `Vector(int, int)` has no `ArrayList` constructor. `StringBuilder`/`StringBuffer` APIs are identical.
- **Fragment `ChangeType` leaves the old import; re-type the uses too.** Running `ChangeType` on just
  the declaration (type expression + initializer) does not re-type the variable's *uses* (`v.add(...)`),
  so those keep the old `JavaType`/method type, `typesInUse` still counts the legacy type, and
  `maybeRemoveImport` cannot drop the import (`ReplaceStackWithDeque` has this wart). Blanket
  `ChangeType` over the whole method is unsafe when the method references non-converted instances of the
  same type (a field, an escaping local). The clean fix is precise per-variable re-typing: record the
  declaration's `JavaType.Variable` in a `CompilationUnit`-scoped `IdentityHashMap`, then in
  `visitIdentifier` re-type any identifier whose `getFieldType()` is that variable, and in
  `visitMethodInvocation` re-map the `JavaType.Method` (declaring type **and return type**, so chained
  calls like `sb.append(a).append(b)` resolve) whenever the receiver is already the replacement type but
  the method type still declares the legacy type. With every reference re-typed, `maybeRemoveImport`
  removes the import with no downstream cleanup needed. `ReplaceSynchronizedType` is the shared base that
  implements this for `Hashtable`/`Vector`/`StringBuffer`.

## Data flow / taint tracking (rewrite-analysis)

### `Dataflow.findSinks` cannot track sources inside `catch` blocks
`org.openrewrite.analysis.dataflow.Dataflow#findSinks` gates its results on control-flow
*reachability*: it computes the control-flow graph of the enclosing method and prunes any
flow whose nodes are not reachable on the normal control-flow path. A `catch` block is only
entered via an exceptional edge, which the control-flow graph does not model, so every
expression inside a `catch` is considered unreachable and the flow is pruned to empty —
`findSinks` returns `Option.none()` even though `DataFlowNode.of(...)` and `spec.isSource(...)`
both succeed.

To taint-track a source that lives inside a `catch` block (e.g. connecting a caught exception
to a newly thrown one), drive the flow engine directly and skip the reachability filter:
```java
DataFlowNode.of(cursor).forEach(node -> {
    FlowGraph graph = ForwardFlow.findAllFlows(node, spec, FlowGraph.Factory.defaultFactory());
    // BFS/DFS over graph.getEdges(); collect each node.getCursor().getValue() that is an Expression
});
```
This yields the full forward taint graph (every expression the source flows into) without the
control-flow reachability gate. See `FindNewExceptionWithoutCause`.

### Matching a reference to a specific local/caught variable
`JavaType.Variable.equals` is structural (compares `name` + `owner`), so a reference's
`J.Identifier#getFieldType()` reliably equals the declaration's `NamedVariable#getVariableType()`.
Fall back to simple-name comparison only when type attribution is missing.

### `@Nullable` on a nested type is a type-use annotation
Write `JavaType.@Nullable Variable`, not `@Nullable JavaType.Variable`. The latter annotates the
scoping construct `JavaType` and fails to compile ("scoping construct cannot be annotated with
type-use annotation"), which crashes the whole Lombok annotation-processing round and produces a
misleading cascade of "does not override abstract method getDescription()" errors across every
Lombok-annotated recipe.
