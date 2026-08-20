import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
}

group = "com.geysermc.tpaforms"
version = "2.1.0-folia"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.codemc.org/repository/maven-public/") // For SkinsRestorer
}

dependencies {
    // Folia API (26.2). Folia's API is a superset of Paper's for our purposes and is the
    // authoritative surface for the target server, so compiling against it guarantees the
    // region schedulers / teleportAsync we rely on actually exist there.
    compileOnly("dev.folia:folia-api:26.2.build.5-beta")

    // The three API jars below are pulled non-transitively on purpose: their POMs drag in
    // org.spigotmc:spigot-api, which declares the same Gradle capability as folia-api and makes
    // resolution fail. We only need their own classes to compile against.
    // Floodgate API for Bedrock player detection (SOFT at runtime - see plugin.yml)
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT") { isTransitive = false }

    // SkinsRestorer API for skin support on cracked servers (SOFT)
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.12.5") { isTransitive = false }

    // NOTE: the EssentialsX compileOnly dependency is gone on purpose. This plugin used to catch
    // net.ess3.api.events.TPARequestEvent to intercept incoming requests; the TPA flow is now
    // implemented natively, so there is nothing left to compile against.

    // GeyserMenu Companion API (SOFT - reflectively gated at runtime)
    compileOnly(files("libs/geyser-menu-companion-api.jar"))
}

java {
    // Compile against the Java 25 folia-api class files but emit Java 21 bytecode so the same
    // jar keeps running on the existing Paper/Purpur/Leaf server (Java 21) as well as Folia 26.2.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// folia-api 26.2 publishes Gradle metadata declaring "JVM 25 or newer". We compile *against* it
// (javac happily reads newer class files off the classpath) but emit Java 21 bytecode, so the
// resolution attribute has to be relaxed explicitly or Gradle rejects the dependency.
configurations.compileClasspath {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveFileName.set("TPAFormsBridge-${version}.jar")
}
