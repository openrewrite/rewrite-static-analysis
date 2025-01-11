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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ALL")
class ExternalizableHasNoArgConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExternalizableHasNoArgsConstructor());
    }

    @Test
    void hasDefaultNoArgsConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Externalizable;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;
                            
              public class MyThing implements Externalizable {
                  private String a;
                  private String b;
                  
                  public void setA(String a) {
                      this.a = a;
                  }
                  public void setB(String b) {
                      this.b = b;
                  }
                  
                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """
          )
        );
    }

    @Test
    void hasNoArgsConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Externalizable;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;
                            
              public class MyThing implements Externalizable {
                  private String a;
                  private String b;
                  
                  public MyThing(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }
                  
                  public MyThing() {}
                  
                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """
          )
        );
    }

    @DocumentExample
    @SuppressWarnings("ExternalizableWithoutPublicNoArgConstructor")
    @Test
    void needsNoArgsConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Externalizable;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;

              public class MyThing implements Externalizable {
                  private String a;
                  private String b;
                  
                  public MyThing(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }

                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """,
            """
              import java.io.Externalizable;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;

              public class MyThing implements Externalizable {
                  private String a;
                  private String b;
                            
                  public MyThing() {
                  }
                  
                  public MyThing(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }

                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """
          )
        );
    }

    @Test
    void implementsExternalizableInterface() {
        rewriteRun(
          //language=java
          java(
            """
              package abc;
              import java.io.Externalizable;
                            
              interface Abc extends Externalizable {
                  String getLetter();
              }
              """
          ),
          //language=java
          java(
            """
              package abc;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;
                            
              public class MyThing implements Abc {
                  private String a;
                  private String b;
                  
                  public MyThing(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }
                  
                  @Override
                  public String getLetter() {
                      return a;
                  }
                  
                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """,
            """
              package abc;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;
                            
              public class MyThing implements Abc {
                  private String a;
                  private String b;
                            
                  public MyThing() {
                  }
                  
                  public MyThing(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }
                  
                  @Override
                  public String getLetter() {
                      return a;
                  }
                  
                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """
          )
        );
    }

    @Test
    void hasFinalFieldVar() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Externalizable;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;

              public class MyThing implements Externalizable {
                  private final String a;
                  private final String b;
                  
                  public MyThing(String a, String b) {
                      this.a = a;
                      this.b = b;
                  }

                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """
          )
        );
    }

    @Test
    void hasInitializedFinalFieldVar() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Externalizable;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;import java.util.ArrayList;

              public class MyThing implements Externalizable {
                  private final Integer limit = 10;
                  private String a;
                  
                  public MyThing(String a) {
                      this.a = a;
                  }

                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """,
            """
              import java.io.Externalizable;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;import java.util.ArrayList;

              public class MyThing implements Externalizable {
                  private final Integer limit = 10;
                  private String a;
                            
                  public MyThing() {
                  }
                  
                  public MyThing(String a) {
                      this.a = a;
                  }

                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """
          )
        );
    }

    @Test
    void superClassDoesNotHaveDefaultConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              package abc;
              public class SuperThing {
                  private final Long l;
                  public SuperThing(Long l) {
                      this.l = l;
                  }
                  public void doSomething() {}
              }
              """
          ),
          //language=java
          java(
            """
              package abc;
              import java.io.Externalizable;
              import java.io.IOException;
              import java.io.ObjectInput;
              import java.io.ObjectOutput;

              public class MyThing extends SuperThing implements Externalizable {
                  
                  public MyThing(Long l) {
                      super(l);
                  }

                  @Override 
                  public void writeExternal(ObjectOutput out) throws IOException {}
                  
                  @Override
                  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
              }
              """
          )
        );
    }
}
