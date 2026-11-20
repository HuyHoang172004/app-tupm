plugins { kotlin("jvm") }
kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir(rootDir.resolve("../../../app/src/main/java"))
        kotlin.include("**/delivery/InstallCoordinator.kt", "**/navigation/FeatureLaunchPolicy.kt")
    }
}
dependencies { implementation(project(":core:contracts")); testImplementation(kotlin("test-junit")) }
