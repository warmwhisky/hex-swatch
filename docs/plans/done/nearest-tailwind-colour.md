# Nearest Tailwind colour

**Status:** ✅ Done, 1.9.0 (Marketplace later).

A design hands you `#3b82f6`. Is that a Tailwind colour, or close to one? The circle tells you, and turns
`bg-[#3b82f6]` into `bg-blue-500` in one click.

## How it behaves
- **Default / setting:** on, no setting (a tooltip and the click you already make on the circle)
- Only in Tailwind projects (Tailwind found in `node_modules` or `package.json`)
- Circle tooltip: `= blue-500` (exact) or `≈ blue-600` (nearest), using the project's Tailwind 3 or 4 table
- "Nearest" measured in Oklab (how different colours look), not plain RGB; maths already there for oklch
- Inside an arbitrary class (`bg-[#3b82f6]`, `hover:text-[#fff]/50`): Alt+click swaps it for the Tailwind class; the tooltip says so
- Tried and dropped: a line in the picker (from 2026.3 its parts are in a module plugins for 2023.3 can't use) and a
  strip beside it (the IDE doesn't say where the picker is, so they overlapped; also needed two Escapes)
- Hex in plain CSS: tooltip only
- Also for rgb()/hsl()/oklch() inside `[...]`

## Steps
- [x] Nearest-colour search over the table (Oklab distance)
- [x] Tooltip on the circle
- [x] Swap `-[#hex]` for `-<colour>-<shade>`, keeping prefix and `/opacity`
- [x] Ben tries it
- [x] README, description, change notes, version bump
