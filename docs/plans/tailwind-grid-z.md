# Tailwind grid and z-index rows

**Status:** 📝 Planned, not started.

More size rows for small fixed scales people tweak all the time: grid columns/rows, spans and z-index.
Only the number changes (`lg:grid-cols-3` → `lg:grid-cols-4`).

## How it behaves
- `grid-cols-*`, `grid-rows-*`: 1–12, none
- `col-span-*`, `row-span-*`: 1–12, full
- `z-*`: 0 10 20 30 40 50 auto (also `-z-10`)
- Text-only boxes like the spacing row; tooltip says what it means (`grid-cols-4  4 equal columns`)
- **Default / setting:** off; its own checkbox "grid and z-index" (a plain click on text, doesn't fit the other three)
- Not covered: `grid-cols-[...]`, `col-start-*`/`col-end-*`

## Steps
- [ ] Patterns and rows; settings checkbox
- [ ] Ben tries it
- [ ] README, description, change notes, version bump
