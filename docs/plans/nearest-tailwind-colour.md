# Nearest Tailwind colour

**Status:** 📝 Planned, not started.

A design hands you `#3b82f6`. Is that a Tailwind colour, or close to one? The circle tells you, and turns
`bg-[#3b82f6]` into `bg-blue-500` in one click.

## How it behaves
- **Default / setting:** on, no setting (a tooltip and the click you already make on the circle)
- Only in Tailwind projects (Tailwind found in `node_modules` or `package.json`)
- Circle tooltip: `= blue-500` (exact) or `≈ blue-600` (nearest), using the project's Tailwind 3 or 4 table
- "Nearest" measured in Oklab (how different colours look), not plain RGB; maths already there for oklch
- Inside an arbitrary class (`bg-[#3b82f6]`, `hover:text-[#fff]/50`): a click option swaps it for the Tailwind class
- ❓ How to offer the swap: a line under the colour picker, or Alt+click on the circle?
- ❓ Hex in plain CSS: tooltip only, or also offer `var(--color-blue-500)` (Tailwind 4)?
- Also for rgb()/hsl()/oklch() inside `[...]`

## Steps
- [ ] Nearest-colour search over the table (Oklab distance)
- [ ] Tooltip on the circle
- [ ] Swap `-[#hex]` for `-<colour>-<shade>`, keeping prefix and `/opacity`
- [ ] Ben tries it
- [ ] README, description, change notes, version bump
