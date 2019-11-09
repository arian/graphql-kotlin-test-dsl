plugins {
    kotlin("jvm") version "1.3.50"
    id("org.jlleitschuh.gradle.ktlint") version "9.0.0"
    `java-library`
    `maven-publish`
    signing
}

group = "com.symbaloo"
version = "1.0.1"
description = "A Kotlin DSL to write Tests for graphql-java"
val repoDescription = description
val repoUrl = "https://github.com/arian/graphql-kotlin-test-dsl"

repositories {
    mavenCentral()
}

dependencies {
    api("com.graphql-java:graphql-java:12.+")
    implementation(kotlin("stdlib"))
    implementation("com.jayway.jsonpath:json-path:2.4.+")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.opentest4j:opentest4j:1.2.0")
    implementation("org.slf4j:slf4j-simple:1.7.+")
    implementation("org.slf4j:slf4j-api:1.7.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.20")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])

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
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

                // these can be set through gradle.properties
                if (properties.containsKey("mavenRepoUser")) {
                    credentials {
                        username = properties["mavenRepoUser"] as String
                        password = properties["mavenRepoPassword"] as String
                    }
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
