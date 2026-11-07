// Infrastructure layer (A1): depends on the kernel only, and is the ONE project allowed to use
// Gson (A10). `implementation`, not `api`, so Gson types never leak to callers.
dependencies {
    api(project(":kernel"))
    implementation(libs.gson)
    testImplementation(project(":testing"))
}
