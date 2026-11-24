# Readable shades (contrast marks)

**Status:** 📝 Planned, not started.

`text-gray-400` on `bg-white` looks fine on your screen but fails the readability check (WCAG contrast).
When text and background are in the same class list, the shade row marks which shades are readable.

## How it behaves
- **Default / setting:** on, no setting (marks inside the popup only); add one only if someone finds them noisy
- Clicking a `text-` diamond: look for a `bg-` colour in the same `class="..."` on that line, and vice versa
- Pairs only classes with the same variants (`dark:text-*` with `dark:bg-*`, plain with plain)
- Small tick under each shade that passes; tooltip adds the ratio: `text-gray-600  4.8 : 1  passes`
- Pass = 4.5 : 1 (WCAG AA normal text) ❓ or also show 3 : 1 for large text?
- Same marks on the colour rows (each colour at the current shade)
- Works with white/black and `bg-[#hex]`
- No pair found, or either side has `/opacity`: no marks (can't know what's behind it)

## Steps
- [ ] Contrast ratio from two colours (WCAG formula)
- [ ] Find the paired class on the line, same variants
- [ ] Ticks and tooltip ratio in the shade and colour rows
- [ ] Ben tries it
- [ ] README, description, change notes, version bump
