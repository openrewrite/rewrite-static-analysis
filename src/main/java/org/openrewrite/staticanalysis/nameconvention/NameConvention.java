package org.openrewrite.staticanalysis.nameconvention;

public interface NameConvention {

    String applyNameConvention(String normalizedName);
}
