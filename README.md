# Hex Swatch

A JetBrains IDE plugin (PhpStorm, WebStorm, IntelliJ IDEA and the rest, 2023.3+) that puts a small colour mark
before every colour in **any** file, including places the IDE's own swatches miss, such as Tailwind classes.

- **Hex colours** (`#abc`, `#aabbcc`, `#aabbccaa`, also inside `hover:bg-[#a10000]/50`): a **circle**.
  Click it for the IDE's colour picker; the hex updates as you drag.
- **rgb() / rgba()** (`rgb(255, 0, 0)`, `rgba(0,128,255,.5)`, `rgb(255 0 0 / 50%)`): a **circle** too, same picker.
  The new colour is written in the same style (commas or spaces, alpha as decimal or %).
- **hsl() / hsla() / oklch()** (`hsl(0 100% 50%)`, `hsla(210, 50%, 40%, .8)`, `oklch(63.7% 0.237 25.331)`):
  a **circle**, same picker, written back in the same format and style.
- **Tailwind colour classes** (`border-red-600`, `hover:text-sky-300/50`): a **diamond**.
  Click it for a row of that colour's shades, 50 to 950; only the shade number changes.
  `text-white`, `bg-black/50` and the like get a diamond too: click it to swap between white and black.
  Colours follow the project's Tailwind version (3 or 4, read from `node_modules` or `package.json`).
- **Tailwind sizes** (off by default): click a size class like `p-4`, `-mt-2`, `w-12`, `text-lg`, `rounded-md` or
  `shadow-sm` for a row of sizes; only the size changes (`hover:px-4` → `hover:px-6`). No marks, and any key closes
  the row. Turn it on in Settings → Tools → Hex Swatch: spacing (`p m gap space`), width/height
  (`w h size min-w min-h max-h`), text size/rounded/shadow. Not covered: `w-full`, `w-1/2`, `p-[3px]`, `max-w-*`.

It reads the plain text, so it works in CSS, Blade, HTML, JS, PHP, Vue, Markdown and anything else.
It changes no IDE settings.

## Performance
- Scans only when a file opens and 300 ms after you stop typing, never on every key.
- One pass through the file per kind of mark, no background indexing. Files over 2 MB are skipped.

## Screenshots
Every kind of mark in one file ([`docs/screenshots/demo.css`](docs/screenshots/demo.css)):

![Every kind of colour mark](docs/screenshots/Screenshot-Demo.png)

Click a circle for the colour picker, even inside a Tailwind class:

![Colour picker on a hex inside a Tailwind class](docs/screenshots/Screenshot-Tailwind-Hex.png)

Click a diamond for the row of shades:

![Tailwind shade row](docs/screenshots/Screenshot-Tailwind-Shades.png)

Or swap white and black:

![Tailwind white/black swap](docs/screenshots/Screenshot-Tailwind-Black-White-Switch.png)

## Install
- **Marketplace:** Settings → Plugins → Marketplace → search "Hex Swatch".
- **From a zip:** Settings → Plugins → ⚙ → Install Plugin from Disk → `hex-swatch-<version>.zip`.

## Optional: hide the Tailwind plugin's own swatches
The IDE's Tailwind CSS plugin draws its own colour swatch on Tailwind classes, so with Hex Swatch you see two.
To keep only the diamonds:

1. Settings → search **Tailwind CSS** (Languages & Frameworks → Style Sheets → Tailwind CSS).
2. In the **Configuration** JSON, change `"colorDecorators": true` to `"colorDecorators": false`.
3. OK. If they don't go at once: Ctrl+Shift+A → **Restart Tailwind CSS Service**.

If the swatches come back in another project, repeat it there (the Tailwind plugin may store it per project).

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
