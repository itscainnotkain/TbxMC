import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.jar.JarFile
import java.net.URLClassLoader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun gitCommitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectErrorStream(true)
            .start()
        val result = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (process.exitValue() == 0) result else "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}

plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
    id("com.diffplug.spotless") version "8.10.2" apply false
}

defaultTasks("collectBuilds")

group = "io.tebex"
version = "2.4.2"
val buildCommit = gitCommitHash()

val collectBuilds = tasks.register("collectBuilds", Sync::class.java) {
    group = "build"
    description = "Builds all shaded jars and copies them into the top-level builds directory."
    into(layout.projectDirectory.dir("builds"))
}

tasks.register("processSources", Copy::class.java) {
    val props = mapOf("@VERSION@" to rootProject.version)
    from("src/main/java")
    into(layout.buildDirectory.dir("processedSources")) // Destination for processed sources
    filteringCharset = "UTF-8"
    expand(props)
}

tasks.withType<JavaCompile> {
    dependsOn("processSources")
    source = fileTree(layout.buildDirectory.dir("processedSources"))
}

subprojects {
    plugins.apply("java")
    plugins.apply("com.gradleup.shadow")
	plugins.apply("com.diffplug.spotless")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
	
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.named("shadowJar", ShadowJar::class.java) {
        archiveFileName.set("tebex-${project.name}-${rootProject.version}-${buildCommit}.jar")
        relocate("okhttp3", "io.tebex.plugin.libs.okhttp3")
        relocate("okio", "io.tebex.plugin.libs.okio")
        relocate("kotlin", "io.tebex.plugin.libs.kotlin")
        relocate("com.google.gson", "io.tebex.plugin.libs.gson")
        relocate("io.gsonfire", "io.tebex.plugin.libs.gsonfire")
        relocate("dev.dejvokep.boostedyaml", "io.tebex.plugin.libs.boostedyaml")
        relocate("org.jetbrains.annotations", "io.tebex.plugin.libs.jetbrains")
        relocate("com.cryptomorin.xseries", "io.tebex.plugin.libs.xseries")
        // Gson/generated adapters are reflective: do not minimize SDK dependencies.
        doLast {
            val archive = archiveFile.get().asFile
            val tempArchive = archive.resolveSibling("${archive.name}.tmp")

            ZipInputStream(archive.inputStream()).use { input ->
                ZipOutputStream(tempArchive.outputStream()).use { output ->
                    var entry = input.nextEntry
                    while (entry != null) {
                        val replacement = ZipEntry(entry.name)
                        if (entry.time >= 0) {
                            replacement.time = entry.time
                        }
                        output.putNextEntry(replacement)

                        if (entry.name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) {
                            val manifest = Manifest(input)
                            manifest.mainAttributes.remove(Attributes.Name("Multi-Release"))
                            manifest.write(output)
                        } else {
                            input.copyTo(output)
                        }

                        output.closeEntry()
                        input.closeEntry()
                        entry = input.nextEntry
                    }
                }
            }

            Files.move(tempArchive.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    if (!project.name.endsWith("-common")) collectBuilds.configure {
        val shadowJarTask = tasks.named("shadowJar", ShadowJar::class.java)
        dependsOn(shadowJarTask)
        from(shadowJarTask.flatMap { it.archiveFile })
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            name = "spigotmc-repo"
        }
        maven("https://hub.spigotmc.org/nexus/content/groups/public/") {
            name = "spigotmc-public"
        }
        maven("https://oss.sonatype.org/content/groups/public/") {
            name = "sonatype"
        }
        maven("https://repo.opencollab.dev/main/") {
            name = "opencollab-snapshot-repo"
        }
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "paper-repo"
        }
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
            name = "extendedclip-repo"
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "sonatype-snapshots"
        }
        maven("https://maven.nucleoid.xyz/") {
            name = "nucleoid"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "neoforged"
        }
    }

    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

    tasks.named("processResources", Copy::class.java) {
        val props = mutableMapOf<String, Any>(
            "version" to rootProject.version,
            "@VERSION@" to rootProject.version,
            "mod_version" to rootProject.version
        )
        listOf(
            "minecraft_version",
            "minecraft_version_range",
            "forge_version",
            "forge_version_range",
            "neo_version",
            "neo_version_range",
            "loader_version_range",
            "mod_id",
            "mod_name",
            "mod_license",
            "mod_authors",
            "mod_description"
        ).forEach { key ->
            project.findProperty(key)?.let { props[key] = it }
        }
        inputs.properties(props)
        filteringCharset = "UTF-8"
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        filesNotMatching("**/*.zip") {
            expand(props)
        }
    }
}

project(":folia") {
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

listOf(
    "fabric-26.1",
    "fabric-26.2"
).forEach { projectName ->
    val fabricProject = project(":$projectName")
    fabricProject.configure<JavaPluginExtension> {
        sourceSets {
            getByName("main") {
                java {
                    srcDir("src/main/kotlin")
                }
            }
        }
    }
}

project(":forge-26.1") {
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

listOf(
    "forge-1.20.1",
    "neoforge-1.20.2"
).forEach { projectName ->
    project(":$projectName") {
        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

listOf(
    "forge-1.21.1",
    "neoforge-1.21.1"
).forEach { projectName ->
    project(":$projectName") {
        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
}

// The included build's tests are explicit: Gradle does not run composite tests implicitly.
tasks.register("verifyArtifacts") {
    group = "verification"
    dependsOn(collectBuilds)
    doLast {
        val platforms = subprojects.filterNot { it.name.endsWith("-common") }
        check(platforms.size == 12) { "Unexpected platform artifact count" }
        platforms.forEach { platform ->
            val archive = platform.tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
            JarFile(archive).use { jar ->
                val required = listOf(
                    "io/tebex/http/PluginApi.class",
                    "io/tebex/model/QueuedCommand.class",
                    "io/tebex/headless/model/Basket.class",
                    "io/tebex/minecraft/runtime/DeliveryQueue.class",
                    "io/tebex/plugin/libs/gson/Gson.class",
                    "io/tebex/plugin/libs/gsonfire/GsonFireBuilder.class",
                    "io/tebex/plugin/libs/okhttp3/OkHttpClient.class"
                )
                required.forEach { check(jar.getEntry(it) != null) { "${platform.name}: missing $it" } }
                check(jar.entries().asSequence().none { it.name.startsWith("io/tebex/sdk/") }) {
                    "${platform.name}: legacy SDK classes packaged"
                }
                listOf("io/tebex/http/PluginApi.class", "io/tebex/minecraft/runtime/DeliveryQueue.class").forEach {
                    val bytes = jar.getInputStream(jar.getEntry(it)).use { stream -> stream.readBytes() }
                    val major = ((bytes[6].toInt() and 255) shl 8) or (bytes[7].toInt() and 255)
                    check(major == 52) { "${platform.name}: shared code is not Java 8 ($major)" }
                }
                val descriptor = when {
                    platform.name == "bukkit" || platform.name == "folia" -> "plugin.yml"
                    platform.name == "bungeecord" -> "bungee.yml"
                    platform.name == "velocity" -> "velocity-plugin.json"
                    platform.name.startsWith("fabric") -> "fabric.mod.json"
                    platform.name.startsWith("neoforge") && platform.name != "neoforge-1.20.2" -> "META-INF/neoforge.mods.toml"
                    else -> "META-INF/mods.toml"
                }
                check(jar.getEntry(descriptor) != null) { "${platform.name}: missing $descriptor" }
                val entryPoint = when {
                    platform.name == "bukkit" -> "TebexBukkitPlugin"
                    platform.name == "folia" -> "TebexFoliaPlugin"
                    platform.name == "bungeecord" -> "TebexBungeePlugin"
                    platform.name == "velocity" -> "TebexVelocityPlugin"
                    platform.name.startsWith("fabric") -> "TebexFabricPlugin"
                    platform.name.startsWith("neoforge") -> "TebexNeoForgePlugin"
                    else -> "TebexForgePlugin"
                }
                val nativeClass = jar.getJarEntry("io/tebex/plugin/$entryPoint.class")
                check(nativeClass != null) { "${platform.name}: missing entry point" }
                val nativeBytes = jar.getInputStream(nativeClass).use { it.readBytes() }
                val nativeMajor = ((nativeBytes[6].toInt() and 255) shl 8) or (nativeBytes[7].toInt() and 255)
                val expectedMajor = when {
                    platform.name == "bukkit" || platform.name == "bungeecord" -> 52
                    platform.name == "velocity" || platform.name == "forge-1.20.1" || platform.name == "neoforge-1.20.2" -> 61
                    platform.name.contains("26.") -> 69
                    else -> 65
                }
                check(nativeMajor == expectedMajor) { "${platform.name}: unexpected native Java target $nativeMajor" }
                val unshaded = listOf("com/google/gson/", "io/gsonfire/", "okhttp3/", "okio/", "kotlin/")
                check(jar.entries().asSequence().none { entry -> unshaded.any { entry.name.startsWith(it) } }) {
                    "${platform.name}: unrelocated SDK dependency"
                }
            }
            // Isolate the jar from Gradle's own libraries to catch missing reflective SDK dependencies.
            URLClassLoader(arrayOf(archive.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
                loader.loadClass("io.tebex.http.PluginApi").getConstructor().newInstance()
                loader.loadClass("io.tebex.http.HeadlessApi").getConstructor().newInstance()
                val gsonClass = loader.loadClass("io.tebex.plugin.libs.gson.Gson")
                val gson = gsonClass.getConstructor().newInstance()
                val model = loader.loadClass("io.tebex.model.QueuedCommand")
                val command = gsonClass.getMethod("fromJson", String::class.java, Class::class.java)
                    .invoke(gson, """{"id":42,"command":"say test"}""", model)
                check(model.getMethod("getId").invoke(command) == 42) { "${platform.name}: shaded model decoding failed" }
            }
        }
    }
}

tasks.register("verifyMigration") {
    group = "verification"
    description = "Runs shared/SDK tests and verifies all distributable platform jars."
    dependsOn(":minecraft-common:test", "verifyArtifacts")
    dependsOn(gradle.includedBuild("tebex-java-sdk").task(":tbx:test"))
    dependsOn(gradle.includedBuild("tebex-java-sdk").task(":headless-api:test"))
}
