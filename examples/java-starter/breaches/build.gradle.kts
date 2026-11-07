// Planted breaches (F5). Each folder breaches/<check-id>/src is its own source set, compiled on
// its own, so one breach can never hide or cause another. Nothing depends on this project, so
// no breach reaches the host's distribution; only the architecture meta-test reads the classes,
// by path, from build/classes/java/<source set>.

// Everything a breach may need to compile: the real code, the test support, and the two
// third-party libraries that two breaches misuse on purpose (Gson outside its wrapper, Mockito).
// They are declared on the (empty) main source set, whose compile classpath every breach shares.
dependencies {
    implementation(project(":loans:application"))
    implementation(project(":testing"))
    implementation(libs.gson)
    implementation(libs.mockito)
}

val sourceSets = the<SourceSetContainer>()
val breachClasspath = sourceSets["main"].compileClasspath
val breachFolders = projectDir.listFiles { f -> f.isDirectory && f.resolve("src").isDirectory }!!.sortedBy { it.name }

val allBreaches by tasks.registering {
    description = "Compiles every planted breach, each in its own source set."
}

breachFolders.forEach { folder ->
    val set = sourceSets.create(folder.name.replace('-', '_')) {
        java.setSrcDirs(listOf(folder.resolve("src")))
        compileClasspath = breachClasspath
    }
    allBreaches { dependsOn(set.classesTaskName) }
}

// Nothing is packaged from here, ever.
tasks.named("jar") { enabled = false }
