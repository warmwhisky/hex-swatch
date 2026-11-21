# Hex Swatch (JetBrains plugin, public)

Ben's general rules load from `~/.claude/CLAUDE.md`. This file is only what's specific to this plugin.

- Circle before every hex colour (click → IDE colour picker, rewrites the hex); diamond before every Tailwind
  colour class (click → row of shades 50–950, rewrites the shade number). Any file type, plain-text regex scan.
- Written in **Java, not Kotlin** (Ben reads Java; no Kotlin plugin in the build). Keep it to one commented file:
  `src/main/java/hexswatch/HexSwatchListener.java`, registered in `src/main/resources/META-INF/plugin.xml`.
- **Published:** ID `org.gtdb.hexswatch` (never change it), vendor GTDB (Ben's Marketplace vendor), MIT licence.
  Every new version: bump `version` in `build.gradle.kts` + a `<change-notes>` block in `plugin.xml`.
- **Compatibility:** compiles against IntelliJ IDEA Community 2023.3.8, Java 17 (`sinceBuild 233`). Only use APIs that
  exist there; `verifyPlugin` checks PhpStorm 2023.3, 2025.1 and the installed one.
- **Build on Ben's machine** with PhpStorm's own Java (foojay downloads Java 17 for compiling):
  `$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\PhpStorm\jbr"; .\gradlew.bat buildPlugin verifyPlugin`.
  That Java has `javac` but no `javap`; check API signatures with a small reflection script against `PhpStorm\lib\*`.
- Tailwind tables: V3 from tailwindcss 3.4 `colors.js`; V4 from tailwindcss 4.x `theme.css` (OKLCH → hex via a node
  script). Project picks by `node_modules/tailwindcss/package.json` version, else `package.json`, else V4.
- Picker call used: `ColorChooserService.getInstance().showPopup(project, colour, listener, RelativePoint, showAlpha, alphaAsPercent)`.
