// The project graph mirrors the guide's layers (A1). Breaches are a project of their own that no
// production project depends on, so they never reach a production artifact (F5).
rootProject.name = "java-starter"

include(
    ":kernel",                   // Kernel: depends on nothing but the JDK
    ":loans:domain",             // Domain of the "loans" context: pure decider and values
    ":loans:application",        // Application of the "loans" context: workflows over the wrapper
    ":infrastructure:notices",   // Infrastructure: the one wrapper allowed to use Gson (A10)
    ":host",                     // Host: wires the system and owns the clock
    ":testing",                  // Test support: counting assertions and the time-budget judge
    ":architecture",             // The self-test harness: checks plus the meta-test
    ":breaches",                 // Planted violations, one per check; never on a production path
)

dependencyResolutionManagement {
    repositories { mavenCentral() }
}
