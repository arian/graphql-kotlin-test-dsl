plugins {
    kotlin("jvm") version "1.3.50"
    id("org.jlleitschuh.gradle.ktlint") version "9.0.0"
}

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
