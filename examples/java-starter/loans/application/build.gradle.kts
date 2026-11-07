// Application layer of the "loans" context (A1): its own domain, the kernel and infrastructure.
dependencies {
    api(project(":loans:domain"))
    api(project(":infrastructure:notices"))
    testImplementation(project(":testing"))
}
