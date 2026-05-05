plugins {
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2")
    }
}

sourceSets {
    main {
        java {
            srcDirs("../src/main/java")
        }
        resources {
            srcDirs("src/main/resources")
        }
    }
}

intellijPlatform {
    buildSearchableOptions = false
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
