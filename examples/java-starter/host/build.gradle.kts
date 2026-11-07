// Host layer (A1): wires the system, reads the clock, and is the runnable entry point.
plugins { application }

dependencies {
    implementation(project(":loans:application"))
}

application { mainClass = "starter.host.Main" }
