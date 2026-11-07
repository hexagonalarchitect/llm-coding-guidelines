import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

// One place sets the compiler, its warnings and the static analysers for every project (F1).
plugins {
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.pitest) apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "net.ltgt.errorprone")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
    }

    dependencies {
        "errorprone"(rootProject.libs.errorprone.core)
        "errorprone"(rootProject.libs.nullaway)
    }

    tasks.withType<JavaCompile>().configureEach {
        // F1: every warning is an error; the opt-in list is "all".
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        options.encoding = "UTF-8"
        options.errorprone {
            disableWarningsInGeneratedCode = true
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "starter")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // jqwik keeps its failure database under build/, not in the source tree.
        systemProperty("jqwik.database", layout.buildDirectory.file("jqwik-database").get().asFile.path)
        testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }
}
