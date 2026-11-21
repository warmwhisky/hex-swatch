# Performance (parked)

**Status:** ⏸ Parked: waiting for real circumstances of performance noticed by Ben.

## Measured (2026-11-21, v1.4.0, gtdb9 files)
- One scan, after the first run: 4 KB → 0.5 ms · 22–34 KB → 4–6 ms · 48 KB CSS → 10 ms · 1.2 MB built CSS → 175 ms
- Runs on open, then 300 ms after typing stops; runs on the UI thread, so the editor waits for it
- The two Tailwind searches (TW, TW_BW) are ~75% of the time
- Files over 2 MB are skipped

## Fixes, if it's ever noticed
- [ ] 1. Run the search in the background (non-blocking read action), apply marks on the UI thread. Don't wait for indexing.
- [ ] 2. Merge TW and TW_BW into one search; skip characters that can't start a class (~2× faster)
- [ ] 3. Lower the size cap from 2 MB to 500 KB (skips built files like `public/css/app.css`)
