import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.dsl.LockMode

// One place sets the compiler, its warnings and the static analysers for every project (F1).
plugins {
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.pitest) apply false
}

// H2: the plugins' own classpath is locked too (buildscript-gradle.lockfile).
buildscript {
    configurations.classpath { resolutionStrategy.activateDependencyLocking() }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "net.ltgt.errorprone")

    // H2: every configuration is locked, transitive versions included, and the lockfiles
    // (gradle.lockfile in each project) are committed. STRICT: a configuration with no lock state
    // fails to resolve. After changing a version in gradle/libs.versions.toml, rewrite them with
    //   ./gradlew resolveAndLockAll --write-locks
    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.STRICT
    }
    tasks.register("resolveAndLockAll") {
        description = "Resolves every configuration, so that --write-locks records them all (H2)."
        notCompatibleWithConfigurationCache("resolves configurations at execution time")
        doFirst { require(gradle.startParameter.isWriteDependencyLocks) { "run with --write-locks" } }
        doLast { configurations.filter { it.isCanBeResolved }.forEach { it.resolve() } }
    }

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
