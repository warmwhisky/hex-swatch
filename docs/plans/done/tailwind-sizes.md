# Tailwind size picker

**Status:** ✅ Released as 1.6.0 (2026-11-24).

Click a size class like `p-1` and get a row of sizes, like the shade row. Only the number changes
(`hover:px-4` → `hover:px-6`).

## How it behaves
- Off by default. Settings → Tools → Hex Swatch, three checkboxes:
  spacing (`p m gap space`, negatives too) · width/height (`w h size min-w min-h max-h`) · text size/rounded/shadow
- No marks. A plain click on the class places the cursor as normal and shows the row under the line
- The row doesn't take the keyboard: any key closes it, so the click stays a normal click
- No popup on drag-select, Shift/Ctrl/Alt+click or double-click
- Rounded and shadow rows follow Tailwind 3 or 4; not covered: `w-full`, `w-1/2`, `p-[3px]`, `max-w-*`

## Steps
- [x] Settings page with three checkboxes, registered in `plugin.xml`
- [x] Plain click on a size class → row that doesn't take focus
- [x] `showRow()` reused with text-only boxes, wraps at 18
- [x] Ben tried it: kept as is
- [x] README, description, change notes, version 1.6.0
- [ ] Commit and merge to main (Ben)
