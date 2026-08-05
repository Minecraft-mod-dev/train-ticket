plugins {
    id("java-library")
    id("com.gradleup.shadow") version "8.3.6"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")

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
        minecraftVersion("1.21.1")

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