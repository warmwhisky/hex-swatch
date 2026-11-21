# Hex Swatch

A JetBrains IDE plugin (PhpStorm, WebStorm, IntelliJ IDEA and the rest, 2023.3+) that puts a small colour mark
before every colour in **any** file, including places the IDE's own swatches miss, such as Tailwind classes.

- **Hex colours** (`#abc`, `#aabbcc`, `#aabbccaa`, also inside `hover:bg-[#a10000]/50`): a **circle**.
  Click it for the IDE's colour picker; the hex updates as you drag.
- **Tailwind colour classes** (`border-red-600`, `hover:text-sky-300/50`): a **diamond**.
  Click it for a row of that colour's shades, 50 to 950; only the shade number changes.
  Colours follow the project's Tailwind version (3 or 4, read from `node_modules` or `package.json`).

It reads the plain text, so it works in CSS, Blade, HTML, JS, PHP, Vue, Markdown and anything else. The scan runs
300 ms after you stop typing; files over 2 MB are skipped. It changes no IDE settings.

## Install
- **Marketplace:** Settings → Plugins → Marketplace → search "Hex Swatch".
- **From a zip:** Settings → Plugins → ⚙ → Install Plugin from Disk → `hex-swatch-<version>.zip`.

## Build
Needs any JDK 17+ to run Gradle (Gradle downloads the Java 17 used for compiling).

    ./gradlew buildPlugin    # -> build/distributions/hex-swatch-<version>.zip
    ./gradlew verifyPlugin   # JetBrains' compatibility check

All the code is one commented file: `src/main/java/hexswatch/HexSwatchListener.java`, registered in
`src/main/resources/META-INF/plugin.xml`.

## New version
1. Bump `version` in `build.gradle.kts`.
2. Add a block at the top of `<change-notes>` in `plugin.xml`.
3. `./gradlew buildPlugin verifyPlugin`, then upload the zip on the Marketplace.

## Licence
MIT, © GTDB ([gtdb.org](https://gtdb.org)).
