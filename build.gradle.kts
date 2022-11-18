plugins {
    kotlin("jvm") version "1.7.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "net.azisaba"
version = "2.1.1"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.acrylicstyle.xyz/repository/maven-public/") }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

dependencies {
    val adventureVersion = "4.11.0"
    implementation(kotlin("stdlib"))
    implementation("dev.kord:kord-core:0.8.0-M16")
    implementation("org.slf4j:slf4j-simple:2.0.1")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.0.8")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("com.charleskorn.kaml:kaml:0.47.0") // YAML support for kotlinx.serialization
    implementation("net.azisaba.interchat:api:2.1.2")
    // ByteBuf
    implementation("io.netty:netty-buffer:4.1.82.Final")
    // Support for minecraft chat components
    implementation("net.kyori:adventure-api:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-legacy:$adventureVersion")
    implementation("net.kyori:adventure-text-serializer-plain:$adventureVersion")
}

tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
        }
    }

    shadowJar {
        manifest {
            attributes(
                "Main-Class" to "net.azisaba.guildchatdiscord.MainKt",
            )
        }
        archiveFileName.set("GuildChatDiscord.jar")
    }
}
