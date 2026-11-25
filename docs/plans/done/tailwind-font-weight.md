# Tailwind font weight row

**Status:** ✅ Done, 1.10.0 (Marketplace later).

Click a weight class like `font-semibold` for a row of weights; only the weight changes
(`md:font-medium` → `md:font-bold`). Same as the size rows.

## How it behaves
- Row: thin, extralight, light, normal, medium, semibold, bold, extrabold, black (same in Tailwind 3 and 4)
- Each box shows "Aa" at that weight in JetBrains Mono (comes with the IDE; no black, so that box shows its name); tooltip `font-bold  700`
- **Default / setting:** off; part of the existing checkbox, renamed "text size/weight, rounded, shadow"
  (label only, stored setting `look` unchanged so it stays on for people who have it on). Same click rules as the size rows
- Not `font-sans`/`font-mono` (those are font families, no scale)

## Steps
- [x] Pattern for `font-<weight>`, added to the "look" setting; rename its checkbox label
- [x] Box that draws "Aa" at a weight instead of a label (fall back to the label if the font has no such weight)
- [x] Ben tries it
- [x] README, description, change notes, version bump
