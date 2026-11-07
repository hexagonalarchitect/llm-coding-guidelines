// The self-test harness (F5). Test code only: the check registry and the meta-test that proves
// each check against the real code and against its planted breach in breaches/<check-id>/.
dependencies {
    testImplementation(project(":testing"))
    testImplementation(project(":loans:application")) // the real code, so real and breach tests can load
    testImplementation(libs.archunit)
    testImplementation(libs.junit.testkit)
}

// The real code is read by path, not from the classpath, so the meta-test sees exactly the
// compiled classes of the production projects and of their tests.
val realProjects = listOf(":kernel", ":loans:domain", ":loans:application", ":infrastructure:notices", ":host", ":testing")
fun classesOf(path: String, set: String) =
    rootDir.resolve(path.removePrefix(":").replace(':', '/')).resolve("build/classes/java/$set")

tasks.test {
    dependsOn(realProjects.map { "$it:classes" }, realProjects.map { "$it:testClasses" }, ":breaches:allBreaches")
    val real = realProjects.flatMap { listOf(classesOf(it, "main"), classesOf(it, "test")) }
    inputs.files(real).withPropertyName("realClasses")
    inputs.dir(rootDir.resolve("breaches/build/classes/java")).withPropertyName("breaches")
    systemProperty("starter.real", real.joinToString(File.pathSeparator))
    systemProperty("starter.breaches", rootDir.resolve("breaches").path)
}
