# Recipe Development Instructions

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

## Testing Commands
After making changes to a recipe, always run the respective tests, such as for instance:
```bash
./gradlew test --tests RemoveRedundantNullCheckBeforeInstanceofTest
```

Ensure all tests pass before considering the changes complete.
