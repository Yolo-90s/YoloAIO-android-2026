pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Jitsi Meet SDK lives in their self-hosted GitHub maven mirror —
        // not on Maven Central. Required for `org.jitsi.react:jitsi-meet-sdk`
        // AND for Jitsi's patched forks of React-Native libraries that ship
        // under `com.facebook.react` with `-jitsi-<sha>` version suffixes
        // (e.g. react-native-webrtc, react-native-webview). Listing both
        // groups so this repo isn't consulted for non-Jitsi artifacts.
        maven {
            url = uri("https://github.com/jitsi/jitsi-maven-repository/raw/master/releases")
            content {
                includeGroup("org.jitsi.react")
                includeGroup("com.facebook.react")
                // JavaScriptCore — React Native's JS engine. RN's mavenCentral
                // copy doesn't carry the specific r250231 build the Jitsi
                // SDK pins to; it's mirrored in the Jitsi repo.
                includeGroup("org.webkit")
            }
        }
    }
}

rootProject.name = "YoloAIO"
include(":app")
 