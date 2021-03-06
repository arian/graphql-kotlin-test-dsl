import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    id("com.diffplug.spotless") version "5.9.0"
    id("org.jetbrains.dokka") version "1.4.20"
    `java-library`
    `maven-publish`
    signing
}

group = "com.symbaloo"
version = "1.0.9"
description = "A Kotlin DSL to write Tests for graphql-java"
val repoDescription = description
val repoUrl = "https://github.com/arian/graphql-kotlin-test-dsl"

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    api("com.graphql-java:graphql-java:15.+")
    implementation("com.jayway.jsonpath:json-path:2.5.+")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.opentest4j:opentest4j:1.2.0")
    implementation("org.slf4j:slf4j-api:1.7.+")
    testImplementation(kotlin("reflect"))
    testImplementation("org.slf4j:slf4j-simple:1.7.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.23")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    javadoc {
        dependsOn("dokkaJavadoc")
    }

    register<Jar>("javadocJar") {
        dependsOn("javadoc")
        archiveClassifier.set("javadoc")
        from("$buildDir/javadoc")
    }
}

spotless {
    kotlin {
        ktlint("0.40.0")
    }
    kotlinGradle {
        ktlint("0.40.0")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name.set("GraphQL Kotlin Test DLS")
                description.set(repoDescription)
                url.set(repoUrl)

                licenses {
                    license {
                        name.set("MIT")
                        url.set("http://www.opensource.org/licenses/mit-license.php")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("arian")
                        name.set("Arian Stolwijk")
                        email.set("arian@symbaloo.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/arian/graphql-kotlin-test-dsl.git")
                    url.set("https://github.com/arian/graphql-kotlin-test-dsl")
                }
            }
        }

        repositories {
            maven {

                val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots")
                url = if (isReleaseVersion) releasesRepoUrl else snapshotsRepoUrl

                // these can be set through gradle.properties
                if (properties.containsKey("mavenRepoUser")) {
                    credentials {
                        username = properties["sonatypeRepoUser"] as String?
                        password = properties["sonatypeRepoPassword"] as String?
                    }
                }
            }
        }
    }
}

signing {
    setRequired { isReleaseVersion && gradle.taskGraph.hasTask("publish") }
    if (isReleaseVersion) {
        sign(publishing.publications["mavenJava"])
    }
}
