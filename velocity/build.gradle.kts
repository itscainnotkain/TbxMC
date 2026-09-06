plugins {
    id("net.kyori.blossom") version "2.1.0"
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
}

sourceSets {
    main {
        blossom {
            javaSources {
                property("version", rootProject.version.toString())
            }
        }
    }
}

dependencies {
    implementation(project(":minecraft-common"))

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    compileOnly("dev.dejvokep:boosted-yaml:1.3")
}

tasks {
    compileJava {
        options.release.set(17)
        options.encoding = Charsets.UTF_8.name()
    }
    shadowJar {
        configurations = listOf(project.configurations.runtimeClasspath.get())

    }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
