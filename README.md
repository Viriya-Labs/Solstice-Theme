# Solstice Theme Suite

An atmospheric JetBrains theme ecosystem built for focus-first coding sessions.

Solstice ships as a two-plugin setup:

- `theme-free` - the core Solstice visual theme family
- `theme-pro` - premium visual workflow features layered on top

---

## What You Get

### Free: Solstice Themes

- Curated dark palette variants (`Moon`, `Sun`, `Star`)
- Tuned editor, gutter, tabs, tool window, and popup colors
- Clean contrast for long sessions without harsh glare

### Pro: Solstice Pro

- Animated harbor pulse status widget
- Marker actions (`Solstice.BUG`, `Solstice.TODO`, `Solstice.IDEA`, `Solstice.REVIEW`)
- Solstice Markers tool window for quick marker navigation and context

---

## Project Structure

```text
Solstice-Theme/
├── src/                    # Shared code/resources (themes + pro logic)
├── theme-free/             # Free plugin packaging module
├── theme-pro/              # Pro plugin packaging module
├── gradle/                 # Gradle wrapper files
├── build.gradle.kts        # Root build configuration
└── settings.gradle.kts     # Module wiring
```

---

## Tech Stack

- Kotlin + Java (IntelliJ Platform APIs)
- Gradle Kotlin DSL
- JetBrains IntelliJ Platform Gradle Plugin (`2.x`)

---

## Local Development

1. Open the project in IntelliJ IDEA.
2. Import as a Gradle project.
3. Run one of the module tasks:
   - `:theme-free:runIde`
   - `:theme-pro:runIde`

This launches a sandbox IDE with the selected plugin module.

---

## Build Plugin ZIPs

Build distributables with:

- `:theme-free:buildPlugin`
- `:theme-pro:buildPlugin`

Outputs are generated in each module's `build/distributions/` directory.

---

## Install From Disk

In any JetBrains IDE:

`Settings -> Plugins -> Gear Icon -> Install Plugin from Disk...`

Select the generated ZIP, restart the IDE, then choose a Solstice theme from:

`Settings -> Appearance & Behavior -> Appearance -> Theme`

---

## Notes

- Build outputs and IDE sandbox artifacts are intentionally gitignored.
- `theme-free` and `theme-pro` are source-tracked; generated distributions are not.

---

## License

MIT - see `LICENSE`.
