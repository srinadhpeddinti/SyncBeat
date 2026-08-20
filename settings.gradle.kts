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
    }
}

rootProject.name = "SyncParty"

include(":app")

include(":core:common")
include(":core:playback")
include(":core:bluetooth")
include(":core:networking")
include(":core:synchronization")
include(":core:audiotransfer")
include(":core:partyengine")

include(":feature:home")
include(":feature:createparty")
include(":feature:joinparty")
include(":feature:party")
include(":feature:medialibrary")
include(":feature:diagnostics")

include(":service:playback")

