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
        resources {
            // Only module-local resources (e.g. META-INF/plugin.xml). Shared themes are copied
            // into /themes/ below so plugin.xml paths like /themes/MoonDark.theme.json resolve.
            srcDir("src/main/resources")
        }
    }
}

intellijPlatform {
    buildSearchableOptions = false
}

tasks.named<ProcessResources>("processResources") {
    // Listing ../src/main/resources/themes as a srcDir flattens files to the JAR root, which
    // breaks themeProvider path="/themes/...". Copy the directory so resources land at themes/*.
    from(rootProject.layout.projectDirectory.dir("src/main/resources/themes")) {
        into("themes")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
