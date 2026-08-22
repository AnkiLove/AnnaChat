plugins {
    java
    id("com.gradleup.shadow") version "9.3.0"
}

group = "dev.annachat"
version = providers.gradleProperty("version").orElse("1.1.11").get()

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "placeholderapi"
        url = uri("https://repo.extendedclip.com/releases/")
    }
    maven {
        name = "momirealms"
        url = uri("https://repo.momirealms.net/releases/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("me.clip:placeholderapi:2.12.2")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(platform("net.kyori:adventure-bom:5.2.0"))
    testImplementation("net.kyori:adventure-text-serializer-legacy")
    testImplementation("net.kyori:adventure-text-serializer-plain")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("net.momirealms:sparrow-minimessage:0.5")
    implementation("net.momirealms:sparrow-yaml:1.0.12")
    implementation("org.duckdb:duckdb_jdbc:1.3.1.0")
    runtimeOnly("com.mysql:mysql-connector-j:9.7.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
    withJavadocJar()
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-parameters")
    }
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    jar {
        archiveBaseName.set("AnnaChat")
        destinationDirectory.set(layout.projectDirectory.dir("out"))
    }
    shadowJar {
        archiveBaseName.set("AnnaChat")
        archiveClassifier.set("")
        destinationDirectory.set(layout.projectDirectory.dir("out"))
        relocate("com.zaxxer.hikari", "dev.annachat.libs.hikari")
        relocate("com.mysql", "dev.annachat.libs.mysql")
        relocate("net.momirealms.sparrow", "dev.annachat.libs.sparrow")
        mergeServiceFiles()
    }
    build {
        dependsOn(shadowJar)
    }
    test {
        useJUnitPlatform()
    }
    javadoc {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).charSet = "UTF-8"
        (options as StandardJavadocDocletOptions).docEncoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}
