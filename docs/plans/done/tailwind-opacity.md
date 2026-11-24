# Tailwind opacity row

**Status:** ✅ 1.8.0 on GitHub, Marketplace pending (2026-11-24).

A third row in the diamond popup: the current colour at each opacity. Picking one changes only the `/50` part
(`bg-red-500/50` → `bg-red-500/30`). Stops the type-look-type loop of tweaking opacity.

## How it behaves
- Row: 5 10 20 25 30 40 50 60 70 75 80 90 95 100, each box the colour over a checkerboard
- Current one outlined; none outlined if the class has an odd value like `/15`
- 100 removes the `/`: `bg-red-500/50` → `bg-red-500`; picking on a solid class adds `/<n>`
- Works on white/black diamonds too (`text-white/80`)
- **Default / setting:** on, no setting (only shows after clicking a mark)
- Popup still never covers the line

## Steps
- [x] Checkerboard drawing in `ShadeCell` (the diamond already draws see-through colours)
- [x] Opacity row under the colour rows; rewrite or add/remove the `/<n>` part
- [x] Builds; verifyPlugin compatible on all three
- [x] Ben tried it: works
- [x] README, description, change notes (1.8.0)
- [x] Commit and push
