// Module serveur — backend auto-hébergé (Ktor + SQLite) qui remplace ntfy.sh
// pour le combat en ligne. Code 100 % en français, JVM pure (pas d'Android).
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
    // Fat JAR auto-suffisant pour le déploiement
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

// Versions épinglées (cf. CLAUDE.md §2 et consignes du module serveur)
val versionKtor = "2.3.12"

kotlin {
    jvmToolchain(17)
}

application {
    // Point d'entrée du serveur (fonction main de Application.kt)
    mainClass.set("com.s7venz.pocodex.serveur.ApplicationKt")
}

dependencies {
    // Serveur Ktor (moteur Netty) + extensions
    implementation("io.ktor:ktor-server-netty:$versionKtor")
    implementation("io.ktor:ktor-server-websockets:$versionKtor")
    implementation("io.ktor:ktor-server-content-negotiation:$versionKtor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$versionKtor")
    implementation("io.ktor:ktor-server-call-logging:$versionKtor")

    // Base de données : JDBC SQLite brut (aucun ORM, code lisible)
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")

    // Hachage des mots de passe
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Journalisation
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Tests : hôte de test Ktor + client (CIO + WebSockets) + kotlin-test
    testImplementation("io.ktor:ktor-server-test-host:$versionKtor")
    testImplementation("io.ktor:ktor-client-cio:$versionKtor")
    testImplementation("io.ktor:ktor-client-websockets:$versionKtor")
    testImplementation("io.ktor:ktor-client-content-negotiation:$versionKtor")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

tasks.test {
    useJUnit()
}

// Le fat JAR de déploiement : serveur/build/libs/pocodex-serveur.jar
tasks.shadowJar {
    archiveFileName.set("pocodex-serveur.jar")
}

// Simulateur d'invité pour les tests de bout en bout (cf. OutilInvite.kt).
// Usage : ./gradlew :serveur:simulerInvite --args="ws://localhost:8080 <jeton> <code> <idPokemon>"
tasks.register<JavaExec>("simulerInvite") {
    group = "application"
    description = "Simule un invité qui rejoint une salle de combat et joue automatiquement."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.s7venz.pocodex.outils.OutilInviteKt")
}
