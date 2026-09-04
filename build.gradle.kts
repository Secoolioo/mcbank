plugins {
    java
}

group = "de.secoolio"
version = "1.0.0"
description = "Item-Bank mit Punkte-Rangliste (NPCs mit Spieler-Skin, Sidebar, /reichste, /kontostand)"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

// Fest auf einen Build gepinnt (reproduzierbar). Die Paper-Doku nutzt alternativ "26.2.build.+".
val paperApi = "io.papermc.paper:paper-api:26.2.build.121-stable"

dependencies {
    compileOnly(paperApi)
    // Tests brauchen Material, ItemRarity und YamlConfiguration; compileOnly gilt nicht für Tests.
    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Seit Gradle 9 ausdrücklich nötig.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    // Jede Nutzung veralteter Paper-API sichtbar machen, ohne den Build abzubrechen.
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    archiveFileName = "BankRanking-${project.version}.jar"
}
