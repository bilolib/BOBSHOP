plugins {
    kotlin("jvm") version "2.2.20-RC"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.bilolib"
version = "1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven(url = "https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compileOnly(files("libs/Vault.jar"))
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.9.6")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
}
tasks {
    runServer {
        minecraftVersion("1.21")
    }
    shadowJar {
        archiveBaseName.set("BOBShop")
        archiveClassifier.set("") // "-all" ekini kaldırır
        archiveVersion.set("")    // "1" gibi versiyonu kaldırır
    }

}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
tasks.shadowJar {
    minimize()
    relocate("com.zaxxer.hikari", "com.bilolib.libs.hikari")
    relocate("org.sqlite", "com.bilolib.libs.sqlite")
}