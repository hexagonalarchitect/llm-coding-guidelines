// Domain layer of the "loans" context (A1): depends on the kernel only.
plugins {
    alias(libs.plugins.pitest)
}

dependencies {
    api(project(":kernel"))
    testImplementation(project(":testing"))
    testImplementation(libs.jqwik)
}

// Mutation testing of the functional core (E-group). Runs in `check`; the budget judge is off
// under PIT only, because mutated and instrumented code is slower by design.
pitest {
    pitestVersion = libs.versions.pitest.core
    junit5PluginVersion = libs.versions.pitest.junit5
    targetClasses = setOf("starter.loans.domain.*")
    excludedMethods = setOf("toString", "hashCode")
    threads = 2
    mutationThreshold = 90
    timestampedReports = false
    outputFormats = setOf("HTML", "XML")
    jvmArgs = listOf("-Dstarter.budgets=off", "-Djqwik.database=" + layout.buildDirectory.file("jqwik-database").get().asFile.path)
}

tasks.named("check") { dependsOn("pitest") }
