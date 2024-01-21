package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddSerialAnnotationToserialVersionUIDTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddSerialAnnotationToserialVersionUID());
    }

    @Test
    void serialAnnotationAlreadyPresent() {
        rewriteRun(
          //language=java
          java(
            """
                             import java.io.Serializable;
                             import java.io.Serial;
                                         
                             public class Example implements Serializable {
                                 String var1 = "first variable";
                                 @Serial
                                 private static final long serialVersionUID = 1L;
                                 int var3 = 666;
                                 }
                    """
          )
        );
    }

    @Disabled
    @Test
    void addSerialAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
                    import java.io.Serializable;
                    import java.io.Serial;
                                
                    public class Example implements Serializable {
                        String var1 = "first variable";
                        private static final long serialVersionUID = 1L;
                        int var3 = 666;
                    }
                    """,
            """
                    import java.io.Serializable;
                    import java.io.Serial;
                                
                    public class Example implements Serializable {  
                        String var1 = "first variable";                        
                        @Serial 
                        private static final long serialVersionUID = 1L;
                        int var3 = 666;
                        String wolvie = "wolverine"; 
                    }
                    """
          )
        );
    }


    // Borowed from AddSerialVersionUidToSerializableTest of recipe AddSerialVersionUidToSerializableTest
    @Disabled
    @Test
    void methodDeclarationsAreNotVisited() {
        rewriteRun(
          //language=java
          java(
            """
                    import java.io.Serializable;
                                
                    public class Example implements Serializable {
                        private String fred;
                        private int numberOfFreds;
                        void doSomething() {
                            long serialVersionUID = 1L;
                        }
                    }
                    """
          )
        );
    }

    // Borowed from AddSerialVersionUidToSerializableTest of recipe AddSerialVersionUidToSerializableTest
    @Disabled
    @Test
    void serializableInnerClass() {
        rewriteRun(
          //language=java
          java(
            """
                    import java.io.Serializable;
                    public class Outer implements Serializable {
                        public static class Inner implements Serializable {
                        }
                    }
                    """,
            """
                    import java.io.Serializable;
                    public class Outer implements Serializable {
                        private static final long serialVersionUID = 1;
                        public static class Inner implements Serializable {
                            private static final long serialVersionUID = 1;
                        }
                    }
                    """
          )
        );
    }

}