import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

subprojects {
    plugins.withId("java") {
        apply(plugin = "jacoco")
        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.14"
        }
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(25)
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            systemProperty("apple.awt.UIElement", "true")
            finalizedBy("jacocoTestReport")
            testLogging {
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
        tasks.named<JacocoReport>("jacocoTestReport") {
            reports {
                xml.required = true
                csv.required = true
            }
        }
    }

    // Published library modules: development builds publish as -SNAPSHOT to
    // the Central Portal snapshot repository; signing applies only when a key
    // is supplied.
    plugins.withId("com.vanniktech.maven.publish") {
        group = "org.strangeeons"
        version = publishedVersion()
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }
            pom {
                url = "https://github.com/strange-eons/strange-eons"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "christophercurrie"
                        name = "Christopher Currie"
                        url = "https://github.com/christophercurrie"
                    }
                }
                scm {
                    url = "https://github.com/strange-eons/strange-eons"
                    connection = "scm:git:https://github.com/strange-eons/strange-eons.git"
                    developerConnection = "scm:git:ssh://git@github.com/strange-eons/strange-eons.git"
                }
            }
        }
    }
}

fun Project.publishedVersion(): String {
    val base = listOf("major", "minor", "patch").joinToString(".") {
        providers.gradleProperty("app.$it").get()
    }
    val type = providers.gradleProperty("app.type").getOrElse("DEVELOPMENT")
    return if (type == "DEVELOPMENT") "$base-SNAPSHOT" else base
}
