pluginManagement {
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupAndSubgroups("com.github")
            }
        }
    }
}

rootProject.name = "Gadgetbridge"

include(":app")
include(":GBDaoGenerator")
include(":FitCodeGenerator")
include(":KaitaiAuxGenerator")
include(":GBProtobufGenerator")
