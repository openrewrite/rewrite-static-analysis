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

### 1. Remove Overlapping Test Cases
- Avoid test cases that essentially test the same functionality
- Each test should cover a distinct scenario or edge case
- Consolidate similar tests when they don't add unique value

### 2. Use Method Parameters Instead of Local Variables
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

### 3. Suppress Warnings Appropriately
- Add `@SuppressWarnings` annotations to test classes to suppress expected warnings
- Common suppressions for this type of recipe:
  - `"ConstantConditions"` - for redundant null checks that the recipe will remove
  - Other suppressions as needed based on the specific warnings in test cases

## Important Notes

- The project uses Gradle with build caching and parallel execution enabled
- Build scans are available at https://ge.openrewrite.org/
- Documentation for recipes is available at https://docs.openrewrite.org/recipes/staticanalysis
- Contributions should follow the [OpenRewrite contributing guide](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md)
- **Recipe development documentation** is available in the `./docs` submodule - consult this for detailed guidance on writing recipes, using JavaTemplate, and testing patterns
- **Recipe writing lessons** are captured in `recipe-writing-lessons.md` - check this file for specific learnings and best practices discovered during recipe development, and continue adding new insights as they are discovered