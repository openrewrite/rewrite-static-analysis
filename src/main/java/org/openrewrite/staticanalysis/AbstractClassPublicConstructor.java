package org.openrewrite.staticanalysis;

import java.util.Collections;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

public class AbstractClassPublicConstructor extends Recipe {
  @Override
  public String getDisplayName() {
    return "Make constructors of abstract classes protected";
  }

  @Override
  public String getDescription() {
    return "Constructors of abstract classes can only be called in constructors of their subclasses. "
      + "Therefore the visibility of public constructors are reduced to protected.";
  }

  @Override
  public Set<String> getTags() {
    return Collections.singleton("RSPEC-S5993");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        if (cd.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Abstract)) {
          doAfterVisit(CHANGE_CONSTRUCTOR_ACCESS_LEVEL_VISITOR);
        }
        return cd;
      }
    };
  }

  static final TreeVisitor<?, ExecutionContext> CHANGE_CONSTRUCTOR_ACCESS_LEVEL_VISITOR = new JavaIsoVisitor<ExecutionContext>() {
    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
      J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
      if (md.isConstructor() && md.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Public)) {
        md = md.withModifiers(ListUtils.map(md.getModifiers(),
          mod -> mod.getType() == J.Modifier.Type.Public ? mod.withType(J.Modifier.Type.Protected) : mod));
      }
      return md;
    }
  };
}
