# Solstice

Solstice is a four-variant IntelliJ Platform theme set with Moon Light, Moon Dark, Sun Light, and Sun Dark palettes, plus the custom marker and harbor scene tooling in this plugin.

## Project Layout

- `src/main/resources/META-INF/plugin.xml` registers the plugin and theme provider.
- `src/main/resources/themes/MidnightHarbor.theme.json` defines the IDE UI theme.
- `src/main/resources/themes/MidnightHarbor.xml` defines the editor color scheme.

## Run It

Open this folder in IntelliJ IDEA, import it as a Gradle project, then run the `runIde` Gradle task.

## Package It

Run the `buildPlugin` Gradle task. The plugin ZIP will be created under `build/distributions/`.

## Install Locally

In IntelliJ IDEA, use:

`Settings | Plugins | gear icon | Install Plugin from Disk...`

Choose the ZIP from `build/distributions/`, restart the IDE, then select one of the `Solstice` themes from:

`Settings | Appearance & Behavior | Appearance | Theme`
