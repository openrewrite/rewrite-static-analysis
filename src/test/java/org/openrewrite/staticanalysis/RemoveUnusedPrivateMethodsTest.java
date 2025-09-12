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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveUnusedPrivateMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedPrivateMethods())
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-params"));
    }

    @DocumentExample
    @Test
    void removeUnusedPrivateMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private void unused() {
                  }

                  public void dontRemove() {
                      dontRemove2();
                  }

                  private void dontRemove2() {
                  }
              }
              """,
            """
              class Test {

                  public void dontRemove() {
                      dontRemove2();
                  }

                  private void dontRemove2() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedPrivateMethodsChainedUsage() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private void unused() {
                      unused2();
                  }

                  private void unused2() {
                  }

                  public void dontRemove() {
                      dontRemove2();
                  }

                  private void dontRemove2() {
                  }
              }
              """,
            """
              class Test {

                  public void dontRemove() {
                      dontRemove2();
                  }

                  private void dontRemove2() {
                  }
              }
              """
          )
        );
    }

    @Test
    void getHostDottedClassName() {
        rewriteRun(
          // Add type stubs for all the unresolved classes
          java(
            """
            package org.apache.bcel.classfile;
            public class Attribute {}
            """,
            spec -> spec.path("org/apache/bcel/classfile/Attribute.java")
          ),
          java(
            """
            package org.apache.bcel.classfile;
            public class JavaClass {
                public Attribute[] getAttributes() { return null; }
                public int getMajor() { return 0; }
                public String getClassName() { return ""; }
            }
            """,
            spec -> spec.path("org/apache/bcel/classfile/JavaClass.java")
          ),
          java(
            """
            package org.apache.bcel.classfile;
            public class NestHost extends Attribute {
                public int getHostClassIndex() { return 0; }
                public ConstantPool getConstantPool() { return null; }
            }
            """,
            spec -> spec.path("org/apache/bcel/classfile/NestHost.java")
          ),
          java(
            """
            package org.apache.bcel.classfile;
            public class NestMembers extends Attribute {
                public String[] getClassNames() { return null; }
            }
            """,
            spec -> spec.path("org/apache/bcel/classfile/NestMembers.java")
          ),
          java(
            """
            package org.apache.bcel.classfile;
            public class ConstantPool {
                public String getConstantString(int index, byte type) { return null; }
            }
            """,
            spec -> spec.path("org/apache/bcel/classfile/ConstantPool.java")
          ),
          java(
            """
            package org.apache.bcel;
            public class Const {
                public static final byte CONSTANT_Class = 7;
                public static final int MAJOR_11 = 55;
            }
            """,
            spec -> spec.path("org/apache/bcel/Const.java")
          ),
          java(
            """
            package org.apache.bcel;
            import org.apache.bcel.classfile.JavaClass;
            public class Repository {
                public static JavaClass lookupClass(String name) { return null; }
            }
            """,
            spec -> spec.path("org/apache/bcel/Repository.java")
          ),
          java(
            """
            package edu.umd.cs.findbugs.ba;
            import org.apache.bcel.classfile.JavaClass;
            public class AnalysisContext {
                public JavaClass lookupClass(String name) { return null; }
            }
            """,
            spec -> spec.path("edu/umd/cs/findbugs/ba/AnalysisContext.java")
          ),
          java(
            """
            package javax.annotation;
            public @interface CheckForNull {}
            """,
            spec -> spec.path("javax/annotation/CheckForNull.java")
          ),
          java(
            """
            package edu.umd.cs.findbugs.util;
            public class ClassName {
                public static String toDottedClassName(String name) { return name; }
            }
            """,
            spec -> spec.path("edu/umd/cs/findbugs/util/ClassName.java")
          ),

          // Your actual test code
          java(
            """
            /*
             * Contributions to SpotBugs
             * Copyright (C) 2020, Simeon Andreev
             *
             * This library is free software; you can redistribute it and/or
             * modify it under the terms of the GNU Lesser General Public
             * License as published by the Free Software Foundation; either
             * version 2.1 of the License, or (at your option) any later version.
             *
             * This library is distributed in the hope that it will be useful,
             * but WITHOUT ANY WARRANTY; without even the implied warranty of
             * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
             * Lesser General Public License for more details.
             *
             * You should have received a copy of the GNU Lesser General Public
             * License along with this library; if not, write to the Free Software
             * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
             */
            package edu.umd.cs.findbugs.util;

            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.List;

            import javax.annotation.CheckForNull;

            import org.apache.bcel.Const;
            import org.apache.bcel.Repository;
            import org.apache.bcel.classfile.Attribute;
            import org.apache.bcel.classfile.ConstantPool;
            import org.apache.bcel.classfile.JavaClass;
            import org.apache.bcel.classfile.NestHost;
            import org.apache.bcel.classfile.NestMembers;

            import edu.umd.cs.findbugs.ba.AnalysisContext;

            /**
             * Provides checks to support JEP 181, improved nested member access.
             *
             * <p>
             * In short, JEP 181 defines "nest mates", "nest host" and "nest members" attributes in compiled files. Access rules are
             * relaxed to allow private member access between nest mates. This removes the need for the compiler to generate
             * synthetic accessors. Extra attributes are added to the separate .class files to let the compiler know which classes
             * are nested in which class.
             * </p>
             * <b>Summary</b> of terminology for JEP 181 and the added attributes:
             * <ol>
             * <li>The "nest host" is the top level class that contains nested classes, i.e. the class that corresponds to the
             * source file</li>
             * <li>The "nest members" are the nested classes in the nest host.</li>
             * <li>The "nest" consists of the nest host and the nest members.</li>
             * <li>A "nest mate" is a class within the nest, "nest mates" can access each others private members.</li>
             * <li>The nest host class has an attribute {@code NestMembers} which lists the qualified names of nested classes.</li>
             * <li>A nested class has an attribute {@code NestHost} which lists the qualified name of the nest host.</li>
             * </ol>
             *
             * @see "https://openjdk.java.net/jeps/181"
             */
            public class NestedAccessUtil {

                private static final int JAVA_11_CLASS_VERSION = Const.MAJOR_11;

                /**
                 * Checks if the specified class is a nested class or defines nested classes.
                 *
                 * @param javaClass
                 *            The class for which to check.
                 * @return {@code true} if the specified class is a nested class or defines nested class, {@code false} otherwise.
                 */
                public static boolean hasNest(JavaClass javaClass) {
                    if (supportsNestedAccess(javaClass)) {
                        Attribute[] attributes = javaClass.getAttributes();
                        for (Attribute attribute : attributes) {
                            if (attribute instanceof NestHost || attribute instanceof NestMembers) {
                                return true;
                            }
                        }
                    }
                    return false;
                }

                /**
                 * Checks whether the specified class supports nested access as per JEP 181.
                 *
                 * @param javaClass
                 *            The class for which to check.
                 * @return {@code true} if the specified class supports nested access as per JEP 181.
                 *
                 * @see NestedAccessUtil
                 */
                public static boolean supportsNestedAccess(JavaClass javaClass) {
                    return hasJava11OrAboveClassVersion(javaClass);
                }

                /**
                 * Retrieves the qualified class names of all nest mates of the specified class.
                 *
                 * @param javaClass
                 *            The class for which qualified class names of nest mates are retrieved.
                 * @param analysisContext
                 *            The analysis context, used to look-up a nest host class if required.
                 * @return The qualified class name of all nest mates. If the specified class is not a nested class or does not have
                 *         nested classes, an empty list is returned.
                 * @throws ClassNotFoundException
                 *             If a nest host class was looked-up but could not be found.
                 *
                 * @see NestedAccessUtil
                 */
                public static List<String> getNestMateClassNames(JavaClass javaClass, AnalysisContext analysisContext)
                        throws ClassNotFoundException {
                    List<String> nestMateClassNames = new ArrayList<>();

                    // check if the specified class is a nest member, if so retrieve all nest members from the nest host
                    String nestHostClassName = getHostClassName(javaClass);
                    if (nestHostClassName != null) {
                        JavaClass nestedHostClass = analysisContext.lookupClass(nestHostClassName);
                        String[] nestMemberClassNames = getNestMemberClassNames(nestedHostClass);
                        if (nestMemberClassNames != null) {
                            nestMateClassNames.addAll(Arrays.asList(nestMemberClassNames));
                            nestMateClassNames.add(nestHostClassName);
                        }
                    } else {
                        // check if the specified class is a nest host, if so retrieve the nest members from the specified class.
                        String[] nestMemberClassNames = getNestMemberClassNames(javaClass);
                        if (nestMemberClassNames != null) {
                            nestMateClassNames.addAll(Arrays.asList(nestMemberClassNames));
                            // the nest host class is always in the nest
                            String className = javaClass.getClassName();
                            nestMateClassNames.add(className);
                        }
                    }
                    return nestMateClassNames;
                }

                private static boolean hasJava11OrAboveClassVersion(JavaClass... javaClasses) {
                    for (JavaClass javaClass : javaClasses) {
                        int javaClassVersion = javaClass.getMajor();
                        if (javaClassVersion < JAVA_11_CLASS_VERSION) {
                            return false;
                        }
                    }
                    return true;
                }

                @CheckForNull
                private static String[] getNestMemberClassNames(JavaClass javaClass) {
                    Attribute[] sourceAttributes = javaClass.getAttributes();
                    for (Attribute sourceAttribute : sourceAttributes) {
                        if (sourceAttribute instanceof NestMembers) {
                            NestMembers nestMembersAttribute = (NestMembers) sourceAttribute;
                            return nestMembersAttribute.getClassNames();
                        }
                    }
                    return null;
                }

                @CheckForNull
                private static String getHostDottedClassName(JavaClass javaClass) {
                    String hostClassName = getHostClassName(javaClass);
                    if (hostClassName != null) {
                        return ClassName.toDottedClassName(hostClassName);
                    }
                    return null;
                }

                @CheckForNull
                private static String getHostClassName(JavaClass javaClass) {
                    Attribute[] attributes = javaClass.getAttributes();
                    for (Attribute attribute : attributes) {
                        if (attribute instanceof NestHost) {
                            NestHost nestHostAttribute = (NestHost) attribute;
                            int targetHostClassIndex = nestHostAttribute.getHostClassIndex();
                            ConstantPool constantPool = nestHostAttribute.getConstantPool();
                            return constantPool.getConstantString(targetHostClassIndex, Const.CONSTANT_Class);
                        }
                    }
                    return null;
                }

                public static List<JavaClass> getHostClasses(JavaClass javaClass) throws ClassNotFoundException {
                    List<JavaClass> list = new ArrayList<>();
                    if (javaClass != null) {
                        String hostClassName = getHostClassName(javaClass);
                        if (hostClassName != null) {
                            JavaClass hostClass = Repository.lookupClass(hostClassName);
                            list.add(hostClass);
                            list.addAll(getHostClasses(hostClass));
                        }
                    }
                    return list;
                }
            }
            """,
            """
            /*
             * Contributions to SpotBugs
             * Copyright (C) 2020, Simeon Andreev
             *
             * This library is free software; you can redistribute it and/or
             * modify it under the terms of the GNU Lesser General Public
             * License as published by the Free Software Foundation; either
             * version 2.1 of the License, or (at your option) any later version.
             *
             * This library is distributed in the hope that it will be useful,
             * but WITHOUT ANY WARRANTY; without even the implied warranty of
             * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
             * Lesser General Public License for more details.
             *
             * You should have received a copy of the GNU Lesser General Public
             * License along with this library; if not, write to the Free Software
             * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
             */
            package edu.umd.cs.findbugs.util;

            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.List;

            import javax.annotation.CheckForNull;

            import org.apache.bcel.Const;
            import org.apache.bcel.Repository;
            import org.apache.bcel.classfile.Attribute;
            import org.apache.bcel.classfile.ConstantPool;
            import org.apache.bcel.classfile.JavaClass;
            import org.apache.bcel.classfile.NestHost;
            import org.apache.bcel.classfile.NestMembers;

            import edu.umd.cs.findbugs.ba.AnalysisContext;

            /**
             * Provides checks to support JEP 181, improved nested member access.
             *
             * <p>
             * In short, JEP 181 defines "nest mates", "nest host" and "nest members" attributes in compiled files. Access rules are
             * relaxed to allow private member access between nest mates. This removes the need for the compiler to generate
             * synthetic accessors. Extra attributes are added to the separate .class files to let the compiler know which classes
             * are nested in which class.
             * </p>
             * <b>Summary</b> of terminology for JEP 181 and the added attributes:
             * <ol>
             * <li>The "nest host" is the top level class that contains nested classes, i.e. the class that corresponds to the
             * source file</li>
             * <li>The "nest members" are the nested classes in the nest host.</li>
             * <li>The "nest" consists of the nest host and the nest members.</li>
             * <li>A "nest mate" is a class within the nest, "nest mates" can access each others private members.</li>
             * <li>The nest host class has an attribute {@code NestMembers} which lists the qualified names of nested classes.</li>
             * <li>A nested class has an attribute {@code NestHost} which lists the qualified name of the nest host.</li>
             * </ol>
             *
             * @see "https://openjdk.java.net/jeps/181"
             */
            public class NestedAccessUtil {

                private static final int JAVA_11_CLASS_VERSION = Const.MAJOR_11;

                /**
                 * Checks if the specified class is a nested class or defines nested classes.
                 *
                 * @param javaClass
                 *            The class for which to check.
                 * @return {@code true} if the specified class is a nested class or defines nested class, {@code false} otherwise.
                 */
                public static boolean hasNest(JavaClass javaClass) {
                    if (supportsNestedAccess(javaClass)) {
                        Attribute[] attributes = javaClass.getAttributes();
                        for (Attribute attribute : attributes) {
                            if (attribute instanceof NestHost || attribute instanceof NestMembers) {
                                return true;
                            }
                        }
                    }
                    return false;
                }

                /**
                 * Checks whether the specified class supports nested access as per JEP 181.
                 *
                 * @param javaClass
                 *            The class for which to check.
                 * @return {@code true} if the specified class supports nested access as per JEP 181.
                 *
                 * @see NestedAccessUtil
                 */
                public static boolean supportsNestedAccess(JavaClass javaClass) {
                    return hasJava11OrAboveClassVersion(javaClass);
                }

                /**
                 * Retrieves the qualified class names of all nest mates of the specified class.
                 *
                 * @param javaClass
                 *            The class for which qualified class names of nest mates are retrieved.
                 * @param analysisContext
                 *            The analysis context, used to look-up a nest host class if required.
                 * @return The qualified class name of all nest mates. If the specified class is not a nested class or does not have
                 *         nested classes, an empty list is returned.
                 * @throws ClassNotFoundException
                 *             If a nest host class was looked-up but could not be found.
                 *
                 * @see NestedAccessUtil
                 */
                public static List<String> getNestMateClassNames(JavaClass javaClass, AnalysisContext analysisContext)
                        throws ClassNotFoundException {
                    List<String> nestMateClassNames = new ArrayList<>();

                    // check if the specified class is a nest member, if so retrieve all nest members from the nest host
                    String nestHostClassName = getHostClassName(javaClass);
                    if (nestHostClassName != null) {
                        JavaClass nestedHostClass = analysisContext.lookupClass(nestHostClassName);
                        String[] nestMemberClassNames = getNestMemberClassNames(nestedHostClass);
                        if (nestMemberClassNames != null) {
                            nestMateClassNames.addAll(Arrays.asList(nestMemberClassNames));
                            nestMateClassNames.add(nestHostClassName);
                        }
                    } else {
                        // check if the specified class is a nest host, if so retrieve the nest members from the specified class.
                        String[] nestMemberClassNames = getNestMemberClassNames(javaClass);
                        if (nestMemberClassNames != null) {
                            nestMateClassNames.addAll(Arrays.asList(nestMemberClassNames));
                            // the nest host class is always in the nest
                            String className = javaClass.getClassName();
                            nestMateClassNames.add(className);
                        }
                    }
                    return nestMateClassNames;
                }

                private static boolean hasJava11OrAboveClassVersion(JavaClass... javaClasses) {
                    for (JavaClass javaClass : javaClasses) {
                        int javaClassVersion = javaClass.getMajor();
                        if (javaClassVersion < JAVA_11_CLASS_VERSION) {
                            return false;
                        }
                    }
                    return true;
                }

                @CheckForNull
                private static String[] getNestMemberClassNames(JavaClass javaClass) {
                    Attribute[] sourceAttributes = javaClass.getAttributes();
                    for (Attribute sourceAttribute : sourceAttributes) {
                        if (sourceAttribute instanceof NestMembers) {
                            NestMembers nestMembersAttribute = (NestMembers) sourceAttribute;
                            return nestMembersAttribute.getClassNames();
                        }
                    }
                    return null;
                }

                @CheckForNull
                private static String getHostClassName(JavaClass javaClass) {
                    Attribute[] attributes = javaClass.getAttributes();
                    for (Attribute attribute : attributes) {
                        if (attribute instanceof NestHost) {
                            NestHost nestHostAttribute = (NestHost) attribute;
                            int targetHostClassIndex = nestHostAttribute.getHostClassIndex();
                            ConstantPool constantPool = nestHostAttribute.getConstantPool();
                            return constantPool.getConstantString(targetHostClassIndex, Const.CONSTANT_Class);
                        }
                    }
                    return null;
                }

                public static List<JavaClass> getHostClasses(JavaClass javaClass) throws ClassNotFoundException {
                    List<JavaClass> list = new ArrayList<>();
                    if (javaClass != null) {
                        String hostClassName = getHostClassName(javaClass);
                        if (hostClassName != null) {
                            JavaClass hostClass = Repository.lookupClass(hostClassName);
                            list.add(hostClass);
                            list.addAll(getHostClasses(hostClass));
                        }
                    }
                    return list;
                }
            }
            """
          )
        );
    }

    @SuppressWarnings("MissingSerialAnnotation")
    @Test
    void doNotRemoveCustomizedSerialization() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private void writeObject(java.io.ObjectOutputStream out) {}
                  private Object writeReplace() {}
                  private void readObject(java.io.ObjectInputStream in) {}
                  private void readObjectNoData() {}
                  private Object readResolve() {}
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveMethodsWithAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.MethodSource;
              import java.util.stream.Stream;

              class Test {
                  @MethodSource("sourceExample")
                  void test(String input) {
                  }
                  private Stream<Object> sourceExample() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1536")
    @Test
    void privateMethodWithBoundedGenericTypes() {
        rewriteRun(
          //language=java
          java(
            """
              public class TestClass {
                  void method() {
                      checkMethodInUse("String", "String");
                  }

                  private static void checkMethodInUse(String arg0, String arg1) {
                  }

                  private static <T> void checkMethodInUse(String arg0, T arg1) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMethodsOnNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;

              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }

                  class InnerTest {
                      void test(String input) {
                      }
                      private Stream<Object> unused() {
                          return null;
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(String input) {
                  }

                  class InnerTest {
                      void test(String input) {
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/4076")
    @Test
    void doNotRemoveMethodsWithUnusedSuppressWarningsOnClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;

              @SuppressWarnings("unused")
              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }
                  private Stream<Object> anotherUnused() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/4076")
    @Test
    void doNotRemoveMethodsWithUnusedSuppressWarningsOnClassNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;

              @SuppressWarnings("unused")
              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }

                  class InnerTest {
                      void test(String input) {
                      }
                      private Stream<Object> unused() {
                          return null;
                      }
                  }
              }
              """
          )
        );
    }
}
