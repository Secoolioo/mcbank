plugins {
    java
}

group = "de.secoolio"
version = "3.2.0"
description = "Item-Bank mit Punkte-Rangliste und Kopfgeldern (WANTED-Plakate, eigenes Resourcepack)"

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

// ---------------------------------------------------------------- Resourcepack
//
// Die losen Pack-Dateien liegen eingecheckt unter src/main/pack und werden von
// tools/pack/build_pack.py sowie tools/sounds/build_sounds.py erzeugt. Ein normaler Build
// braucht deshalb kein Python.
val packQuelle = layout.projectDirectory.dir("src/main/pack")

val packZip by tasks.registering(Zip::class) {
    description = "Packt das Resourcepack reproduzierbar."
    from(packQuelle) {
        // metrics.properties beschreibt die Schriftmasse fuer die Java-Seite. Der Client
        // braucht sie nicht, sie gehoert also nicht ins Pack.
        exclude("metrics.properties")
    }
    archiveFileName = "kopfgeld.zip"
    destinationDirectory = layout.buildDirectory.dir("generated/pack")

    // Ohne diese beiden Zeilen wanderten Zeitstempel und Dateireihenfolge in die ZIP und
    // damit in ihren SHA-1. Der Hash aenderte sich bei jedem Build, und jeder Client
    // laedt das Pack neu, sobald er den Server betritt.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    entryCompression = ZipEntryCompression.DEFLATED
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
    // Die ZIP darf nicht durch die UTF-8-Filterung laufen, sonst waere sie beschaedigt.
    // Das ist hier gegeben, weil filesMatching nur plugin.yml erfasst.
    from(packZip) { into("pack") }
    from(packQuelle.file("metrics.properties")) { into("pack") }
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
