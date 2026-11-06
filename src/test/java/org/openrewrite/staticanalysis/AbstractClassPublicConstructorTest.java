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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings("java:S2699")
class AbstractClassPublicConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AbstractClassPublicConstructor());
    }

    @Test
    void preserveInheritedPublicNestedConstructor() {
        rewriteRun(java("""
          public class Api extends Internal {}
          class Internal {
              public abstract static class Nested {
                  public Nested() {}
              }
          }
          """));
    }

    @Test
    void preservePublicLeafNodeConstructor() {
        rewriteRun(java("""
          package org.jsoup.nodes;
          public abstract class LeafNode {
              Object value;
              public LeafNode() {
                  value = "";
              }
          }
          """));
    }

    @Test
    void preservePublicNestedEvaluatorConstructors() {
        rewriteRun(java("""
          package org.jsoup.select;
          public abstract class Evaluator {
              public abstract static class AttributeKeyPair extends Evaluator {
                  protected final String key;
                  protected final String value;
                  public AttributeKeyPair(String key, String value) {
                      this.key = key;
                      this.value = value;
                  }
              }
              public abstract static class IndexEvaluator extends Evaluator {
                  final int index;
                  public IndexEvaluator(int index) {
                      this.index = index;
                  }
              }
              public abstract static class CssNthEvaluator extends Evaluator {
                  protected final int a;
                  protected final int b;
                  public CssNthEvaluator(int step, int offset) {
                      this.a = step;
                      this.b = offset;
                  }
                  public CssNthEvaluator(int offset) {
                      this(0, offset);
                  }
              }
          }
          """));
    }

    @Test
    void preserveKotlinInferredReturnTypeMethodVisibility() {
        rewriteRun(
          kotlin(
            """
              abstract class Dispatcher : AutoCloseable {
                  public open override fun close() {}
              }
              """
          )
        );
    }

    @Test
    void preserveKotlinPublicSecondaryConstructorVisibility() {
        rewriteRun(
          kotlin(
            """
              abstract class Base {
                  public constructor()
              }
              """
          )
        );
    }

    @Test
    void preserveProtectedAndImplicitPublicNestedClasses() {
        rewriteRun(
          java("""
            public class Api {
                protected abstract static class ProtectedBase {
                    public ProtectedBase() {}
                }
                public interface Contract {
                    abstract class ImplicitlyPublic {
                        public ImplicitlyPublic() {}
                    }
                }
            }
            """));
    }

    @Test
    void restrictConstructorsInInternalNestedClasses() {
        rewriteRun(java("""
          class Internal {
              abstract static class Nested {
                  public Nested() {}
              }
          }
          """, """
          class Internal {
              abstract static class Nested {
                  protected Nested() {}
              }
          }
          """), java("""
          public class Api {
              private abstract static class Internal {
                  public Internal() {}
              }
          }
          """, """
          public class Api {
              private abstract static class Internal {
                  protected Internal() {}
              }
          }
          """));
    }

    @DocumentExample
    @Test
    void replacePublicByProtected() {
        rewriteRun(
          //language=java
          java(
            """
            abstract class Test {
                public Test() {
                }
            }
            """,
            """
            abstract class Test {
                protected Test() {
                }
            }
            """
          )
        );
    }

    @Test
    void noReplaceOnNonAbstractClass() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                public Test() {
                }
            }
            """
          )
        );
    }

    @Test
    void noReplaceOnPackageProtectedConstructor() {
        rewriteRun(
          //language=java
          java(
                """
            abstract class Test {
                Test() {
                }
            }
            """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/449")
    @Test
    void noReplaceOnNestedClasses() {
        rewriteRun(
          //language=java
          java(
            """
            abstract class Test {
                static class Nested {
                    public Nested() {
                    }
                }
            }
            """
          )
        );
    }
}
