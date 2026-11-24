# Tailwind colour swap

**Status:** ✅ Released as 1.7.0 (2026-11-24). On by default (no setting).

Clicking the diamond on `bg-red-500` shows the shade row as now, plus a second row underneath: every Tailwind
colour at the current shade (`slate-500`, `gray-500` … `rose-500`, white, black). Clicking one swaps only the
colour name (`bg-red-500` → `bg-blue-500`, `hover:text-red-500/50` → `hover:text-blue-500/50`).

## How it behaves
- One swatch per colour at the current shade, never the full colour × shade grid
- Tooltip with the colour name on each swatch (the greys look alike)
- Current colour marked, like the current shade is now
- `red-500` → white/black drops the shade; white/black → a colour adds `-500`
- On `bg-white`/`bg-black`: shade row hidden, colour row shows the 500s
- Colour list follows Tailwind 3 or 4, like the shade tables
- Both rows together still never cover the line (same rule as 1.4.1), near the bottom of the editor too

## Steps
- [x] Colour rows under the shade row: small boxes, no labels, two rows (14 wide on V4, 12 on V3)
- [x] Rewrite the colour name, keep prefix, shade and `/opacity`
- [x] white/black cases
- [x] Builds; verifyPlugin compatible on all three
- [x] Ben tried it: kept as is
- [x] README, description, change notes, version 1.7.0 (zip built and verified)
- [x] README screenshots of the new popup (Ben)
- [x] Commit and push
