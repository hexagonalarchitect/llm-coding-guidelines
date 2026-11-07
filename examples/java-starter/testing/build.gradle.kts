// Test support (the guide's Test layer): the counting assertion module (E22) and the per-test
// time-budget judge (E8). Every project that tests depends on this one, and both judges are
// switched on for it by junit-platform.properties, so no test can opt out by forgetting them.
dependencies {
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    runtimeOnly(libs.junit.launcher)
}
