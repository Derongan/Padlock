plugins {
    idea
    java
}

group = "com.derongan.minecraft"

repositories {
    mavenCentral()
    maven("https://repo.dmulloy2.net/repository/public") // Protocol Lib
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.17-R0.1-SNAPSHOT") // The Spigot API with no shadowing. Requires the OSS repo.
    compileOnly("com.comphenix.protocol:ProtocolLib:4.7.0")

    implementation("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation("org.spigotmc:spigot-api:1.17-R0.1-SNAPSHOT") // The Spigot API with no shadowing. Requires the OSS repo.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}


tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(mutableMapOf("plugin_version" to version))
        }
    }
}