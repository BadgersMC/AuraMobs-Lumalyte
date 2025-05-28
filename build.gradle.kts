import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    `maven-publish`
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "dev.aurelium"
version = project.property("projectVersion") as String

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://maven.nostal.ink/repository/maven-snapshots/")
    }
    maven {
        url = uri("https://repo.dreeam.cn/repository/maven-public/")
    }
}

dependencies {
    implementation("net.objecthunter:exp4j:0.4.8")
    implementation("com.github.Archy-X:Polyglot:1.2.1") {
        exclude("org.yaml", "snakeyaml")
    }
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.2.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.7-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.6.1")
    compileOnly("cn.dreeam.leaf:leaf-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly( "com.github.cryptomorin:kingdoms:1.17.10-BETA")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<ShadowJar> {
    val projectVersion: String by project
    archiveFileName.set("AuraMobs-${projectVersion}.jar")

    relocate("co.aikar.commands", "dev.aurelium.auramobs.acf")
    relocate("co.aikar.locales", "dev.aurelium.auramobs.locales")
    relocate("com.archyx.polyglot", "dev.aurelium.auramobs.polyglot")
    relocate("org.bstats", "dev.aurelium.auramobs.bstats")
    relocate("net.objecthunter.exp4j", "dev.aurelium.auramobs.exp4j")
    relocate("org.spongepowered.configurate", "dev.aurelium.auramobs.configurate")
    relocate("io.leangen.geantyref", "dev.aurelium.auramobs.geantyref")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("projectVersion" to project.version)
        }
    }
    build {
        dependsOn(shadowJar)
    }
    assemble {
        dependsOn(shadowJar)
    }
    javadoc {
        options {
            (this as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isDebug = true
    options.compilerArgs.add("-parameters")
    options.isFork = true
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.Archy-X"
            artifactId = "AuraMobs"
            version = project.property("projectVersion") as String

            from(components["java"])
        }
    }
}