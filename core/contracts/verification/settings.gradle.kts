pluginManagement { repositories { mavenCentral(); gradlePluginPortal(); google() } }
dependencyResolutionManagement {
    repositories { mavenCentral(); google() }
    versionCatalogs { create("libs") { from(files("../../../gradle/libs.versions.toml")) } }
}
rootProject.name = "LiteTravelDeliveryTests"
include(":core:contracts", ":delivery-tests")
project(":core:contracts").projectDir = settingsDir.resolve("..")
