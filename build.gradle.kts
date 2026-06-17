plugins {
    id("com.google.protobuf") version "0.9.4"
    id("org.graalvm.buildtools.native") version "1.1.2"
    application
}

group = "ch.ost.cal.ade.plugins"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass.set("ch.ost.cal.ade.plugins.archunit.Main")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:4.29.3")
    implementation("org.freemarker:freemarker:2.3.33")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

tasks.test {
    useJUnitPlatform()
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3"
    }
}

graalvmNative {
    binaries {
        named("main") {
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
            })
        }
        all {
            resources.autodetect()
        }
    }
}

// Fat JAR so the plugin runs as a single file:
//   java -jar build/libs/plugin.jar --info
tasks.register<Jar>("fatJar") {
    archiveFileName.set("plugin.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "ch.ost.cal.ade.plugins.archunit.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
    dependsOn(tasks.compileJava)
}

tasks.build {
    dependsOn(tasks["fatJar"])
}
