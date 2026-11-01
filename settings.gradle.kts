rootProject.name = "strange-eons"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":vendor", ":strange-eons-script-api", ":strange-eons-app")
