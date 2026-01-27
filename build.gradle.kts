plugins {
    java
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "com.geysermc.tpaforms"
version = "2.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.codemc.org/repository/maven-public/") // For SkinsRestorer
    maven("https://repo.essentialsx.net/releases/") // For EssentialsX
    mavenLocal() // For GeyserMenu companion if built locally
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    
    // Floodgate API for Bedrock player detection
    compileOnly("org.geysermc.floodgate:api:2.2.2-SNAPSHOT")
    
    // SkinsRestorer API for skin support on cracked servers
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.0.13")
    
    // EssentialsX for TPA events
    compileOnly("net.essentialsx:EssentialsX:2.20.1")

    // GeyserMenu Companion API
    // Option 1: Use local JAR file if built locally
    compileOnly(files("libs/geyser-menu-companion-api.jar"))
    // Option 2: Once published, use Maven coordinates:
    // compileOnly("com.geysermenu:companion-api:1.0.0")

    // Lombok for cleaner code
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("TPAFormsBridge-${version}.jar")
    
    // Minimize the JAR by removing unused classes
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
