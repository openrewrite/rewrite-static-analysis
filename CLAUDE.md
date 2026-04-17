# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the OpenRewrite Static Analysis repository - a collection of recipes that automatically fix SAST-like issues in Java, Kotlin, Groovy, and C# codebases. It's built on the OpenRewrite framework for automated code transformation.

## Build and Test Commands

### Building the Project
```bash
./gradlew build
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests RemoveRedundantNullCheckBeforeInstanceofTest

# Run tests matching a pattern
./gradlew test --tests "*NullCheck*"

# Run a specific test method
./gradlew test --tests "RemoveRedundantNullCheckBeforeInstanceofTest.testMethodName"
```

### Other Commands
```bash
# Clean build
./gradlew clean

# Check code (compile + test)
./gradlew check

# Assemble without running tests
./gradlew assemble
```

## High-Level Architecture

### Recipe Development Pattern

1. **Recipe Structure**: Each recipe extends `org.openrewrite.Recipe` and implements:
   - `getDisplayName()` - Human-readable name
   - `getDescription()` - Detailed description
   - `getTags()` - Tags for categorization (often RSPEC rule IDs)
   - `getVisitor()` - Returns a TreeVisitor that performs the transformation

2. **Visitor Pattern**: Recipes use `JavaVisitor<ExecutionContext>` to traverse and transform the AST:
   - Override specific visit methods (`visitBinary()`, `visitMethodInvocation()`, etc.)
   - Work with immutable AST nodes (J.Binary, J.Unary, J.InstanceOf, etc.)
   - Use `.with*()` methods for transformations while preserving formatting

3. **Recipe Collections**: YAML files in `src/main/resources/META-INF/rewrite/` group recipes:
   - `common-static-analysis.yml` - Common static analysis fixes
   - `java-best-practices.yml` - Java-specific best practices
   - `static-analysis.yml` - General static analysis recipes

### Test Pattern

Tests implement `RewriteTest` and follow this structure:
```java
@Test
void testName() {
    rewriteRun(
        java(
            """
            // before code
            """,
            """
            // after code
            """
        )
    );
}
```

## Code Style Preferences

### 1. Avoid Small Utility Methods
- Prefer more concise code over many small utility methods
- Inline logic when it doesn't significantly reduce readability
- Consolidate related logic into fewer, more comprehensive methods

### 2. Use SemanticallyEqual for Expression Comparison
- When comparing expressions in recipes, use `SemanticallyEqual.areEqual()` from `org.openrewrite.java.search.SemanticallyEqual`
- This provides more robust comparison than string matching
- Handles formatting and whitespace differences automatically

## Test Development Guidelines

### 1. Test Structure
- Implement `RewriteTest` interface
- Override `defaults(RecipeSpec spec)` to set the recipe being tested
- Use `@DocumentExample` on the primary test case that best demonstrates the recipe
- Add `@Issue("https://github.com/...")` annotations for tests related to specific issues

### 2. Test Naming
- Use descriptive test method names that explain the scenario being tested
- Prefix negative tests with `doNotChange` or `unchanged`
- Group related test scenarios (e.g., `removeRedundantNullCheck`, `removeRedundantNullCheckWithMethodInvocation`)

### 3. Test Case Design
- Avoid test cases that essentially test the same functionality
- Each test should cover a distinct scenario or edge case
- Consolidate similar tests when they don't add unique value
- In test text blocks, prefer method parameters over local variable declarations
- This makes tests more concise and focuses on the transformation being tested
- Example:
  ```java
  // Instead of:
  void foo() {
      String s = "test";
      if (s != null && s instanceof String) {
          // ...
      }
  }

  // Use:
  void foo(String s) {
      if (s != null && s instanceof String) {
          // ...
      }
  }
  ```

### 4. Suppress Warnings Appropriately
- Add `@SuppressWarnings` at the class level for expected warnings in test code:
  - `"ConstantConditions"` - for redundant null checks that the recipe will remove
  - `"ConditionCoveredByFurtherCondition"` - for redundant conditions
  - `"RedundantCast"` - for unnecessary casts
  - `"unused"` - for unused variables/methods

### 5. Language Comments for IDE Support
- Add `//language=java` before `java()` calls when there's no customization
- Place comments on individual strings when there's customization or multiple `java()` calls
- Don't add language comments to JavaTemplate strings with parameters

### 6. Test Coverage Guidelines
- Test basic functionality with simple cases
- Include edge cases (method invocations, field access, complex expressions)
- Test cases where the recipe should NOT make changes
- Test partial transformations in complex conditions
- Test different operator positions and combinations
- Include tests for language-specific behavior (e.g., Kotlin's `!is` operator)

## Recipe Implementation Conventions

### 1. Recipe Structure
- Use `@EqualsAndHashCode(callSuper = false)` and `@Value` from Lombok for immutable recipe classes
- Implement `getEstimatedEffortPerOccurrence()` - use the same time estimate from SonarQube when implementing RSPEC rules
- Always include RSPEC tags in `getTags()` when implementing SonarQube rules (e.g., `"RSPEC-S1697"`)
- Use `Preconditions.check()` to exclude specific file types (e.g., Kotlin files) when a recipe is language-specific

### 2. Visitor Choice
- Use `JavaIsoVisitor<ExecutionContext>` when returning the same type of LST element being visited
- Use `JavaVisitor<ExecutionContext>` when the transformation changes the tree structure or returns a different type
- For multi-language support, implement separate visitors for each language (JavaVisitor, KotlinVisitor, etc.)

### 3. Expression Comparison
- Always use `SemanticallyEqual.areEqual()` for comparing expressions instead of string matching
- Use `J.Literal.isLiteralValue()` for checking literal values
- Use `TypeUtils` helper methods (e.g., `TypeUtils.asArray()`, `TypeUtils.asFullyQualified()`) instead of instanceof checks with JavaType

### 4. Method Matching
- Use `MethodMatcher` for reliable method matching instead of manual name/type checks
- Example: `private static final MethodMatcher TO_ARRAY = new MethodMatcher("java.util.Collection toArray()", true);`

### 5. Refaster Templates
- Simple transformations can use Refaster-style templates with `@BeforeTemplate` and `@AfterTemplate` annotations
- Use `@RecipeDescriptor` annotation for metadata on Refaster template classes

### 6. JavaTemplate Best Practices
- Always declare imports when templates introduce types: `.imports(fqn)`
- Call `maybeAddImport()` after applying templates that use new types
- Use `service(ImportService.class).shortenFullyQualifiedTypeReferencesIn()` to clean up imports
- Don't add `//language=java` comments to JavaTemplate strings containing parameters like `#{any()}`

## YAML Configuration

### Recipe Collections
Recipes are organized into YAML files in `src/main/resources/META-INF/rewrite/`:
- `common-static-analysis.yml` - Common static analysis fixes that apply across languages
- `java-best-practices.yml` - Java-specific best practices and idioms
- `static-analysis.yml` - General static analysis recipes

### Adding Recipes to Collections
- Do NOT add new recipes to `common-static-analysis.yml` by default — it contains tested and proven recipes only
- Add new recipes to appropriate YAML files based on their scope
- Comment out recipes that are experimental or have known issues (with a comment explaining why)
- Group related recipes together in the YAML lists

## Important Notes

- The project uses Gradle with build caching and parallel execution enabled
- Build scans are available at https://ge.openrewrite.org/
- Documentation for recipes is available at https://docs.openrewrite.org/recipes/staticanalysis
- Contributions should follow the [OpenRewrite contributing guide](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md)
- **Recipe development documentation** is available in the `./docs` submodule - consult this for detailed guidance on writing recipes, using JavaTemplate, and testing patterns
- **Recipe writing lessons** are captured in `recipe-writing-lessons.md` - check this file for specific learnings and best practices discovered during recipe development, and continue adding new insights as they are discovered