pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("http://4thline.org/m2") }
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "DLNAMirroring"
include(":app")
