plugins {
    kotlin("jvm") version "2.3.20"
}

repositories { mavenCentral() }

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val parityTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the expr-eval parity and Java interoperability suite."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.expreval.ParityTest")
}

tasks.test { dependsOn(parityTest) }
