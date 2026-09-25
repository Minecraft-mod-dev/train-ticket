plugins {
    id("java-library")
    id("com.gradleup.shadow") version "8.3.6"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

val mcVersion: String = project.findProperty("minecraftVersion")?.toString() ?: "1.21.1"
val vaultVersion: String = project.findProperty("vaultVersion")?.toString() ?: "1.7"

// Append Minecraft version to project.version for releases (idempotent)
val rawVersion = project.version.toString()
if (!rawVersion.contains("-mc")) {
    project.version = "$rawVersion-mc$mcVersion"
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    // Paper snapshots repository (needed for unreleased MC minor versions)
    maven("https://repo.papermc.io/repository/maven-snapshots/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // Paper API
    val paperApiVersion = project.findProperty("paperApiVersion")?.toString() ?: "${mcVersion}-R0.1-SNAPSHOT"
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion}")

    // Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:${vaultVersion}")

    // bStats
    implementation("org.bstats:bstats-bukkit:3.0.2")

    // JSON
    implementation("org.json:json:20230618")

    // SQLite
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {

    runServer {
        minecraftVersion(mcVersion)

        jvmArgs(
            "-Xms2G",
            "-Xmx2G"
        )
    }


    processResources {

        val props = mapOf(
            "version" to project.version
        )

        filesMatching("plugin.yml") {
            expand(props)
        }
    }


    shadowJar {

        // 不生成 -all 后缀
        archiveClassifier.set("")

        // 排除签名文件
        exclude(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA"
        )
    }


    build {
        dependsOn(shadowJar)
    }
}