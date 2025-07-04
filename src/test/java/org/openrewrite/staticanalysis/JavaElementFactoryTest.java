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
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.internal.JavaTypeCache;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.staticanalysis.JavaElementFactory.newInstanceMethodReference;
import static org.openrewrite.staticanalysis.JavaElementFactory.newStaticMethodReference;

class JavaElementFactoryTest implements RewriteTest {

    JavaTypeCache typeCache = new JavaTypeCache();

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().typeCache(typeCache));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void instanceMethodReference() {
        RecipeSpec spec = RecipeSpec.defaults();
        defaults(spec);
        SourceFile sourceFile = spec.getParsers().getFirst().build().parse(
          //language=java
          """
            package foo;
            import java.util.function.Supplier;
            class Foo {
                int foo() {
                    return 0;
                }
                Supplier<Integer> bar() {
                    return this::foo;
                }
            }
            """
        ).findFirst().get();
        JavaType.Class fooType = typeCache.get("foo.Foo");
        assertThat(fooType).isNotNull();

        JavaType.Method fooMethod = fooType.getMethods().stream().filter(m -> "foo".equals(m.getName())).findFirst().get();
        assertThat(fooMethod).isNotNull();

        J.MemberReference reference = new JavaIsoVisitor<AtomicReference<J.MemberReference>>() {
            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, AtomicReference<J.MemberReference> collector) {
                collector.compareAndSet(null, memberRef);
                return super.visitMemberReference(memberRef, collector);
            }
        }.reduce(sourceFile, new AtomicReference<>(null)).get();
        assertThat(reference).isNotNull();

        J.MemberReference methodReference = newInstanceMethodReference(reference.getContaining(), fooMethod, reference.getType());
        assertThat(SemanticallyEqual.areEqual(reference, methodReference)).isTrue();
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void staticMethodReference() {
        RecipeSpec spec = RecipeSpec.defaults();
        defaults(spec);
        SourceFile sourceFile = spec.getParsers().getFirst().build().parse(
          //language=java
          """
            package foo;
            import java.util.stream.Stream;
            class Foo {
                int foo() {
                    return 0;
                }
                long bar() {
                    return Stream.of(new Foo()).map(Foo::foo).count();
                }
            }
            """
        ).findFirst().get();
        JavaType.Class fooType = typeCache.get("foo.Foo");
        assertThat(fooType).isNotNull();

        JavaType.Method fooMethod = fooType.getMethods().stream().filter(m -> "foo".equals(m.getName())).findFirst().get();
        assertThat(fooMethod).isNotNull();

        J.MemberReference reference = new JavaIsoVisitor<AtomicReference<J.MemberReference>>() {
            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, AtomicReference<J.MemberReference> collector) {
                collector.compareAndSet(null, memberRef);
                return super.visitMemberReference(memberRef, collector);
            }
        }.reduce(sourceFile, new AtomicReference<>(null)).get();
        assertThat(reference).isNotNull();

        J.MemberReference methodReference = newStaticMethodReference(fooMethod, false, reference.getType());
        assertThat(SemanticallyEqual.areEqual(reference, methodReference)).isTrue();
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void qualifiedStaticMethodReference() {
        RecipeSpec spec = RecipeSpec.defaults();
        defaults(spec);
        SourceFile sourceFile = spec.getParsers().getFirst().build().parse(
          //language=java
          """
            package foo;
            import java.util.stream.Stream;
            class Foo {
                long foo() {
                    return Stream.of(new Foo()).map(java.lang.Object::toString).count();
                }
            }
            """
        ).findFirst().get();
        JavaType.Class fooType = typeCache.get("foo.Foo");
        assertThat(fooType).isNotNull();

        J.MemberReference reference = new JavaIsoVisitor<AtomicReference<J.MemberReference>>() {
            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, AtomicReference<J.MemberReference> collector) {
                collector.compareAndSet(null, memberRef);
                return super.visitMemberReference(memberRef, collector);
            }
        }.reduce(sourceFile, new AtomicReference<>(null)).get();
        assertThat(reference).isNotNull();
        assertThat(reference.getMethodType()).isNotNull();

        J.MemberReference methodReference = newStaticMethodReference(reference.getMethodType(), true, reference.getType());
        assertThat(SemanticallyEqual.areEqual(reference, methodReference)).isTrue();
    }
}
