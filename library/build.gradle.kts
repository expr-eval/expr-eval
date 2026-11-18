plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.dokka") version "2.2.0"
    `maven-publish`
    signing
}

group = "io.github.expr-eval"
version = providers.gradleProperty("releaseVersion").getOrElse("3.8.1-SNAPSHOT")

repositories { mavenCentral() }

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjdk-release=17")
        }
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    js(IR) {
        useEsModules()
        outputModuleName.set("expr-eval")
        nodejs()
        binaries.library()
    }
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest {
            resources.srcDir(rootProject.file("parity"))
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.14.1")
                runtimeOnly("org.junit.platform:junit-platform-launcher:1.14.1")
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

val docsJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationHtml)
}

val stage = layout.buildDirectory.dir("central-staging")
val artifact = "expr-eval"
val projectUrl = "https://github.com/expr-eval/expr-eval"
val release = version.toString()

publishing {
    publications {
        named<MavenPublication>("jvm") {
            artifactId = artifact
            artifact(docsJar)
            pom {
                name.set("expr-eval JVM")
                description.set("A Kotlin and Java expression parser and evaluator with expr-eval syntax.")
                url.set(projectUrl)
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                    }
                }
                developers {
                    developer {
                        id.set("expr-eval")
                        name.set("expr-eval contributors")
                        url.set(projectUrl)
                    }
                }
                scm {
                    url.set(projectUrl)
                    connection.set("scm:git:$projectUrl.git")
                    developerConnection.set("scm:git:ssh://git@github.com/expr-eval/expr-eval.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "centralStaging"
            url = uri(stage)
        }
    }
}

val signCentral = providers.gradleProperty("centralSigning").map(String::toBoolean).getOrElse(false)
if (signCentral) {
    signing {
        useGpgCmd()
        sign(publishing.publications["jvm"])
    }
}

val coordinatePath = "${group.toString().replace('.', '/')}/$artifact/$release"
val validateCentralBundle by tasks.registering {
    doLast {
        require(signCentral && !release.endsWith("-SNAPSHOT")) {
            "Run with -PcentralSigning=true -PreleaseVersion=<release> and a configured GPG key."
        }
    }
}
val stagePublication = tasks.named("publishJvmPublicationToCentralStagingRepository")
stagePublication.configure {
    mustRunAfter(validateCentralBundle)
}
tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Builds a signed archive for manual upload to the Maven Central Portal."
    dependsOn(validateCentralBundle, stagePublication, tasks.named("jvmTest"))
    from(stage) { include("$coordinatePath/**") }
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("$artifact-$release-central.zip")
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE.txt")) { into("META-INF") }
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
