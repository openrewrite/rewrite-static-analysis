plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
    id("org.openrewrite.rewrite") version("6.29.2")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

dependencies {
    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    compileOnly("org.projectlombok:lombok:latest.release")
    compileOnly("com.google.errorprone:error_prone_core:2.+:with-dependencies") {
        exclude("com.google.auto.service", "auto-service-annotations")
    }
    implementation("org.apache.commons:commons-text:latest.release")
    implementation("org.openrewrite.meta:rewrite-analysis:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-csharp:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-groovy:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-kotlin:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    testImplementation("com.google.code.gson:gson:latest.release")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains:annotations:24.+")
    testImplementation("org.junit-pioneer:junit-pioneer:2.+")
    testImplementation("org.openrewrite:rewrite-groovy")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.projectlombok:lombok:latest.release")
    testRuntimeOnly("com.google.code.findbugs:jsr305:latest.release")
    testRuntimeOnly("org.openrewrite:rewrite-java-17")
    rewrite("org.openrewrite.recipe:rewrite-static-analysis:1.22.0")
}
repositories {
    mavenCentral()
}
rewrite {
    activeRecipe("org.openrewrite.staticanalysis.CommonStaticAnalysis")
//    activeRecipe("org.openrewrite.staticanalysis.EqualsAvoidsNull") // not found
//    activeRecipe("org.openrewrite.java.RemoveObjectsIsNull") // found
    setExportDatatables(true)
}
