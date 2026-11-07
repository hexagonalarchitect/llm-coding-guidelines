// The self-test harness (F5). Test code only: the check registry and the meta-test that proves
// each check against the real code and against its planted breach in breaches/<check-id>/.
dependencies {
    testImplementation(project(":testing"))
    testImplementation(project(":loans:application")) // the real code, so real and breach tests can load
    testImplementation(libs.archunit)
    testImplementation(libs.junit.testkit)
}

// The real code is read by path, not from the classpath, so the meta-test sees exactly the
// compiled classes of the production projects and of their tests, and the production sources
// (for the source scans, such as C12's).
val realProjects = listOf(":kernel", ":loans:domain", ":loans:application", ":infrastructure:notices", ":host", ":testing")
fun classesOf(path: String, set: String) =
    rootDir.resolve(path.removePrefix(":").replace(':', '/')).resolve("build/classes/java/$set")

tasks.test {
    // Every task whose output the meta-test reads by path, so Gradle orders them (and does not refuse
    // an implicit dependency): the real projects' classes, and all of :breaches' source sets.
    dependsOn(realProjects.map { "$it:classes" }, realProjects.map { "$it:testClasses" },
        ":breaches:allBreaches", ":breaches:classes", ":breaches:testClasses")
    val real = realProjects.flatMap { listOf(classesOf(it, "main"), classesOf(it, "test")) }
    val realSources = realProjects.map { rootDir.resolve(it.removePrefix(":").replace(':', '/')).resolve("src/main/java") }
    inputs.files(real).withPropertyName("realClasses")
    inputs.files(realSources).withPropertyName("realSources")
    inputs.dir(rootDir.resolve("breaches/build/classes/java")).withPropertyName("breaches")
    systemProperty("starter.real", real.joinToString(File.pathSeparator))
    systemProperty("starter.realSources", realSources.joinToString(File.pathSeparator))
    systemProperty("starter.breaches", rootDir.resolve("breaches").path)
    inputs.file(rootDir.resolve("standards/rules.toml")).withPropertyName("rules")
    systemProperty("starter.rules", rootDir.resolve("standards/rules.toml").path)
}
