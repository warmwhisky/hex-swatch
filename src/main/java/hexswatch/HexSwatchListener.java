// Hex Swatch: a small colour mark before every colour in any file; click it to change the colour.
//   - Hex colours (#abc, #aabbcc, #aabbccaa)            -> circle  -> PhpStorm's colour picker
//   - rgb()/rgba() (rgb(255, 0, 0), rgb(255 0 0 / 50%)) -> circle  -> same picker, written back in the same style
//   - hsl()/hsla() (hsl(0 100% 50%)), oklch() (oklch(63.7% 0.237 25.331)) -> circle -> same picker, same style
//   - Tailwind colour classes (hover:bg-red-600, /50)  -> diamond -> a row of that colour's shades (50-950)
//     Colours follow the project's Tailwind version: 3 or 4 (tableFor()).
//   - Tailwind white/black (text-white, bg-black/50)   -> diamond -> white / black, to swap between them
//   - Tailwind sizes (p-4, -mt-2, w-8, text-sm, rounded-lg, shadow-md) -> no mark; a plain click on the class
//     shows a row of its sizes. Off by default: Settings -> Tools -> Hex Swatch (Settings / SettingsPage below).
//
// Loaded by: META-INF/plugin.xml (<editorFactoryListener>). PhpStorm calls editorCreated() for every editor it opens.
// Calls:     PhpStorm's inlay API (the marks), ColorChooserService (colour picker), JBPopupFactory (shade row).
//
// How it works:
//   1. editorCreated()   - for each code editor, listen for typing and mouse clicks, then scan once.
//   2. scheduleScan()    - after typing stops for 300 ms, scan() runs once (not on every key).
//   3. scan()            - one regex pass per kind over the file text; adds marks for new colours, removes stale ones.
//   4. onClick()         - a click on a mark opens the colour picker or the shade row; a choice rewrites the text.
//                          A click on text goes to onTextClick() (4d), which looks only at the clicked line.

package hexswatch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HexSwatchListener implements EditorFactoryListener {

    // #abc, #abcd, #aabbcc, #aabbccdd. Not after a letter/digit/&/# (skips "&#039;", "abc#def"),
    // and not followed by a letter/digit/_/- (skips "#abcdefg", "#add-button").
    private static final Pattern HEX = Pattern.compile(
            "(?<![\\w&#])#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})(?![\\w-])");

    // Tailwind's 11 shades and two colour tables; each project uses the one matching its Tailwind (see tableFor()).
    //   V3: 22 colours copied from tailwindcss 3.4 (colors.js).
    //   V4: 26 colours from tailwindcss 4.3 (theme.css), OKLCH converted to hex. Adds mauve, olive, mist, taupe.
    private static final String[] SHADES = {"50", "100", "200", "300", "400", "500", "600", "700", "800", "900", "950"};
    private static final Map<String, String[]> V3 = new LinkedHashMap<>();
    private static final Map<String, String[]> V4 = new LinkedHashMap<>();
    static {
        put(V3, "slate", "#f8fafc #f1f5f9 #e2e8f0 #cbd5e1 #94a3b8 #64748b #475569 #334155 #1e293b #0f172a #020617");
        put(V3, "gray", "#f9fafb #f3f4f6 #e5e7eb #d1d5db #9ca3af #6b7280 #4b5563 #374151 #1f2937 #111827 #030712");
        put(V3, "zinc", "#fafafa #f4f4f5 #e4e4e7 #d4d4d8 #a1a1aa #71717a #52525b #3f3f46 #27272a #18181b #09090b");
        put(V3, "neutral", "#fafafa #f5f5f5 #e5e5e5 #d4d4d4 #a3a3a3 #737373 #525252 #404040 #262626 #171717 #0a0a0a");
        put(V3, "stone", "#fafaf9 #f5f5f4 #e7e5e4 #d6d3d1 #a8a29e #78716c #57534e #44403c #292524 #1c1917 #0c0a09");
        put(V3, "red", "#fef2f2 #fee2e2 #fecaca #fca5a5 #f87171 #ef4444 #dc2626 #b91c1c #991b1b #7f1d1d #450a0a");
        put(V3, "orange", "#fff7ed #ffedd5 #fed7aa #fdba74 #fb923c #f97316 #ea580c #c2410c #9a3412 #7c2d12 #431407");
        put(V3, "amber", "#fffbeb #fef3c7 #fde68a #fcd34d #fbbf24 #f59e0b #d97706 #b45309 #92400e #78350f #451a03");
        put(V3, "yellow", "#fefce8 #fef9c3 #fef08a #fde047 #facc15 #eab308 #ca8a04 #a16207 #854d0e #713f12 #422006");
        put(V3, "lime", "#f7fee7 #ecfccb #d9f99d #bef264 #a3e635 #84cc16 #65a30d #4d7c0f #3f6212 #365314 #1a2e05");
        put(V3, "green", "#f0fdf4 #dcfce7 #bbf7d0 #86efac #4ade80 #22c55e #16a34a #15803d #166534 #14532d #052e16");
        put(V3, "emerald", "#ecfdf5 #d1fae5 #a7f3d0 #6ee7b7 #34d399 #10b981 #059669 #047857 #065f46 #064e3b #022c22");
        put(V3, "teal", "#f0fdfa #ccfbf1 #99f6e4 #5eead4 #2dd4bf #14b8a6 #0d9488 #0f766e #115e59 #134e4a #042f2e");
        put(V3, "cyan", "#ecfeff #cffafe #a5f3fc #67e8f9 #22d3ee #06b6d4 #0891b2 #0e7490 #155e75 #164e63 #083344");
        put(V3, "sky", "#f0f9ff #e0f2fe #bae6fd #7dd3fc #38bdf8 #0ea5e9 #0284c7 #0369a1 #075985 #0c4a6e #082f49");
        put(V3, "blue", "#eff6ff #dbeafe #bfdbfe #93c5fd #60a5fa #3b82f6 #2563eb #1d4ed8 #1e40af #1e3a8a #172554");
        put(V3, "indigo", "#eef2ff #e0e7ff #c7d2fe #a5b4fc #818cf8 #6366f1 #4f46e5 #4338ca #3730a3 #312e81 #1e1b4b");
        put(V3, "violet", "#f5f3ff #ede9fe #ddd6fe #c4b5fd #a78bfa #8b5cf6 #7c3aed #6d28d9 #5b21b6 #4c1d95 #2e1065");
        put(V3, "purple", "#faf5ff #f3e8ff #e9d5ff #d8b4fe #c084fc #a855f7 #9333ea #7e22ce #6b21a8 #581c87 #3b0764");
        put(V3, "fuchsia", "#fdf4ff #fae8ff #f5d0fe #f0abfc #e879f9 #d946ef #c026d3 #a21caf #86198f #701a75 #4a044e");
        put(V3, "pink", "#fdf2f8 #fce7f3 #fbcfe8 #f9a8d4 #f472b6 #ec4899 #db2777 #be185d #9d174d #831843 #500724");
        put(V3, "rose", "#fff1f2 #ffe4e6 #fecdd3 #fda4af #fb7185 #f43f5e #e11d48 #be123c #9f1239 #881337 #4c0519");
        put(V4, "slate", "#f8fafc #f1f5f9 #e2e8f0 #cad5e2 #90a1b9 #62748e #45556c #314158 #1d293d #0f172b #020618");
        put(V4, "gray", "#f9fafb #f3f4f6 #e5e7eb #d1d5dc #99a1af #6a7282 #4a5565 #364153 #1e2939 #101828 #030712");
        put(V4, "zinc", "#fafafa #f4f4f5 #e4e4e7 #d4d4d8 #9f9fa9 #71717b #52525c #3f3f46 #27272a #18181b #09090b");
        put(V4, "neutral", "#fafafa #f5f5f5 #e5e5e5 #d4d4d4 #a1a1a1 #737373 #525252 #404040 #262626 #171717 #0a0a0a");
        put(V4, "stone", "#fafaf9 #f5f5f4 #e7e5e4 #d6d3d1 #a6a09b #79716b #57534d #44403b #292524 #1c1917 #0c0a09");
        put(V4, "mauve", "#fafafa #f3f1f3 #e7e4e7 #d7d0d7 #a89ea9 #79697b #594c5b #463947 #2a212c #1d161e #0c090c");
        put(V4, "olive", "#fbfbf9 #f4f4f0 #e8e8e3 #d8d8d0 #abab9c #7c7c67 #5b5b4b #474739 #2b2b22 #1d1d16 #0c0c09");
        put(V4, "mist", "#f9fbfb #f1f3f3 #e3e7e8 #d0d6d8 #9ca8ab #67787c #4b585b #394447 #22292b #161b1d #090b0c");
        put(V4, "taupe", "#fbfaf9 #f3f1f1 #e8e4e3 #d8d2d0 #aba09c #7c6d67 #5b4f4b #473c39 #2b2422 #1d1816 #0c0a09");
        put(V4, "red", "#fef2f2 #ffe2e2 #ffc9c9 #ffa2a2 #ff6467 #fb2c36 #e7000b #c10007 #9f0712 #82181a #460809");
        put(V4, "orange", "#fff7ed #ffedd4 #ffd6a7 #ffb86a #ff8904 #ff6900 #f54900 #ca3500 #9f2d00 #7e2a0c #441306");
        put(V4, "amber", "#fffbeb #fef3c6 #fee685 #ffd230 #ffb900 #fe9a00 #e17100 #bb4d00 #973c00 #7b3306 #461901");
        put(V4, "yellow", "#fefce8 #fef9c2 #fff085 #ffdf20 #fdc700 #f0b100 #d08700 #a65f00 #894b00 #733e0a #432004");
        put(V4, "lime", "#f7fee7 #ecfcca #d8f999 #bbf451 #9ae600 #7ccf00 #5ea500 #497d00 #3c6300 #35530e #192e03");
        put(V4, "green", "#f0fdf4 #dcfce7 #b9f8cf #7bf1a8 #05df72 #00c950 #00a63e #008236 #016630 #0d542b #032e15");
        put(V4, "emerald", "#ecfdf5 #d0fae5 #a4f4cf #5ee9b5 #00d492 #00bc7d #009966 #007a55 #006045 #004f3b #002c22");
        put(V4, "teal", "#f0fdfa #cbfbf1 #96f7e4 #46ecd5 #00d5be #00bba7 #009689 #00786f #005f5a #0b4f4a #022f2e");
        put(V4, "cyan", "#ecfeff #cefafe #a2f4fd #53eafd #00d3f2 #00b8db #0092b8 #007595 #005f78 #104e64 #053345");
        put(V4, "sky", "#f0f9ff #dff2fe #b8e6fe #74d4ff #00bcff #00a6f4 #0084d1 #0069a8 #00598a #024a70 #052f4a");
        put(V4, "blue", "#eff6ff #dbeafe #bedbff #8ec5ff #51a2ff #2b7fff #155dfc #1447e6 #193cb8 #1c398e #162456");
        put(V4, "indigo", "#eef2ff #e0e7ff #c6d2ff #a3b3ff #7c86ff #615fff #4f39f6 #432dd7 #372aac #312c85 #1e1a4d");
        put(V4, "violet", "#f5f3ff #ede9fe #ddd6ff #c4b4ff #a684ff #8e51ff #7f22fe #7008e7 #5d0ec0 #4d179a #2f0d68");
        put(V4, "purple", "#faf5ff #f3e8ff #e9d4ff #dab2ff #c27aff #ad46ff #9810fa #8200db #6e11b0 #59168b #3c0366");
        put(V4, "fuchsia", "#fdf4ff #fae8ff #f6cfff #f4a8ff #ed6aff #e12afb #c800de #a800b7 #8a0194 #721378 #4b004f");
        put(V4, "pink", "#fdf2f8 #fce7f3 #fccee8 #fda5d5 #fb64b6 #f6339a #e60076 #c6005c #a3004c #861043 #510424");
        put(V4, "rose", "#fff1f2 #ffe4e6 #ffccd3 #ffa1ad #ff637e #ff2056 #ec003f #c70036 #a50036 #8b0836 #4d0218");
    }

    private static void put(Map<String, String[]> table, String name, String hexes) {
        table.put(name, hexes.split(" "));
    }

    // The start of every Tailwind colour utility. Variants (hover:, dark:, md:) sit before it after a ":",
    // which the lookbehind allows.
    private static final String PREFIX = "(?<![\\w-])(?:bg|text|border(?:-[xytrblse])?|divide|outline|ring(?:-offset)?"
            + "|shadow|decoration|accent|caret|fill|stroke|placeholder|from|via|to)-";

    // <prefix>-<colour>-<shade>, optional /<opacity>. Groups: 1 colour, 2 shade, 3 opacity (may be null).
    private static final Pattern TW = Pattern.compile(PREFIX + "("
            + String.join("|", V4.keySet()) + ")-(" // V4 has every V3 name plus four more
            + String.join("|", SHADES) + ")(?:/(\\d{1,3}))?(?![\\w-])");

    // <prefix>-white or <prefix>-black, optional /<opacity>. Groups: 1 white|black, 2 opacity (may be null).
    private static final Pattern TW_BW = Pattern.compile(PREFIX + "(white|black)(?:/(\\d{1,3}))?(?![\\w-])");

    // Colour functions, any case: rgb(), rgba(), hsl(), hsla(), oklch(). One search for all five.
    // Groups: 1 the name, 2 what's inside the brackets, checked by parseFunction().
    // [^()] skips anything with brackets inside, like rgb(var(--x)) or oklch(from var(--x) l c h).
    private static final Pattern FUNC = Pattern.compile("(?i)(?<![\\w-])(rgba?|hsla?|oklch)\\(([^()]*)\\)");

    // Tailwind size classes, only checked on the clicked line (4d). Groups: 1 the prefix ("px", "-mt", "rounded-t"),
    // 2 the size with its dash ("-4", "-lg"), which is the part a pick replaces. Empty for a bare "rounded"/"shadow".
    private static final String SPACE_VALUE = "(?:px|\\d+(?:\\.5)?)"; // 0, px, 0.5, 4, 96...
    private static final Pattern SPACING = Pattern.compile( // setting: spacing
            "(?<![\\w-])(-?(?:p[xytrblse]?|m[xytrblse]?|gap(?:-[xy])?|space-[xy]))(-" + SPACE_VALUE + ")(?![\\w./-])");
    private static final Pattern SIZING = Pattern.compile(  // setting: sizing
            "(?<![\\w-])(w|h|size|min-w|min-h|max-h)(-" + SPACE_VALUE + ")(?![\\w./-])");
    private static final Pattern TEXT_SIZE = Pattern.compile( // setting: look ("text-sm/6" keeps its /6)
            "(?<![\\w-])(text)(-(?:xs|sm|base|lg|xl|[2-9]xl))(?![\\w-])");
    private static final Pattern ROUNDED_SHADOW = Pattern.compile( // setting: look
            "(?<![\\w-])(rounded(?:-(?:tl|tr|bl|br|ss|se|es|ee|[tblrse]))?|shadow)"
            + "((?:-(?:none|2xs|xs|sm|md|lg|xl|[234]xl|full|inner))?)(?![\\w-])");

    // The rows: "label:tooltip". "—" is the bare class (plain "rounded", "shadow").
    private static final String[] SPACING_SCALE = ("0 px 0.5 1 1.5 2 2.5 3 3.5 4 5 6 7 8 9 10 11 12 14 16 20 24 28 32 36 "
            + "40 44 48 52 56 60 64 72 80 96").split(" "); // tooltip worked out in spacingTip()
    private static final String[] TEXT_SIZES = ("xs:12px sm:14px base:16px lg:18px xl:20px 2xl:24px 3xl:30px 4xl:36px "
            + "5xl:48px 6xl:60px 7xl:72px 8xl:96px 9xl:128px").split(" ");
    private static final String[] ROUNDED_V3 = "none:0 sm:2px —:4px md:6px lg:8px xl:12px 2xl:16px 3xl:24px full:round".split(" ");
    private static final String[] ROUNDED_V4 = "none:0 xs:2px sm:4px md:6px lg:8px xl:12px 2xl:16px 3xl:24px 4xl:32px full:round".split(" ");
    private static final String[] SHADOW_V3 = "none:none sm:small —:default md:medium lg:large xl:extra-large 2xl:huge inner:inside".split(" ");
    private static final String[] SHADOW_V4 = "none:none 2xs:tiny xs:extra-small sm:small md:medium lg:large xl:extra-large 2xl:huge".split(" ");

    // The size row open now, if any, so a second click (double-click) can close it.
    private static JBPopup sizePopup;

    private static final int MAX_FILE_CHARS = 2_000_000; // skip huge files (minified bundles, dumps)
    private static final int SCAN_DELAY_MS = 300;

    // Per-editor cleanup handle, so everything we add is removed when the editor closes.
    private static final Key<Disposable> DISPOSABLE = Key.create("hexswatch.disposable");

    // Per-project memo of which Tailwind table it uses, read once (restart the IDE after upgrading Tailwind).
    private static final Key<Map<String, String[]>> TABLE = Key.create("hexswatch.tailwindTable");

    // ---- 1. Editor opened / closed -------------------------------------------------------------

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor.getEditorKind() != EditorKind.MAIN_EDITOR || editor.getProject() == null) {
            return; // only code editors, not consoles, search boxes, commit messages...
        }

        Disposable disposable = Disposer.newDisposable("hexswatch");
        editor.putUserData(DISPOSABLE, disposable);
        Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable);

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent e) {
                scheduleScan(editor, alarm);
            }
        }, disposable);

        editor.addEditorMouseListener(new EditorMouseListener() {
            @Override
            public void mousePressed(@NotNull EditorMouseEvent e) {
                closeSizeRow(); // any press closes the size row but still reaches the editor (double-click selects)
            }

            @Override
            public void mouseClicked(@NotNull EditorMouseEvent e) {
                onClick(editor, e);
            }
        }, disposable);

        scheduleScan(editor, alarm);
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Disposable disposable = event.getEditor().getUserData(DISPOSABLE);
        if (disposable != null) {
            Disposer.dispose(disposable); // stops the timer and both listeners
        }
    }

    // ---- 2. Wait for a pause in typing ---------------------------------------------------------

    private static void scheduleScan(Editor editor, Alarm alarm) {
        alarm.cancelAllRequests();
        alarm.addRequest(() -> {
            if (!editor.isDisposed()) scan(editor);
        }, SCAN_DELAY_MS);
    }

    // ---- 3. Find colours and sync the marks ----------------------------------------------------

    private static void scan(Editor editor) {
        CharSequence text = editor.getDocument().getImmutableCharSequence();

        // What should be there: offset where the mark goes -> mark.
        Map<Integer, Swatch> wanted = new HashMap<>();
        if (text.length() <= MAX_FILE_CHARS) {
            Matcher hex = HEX.matcher(text);
            while (hex.find()) {
                wanted.put(hex.start(), new Swatch(parseHex(hex.group(1)), false));
            }
            Matcher fn = FUNC.matcher(text);
            while (fn.find()) {
                Color c = parseFunction(fn.group(1), fn.group(2));
                if (c != null) wanted.put(fn.start(), new Swatch(c, false));
            }
            Matcher bw = TW_BW.matcher(text);
            while (bw.find()) {
                wanted.put(bw.start(), new Swatch(withOpacity(blackOrWhite(bw.group(1)), bw.group(2)), true));
            }
            Matcher tw = TW.matcher(text);
            while (tw.find()) {
                Color c = tailwindColor(editor.getProject(), tw.group(1), tw.group(2), tw.group(3));
                wanted.put(tw.start(), new Swatch(c, true));
            }
        }

        // Keep marks that are still right, remove the rest.
        List<Inlay<? extends Swatch>> existing =
                editor.getInlayModel().getInlineElementsInRange(0, text.length(), Swatch.class);
        for (Inlay<? extends Swatch> inlay : existing) {
            Swatch want = wanted.get(inlay.getOffset());
            if (want != null && want.sameAs(inlay.getRenderer())) {
                wanted.remove(inlay.getOffset()); // already correct
            } else {
                Disposer.dispose(inlay);
            }
        }

        // Add the new ones.
        for (Map.Entry<Integer, Swatch> e : wanted.entrySet()) {
            editor.getInlayModel().addInlineElement(e.getKey(), false, e.getValue());
        }
    }

    // "a10000" -> Color. 3/4 digits are shorthand (#f00 = #ff0000); the 4th/8th digits are alpha, as in CSS.
    private static Color parseHex(String hex) {
        if (hex.length() <= 4) {
            StringBuilder full = new StringBuilder();
            for (char c : hex.toCharArray()) full.append(c).append(c);
            hex = full.toString();
        }
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        int a = hex.length() == 8 ? Integer.parseInt(hex.substring(6, 8), 16) : 255;
        return new Color(r, g, b, a);
    }

    // The inside of a colour function -> Color. Returns null if it isn't 3 colour numbers plus an optional alpha
    // (then no circle is drawn). Separators: commas, spaces, "/" before the alpha, Tailwind's "_".
    //   rgb:   "255, 0, 0", "255 0 0 / 50%", "100% 0% 0%", "255,0,0,.5", "255_0_0"
    //   hsl:   "0, 100%, 50%", "0 100% 50% / .5", "120deg 50 50"  (hue in deg/turn/rad/grad or a bare number)
    //   oklch: "63.7% 0.237 25.331", "0.637 0.237 25.331 / 50%"
    private static Color parseFunction(String name, String inner) {
        String[] p = parts(inner);
        if (p.length != 3 && p.length != 4) return null;
        try {
            int a = p.length == 4 ? parseAlpha(p[3]) : 255;
            String n = name.toLowerCase(Locale.ROOT);
            Color c = n.startsWith("rgb") ? parseRgb(p) : n.startsWith("hsl") ? parseHsl(p) : parseOklch(p);
            if (c == null || a < 0) return null;
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
        } catch (NumberFormatException e) {
            return null; // "rgb(r, g, b)" in a comment, "rgba($color, 0.5)" in Sass, "none" in oklch...
        }
    }

    private static String[] parts(String inner) {
        return inner.trim().split("[\\s,/_]+");
    }

    // "50%" -> 50, "50" -> 50.
    private static float number(String p) {
        return Float.parseFloat(p.endsWith("%") ? p.substring(0, p.length() - 1) : p);
    }

    // "0.5" or "50%" -> 0-255, or -1 if out of range.
    private static int parseAlpha(String p) {
        float v = p.endsWith("%") ? number(p) / 100 : number(p);
        return v < 0 || v > 1 ? -1 : Math.round(v * 255);
    }

    // rgb: each channel 0-255, or 0%-100%.
    private static Color parseRgb(String[] p) {
        int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
            float v = p[i].endsWith("%") ? number(p[i]) * 2.55f : number(p[i]);
            if (v < 0 || v > 255.5f) return null;
            rgb[i] = Math.round(v);
        }
        return new Color(rgb[0], rgb[1], rgb[2]);
    }

    // hsl: hue, then saturation and lightness 0-100 (with or without %). CSS's own formula.
    private static Color parseHsl(String[] p) {
        float h = parseHue(p[0]);
        float s = number(p[1]) / 100;
        float l = number(p[2]) / 100;
        if (s < 0 || s > 1 || l < 0 || l > 1) return null;
        float a = s * Math.min(l, 1 - l);
        float[] rgb = new float[3];
        int[] offsets = {0, 8, 4}; // red, green, blue
        for (int i = 0; i < 3; i++) {
            float k = (offsets[i] + h / 30) % 12;
            rgb[i] = l - a * Math.max(-1, Math.min(Math.min(k - 3, 9 - k), 1));
        }
        return rgbColor(rgb[0], rgb[1], rgb[2]);
    }

    // oklch: lightness 0-1 (or 0%-100%), chroma 0-0.4ish (100% = 0.4), hue.
    // OKLCH -> OKLab -> linear sRGB -> sRGB, Björn Ottosson's matrices (the ones CSS uses).
    // Colours outside what a screen can show are clipped to the nearest edge (browsers adjust them a bit more gently).
    private static Color parseOklch(String[] p) {
        double l = p[0].endsWith("%") ? number(p[0]) / 100 : number(p[0]);
        double c = p[1].endsWith("%") ? number(p[1]) * 0.004 : number(p[1]);
        double h = Math.toRadians(parseHue(p[2]));
        if (l < 0 || l > 1 || c < 0) return null;
        double a = c * Math.cos(h), b = c * Math.sin(h);

        double lc = Math.pow(l + 0.3963377774 * a + 0.2158037573 * b, 3);
        double mc = Math.pow(l - 0.1055613458 * a - 0.0638541728 * b, 3);
        double sc = Math.pow(l - 0.0894841775 * a - 1.2914855480 * b, 3);
        return rgbColor(
                toGamma(4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc),
                toGamma(-1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc),
                toGamma(-0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc));
    }

    // "200", "200deg", "0.5turn", "3.14rad", "200grad" -> degrees, 0-360.
    private static float parseHue(String p) {
        String h = p.toLowerCase(Locale.ROOT);
        float deg = h.endsWith("deg") ? Float.parseFloat(h.substring(0, h.length() - 3))
                : h.endsWith("grad") ? Float.parseFloat(h.substring(0, h.length() - 4)) * 0.9f
                : h.endsWith("turn") ? Float.parseFloat(h.substring(0, h.length() - 4)) * 360
                : h.endsWith("rad") ? (float) Math.toDegrees(Float.parseFloat(h.substring(0, h.length() - 3)))
                : Float.parseFloat(h);
        return ((deg % 360) + 360) % 360;
    }

    // Light-linear value -> screen value (sRGB curve), and back.
    private static float toGamma(double x) {
        return (float) (x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - 0.055);
    }

    private static double toLinear(int channel) {
        double x = channel / 255.0;
        return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }

    // 0-1 floats -> Color, clipped to 0-1 first (rounding can land a hair outside).
    private static Color rgbColor(float r, float g, float b) {
        return new Color(Math.max(0, Math.min(1, r)), Math.max(0, Math.min(1, g)), Math.max(0, Math.min(1, b)));
    }

    // ("red", "600", "50") -> #dc2626 at 50% opacity. Opacity null = solid.
    private static Color tailwindColor(Project project, String name, String shade, String opacity) {
        return withOpacity(parseHex(shadesFor(project, name)[shadeIndex(shade)].substring(1)), opacity);
    }

    // Tailwind's /50 suffix -> alpha. Null = solid.
    private static Color withOpacity(Color c, String opacity) {
        if (opacity == null) return c;
        int a = Math.round(Math.min(100, Integer.parseInt(opacity)) * 2.55f);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static Color blackOrWhite(String word) {
        return word.equals("white") ? Color.WHITE : Color.BLACK; // same in Tailwind 3 and 4
    }

    // The 11 hex values for a colour name in this project's Tailwind. Names only in V4 (mauve...) always use V4.
    private static String[] shadesFor(Project project, String name) {
        String[] hexes = tableFor(project).get(name);
        return hexes != null ? hexes : V4.get(name);
    }

    // V3 if the project has Tailwind 3 installed (node_modules) or listed (package.json); otherwise V4, the current one.
    private static Map<String, String[]> tableFor(Project project) {
        Map<String, String[]> table = project.getUserData(TABLE);
        if (table == null) {
            table = V4;
            String base = project.getBasePath();
            if (base != null) {
                String installed = readFile(Paths.get(base, "node_modules", "tailwindcss", "package.json"));
                String listed = readFile(Paths.get(base, "package.json"));
                Matcher v = Pattern.compile("\"version\"\\s*:\\s*\"(\\d+)").matcher(installed);
                Matcher l = Pattern.compile("\"tailwindcss\"\\s*:\\s*\"\\D*(\\d+)").matcher(listed);
                String major = v.find() ? v.group(1) : l.find() ? l.group(1) : "4";
                if (major.equals("3")) table = V3;
            }
            project.putUserData(TABLE, table);
        }
        return table;
    }

    private static String readFile(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static int shadeIndex(String shade) {
        for (int i = 0; i < SHADES.length; i++) if (SHADES[i].equals(shade)) return i;
        return 0;
    }

    // ---- 4. Click a mark -----------------------------------------------------------------------

    private static void onClick(Editor editor, EditorMouseEvent e) {
        Inlay<?> inlay = e.getInlay();
        if (inlay == null) {
            onTextClick(editor, e); // maybe a Tailwind size class (4d)
            return;
        }
        if (!(inlay.getRenderer() instanceof Swatch swatch)) return;
        e.consume(); // don't also move the caret

        int start = inlay.getOffset();
        MouseEvent click = e.getMouseEvent();
        CharSequence text = editor.getDocument().getImmutableCharSequence();

        if (swatch.tailwind) {
            if (!showShades(editor, start, click)) showBlackWhite(editor, start, click);
            return;
        }

        // A circle: hex or a colour function. Work out how the colour is written now, so the new one matches.
        Matcher hex = HEX.matcher(text);
        if (hex.find(start) && hex.start() == start) {
            boolean upper = !hex.group(1).equals(hex.group(1).toLowerCase()); // #A10000 stays capitals
            showPicker(editor, start, swatch.color, click, HEX, color -> formatHex(color, upper));
            return;
        }
        Matcher fn = FUNC.matcher(text);
        if (fn.find(start) && fn.start() == start) {
            String name = fn.group(1);
            String inner = fn.group(2);
            showPicker(editor, start, swatch.color, click, FUNC, color -> formatFunction(color, name, inner));
        }
    }

    // ---- 4a. Hex / rgb() / hsl() / oklch(): PhpStorm's picker, rewrites the colour as you drag ---

    private static void showPicker(Editor editor, int start, Color current, MouseEvent click, Pattern pattern,
                                   Function<Color, String> format) {
        ColorChooserService.getInstance().showPopup(
                editor.getProject(),
                current,
                (color, source) -> replaceGroup(editor, start, pattern, 0, format.apply(color), "Change Colour"),
                new RelativePoint(click),
                true,   // show the alpha (opacity) slider
                false); // alpha as 0-255, not percent
    }

    // Color -> "#a10000", or "#a1000080" when see-through.
    private static String formatHex(Color c, boolean upper) {
        String hex = c.getAlpha() == 255
                ? String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue())
                : String.format("#%02x%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        return upper ? hex.toUpperCase() : hex;
    }

    // Color -> rgb()/hsl()/oklch() in the same style as the original `inner` text:
    //   "255, 0, 0" -> "rgb(161, 0, 0)" / "rgba(161, 0, 0, 0.5)"   (commas; "rgb" becomes "rgba" when alpha is added)
    //   "255 0 0"   -> "rgb(161 0 0)"   / "rgb(161 0 0 / 0.5)"     (spaces; "_" kept for Tailwind's rgb(255_0_0))
    // An alpha written as a percentage stays a percentage. The three numbers come from rgbText/hslText/oklchText.
    private static String formatFunction(Color c, String name, String inner) {
        String[] parts = parts(inner);
        String n = name.toLowerCase(Locale.ROOT);
        String[] channels = n.startsWith("rgb") ? rgbText(c) : n.startsWith("hsl") ? hslText(c, parts) : oklchText(c, parts);
        boolean hadAlpha = parts.length == 4;
        boolean alphaPct = hadAlpha && parts[3].endsWith("%");
        boolean writeAlpha = hadAlpha || c.getAlpha() < 255;
        String alpha = alphaPct
                ? Math.round(c.getAlpha() / 2.55f) + "%"
                : trimZeros(String.format(Locale.ROOT, "%.2f", c.getAlpha() / 255f));

        boolean comma = inner.contains(",");
        String sep = comma ? (inner.contains(", ") ? ", " : ",") : (inner.contains("_") ? "_" : " ");
        String body = String.join(sep, channels);

        String outName = name;
        if (comma) {
            if (writeAlpha) {
                body += sep + alpha;
                if (n.equals("rgb") || n.equals("hsl")) {
                    outName = name + (name.equals(name.toUpperCase()) ? "A" : "a"); // rgb -> rgba, hsl -> hsla
                }
            }
        } else if (writeAlpha) {
            body += sep + "/" + sep + alpha;
        }
        return outName + "(" + body + ")";
    }

    // rgb: always 0-255.
    private static String[] rgbText(Color c) {
        return new String[]{"" + c.getRed(), "" + c.getGreen(), "" + c.getBlue()};
    }

    // hsl: whole numbers. "deg" and "%" are kept if the original had them.
    private static String[] hslText(Color c, String[] old) {
        float r = c.getRed() / 255f, g = c.getGreen() / 255f, b = c.getBlue() / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float l = (max + min) / 2;
        float s = d == 0 ? 0 : d / (1 - Math.abs(2 * l - 1));
        float h = d == 0 ? 0 : max == r ? 60 * (((g - b) / d) % 6) : max == g ? 60 * ((b - r) / d + 2) : 60 * ((r - g) / d + 4);
        if (h < 0) h += 360;
        return new String[]{
                Math.round(h) % 360 + (old[0].toLowerCase(Locale.ROOT).endsWith("deg") ? "deg" : ""),
                Math.round(s * 100) + (old[1].endsWith("%") ? "%" : ""),
                Math.round(l * 100) + (old[2].endsWith("%") ? "%" : "")};
    }

    // oklch: like Tailwind writes it ("63.7% 0.237 25.3"). Lightness/chroma as % if the original used %.
    // A hue written in turn/rad/grad comes back in degrees. Grey has no hue, so it is written as 0.
    private static String[] oklchText(Color c, String[] old) {
        double r = toLinear(c.getRed()), g = toLinear(c.getGreen()), b = toLinear(c.getBlue());
        double lc = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double mc = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double sc = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        double l = 0.2104542553 * lc + 0.7936177850 * mc - 0.0040720468 * sc;
        double oa = 1.9779984951 * lc - 2.4285922050 * mc + 0.4505937099 * sc;
        double ob = 0.0259040371 * lc + 0.7827717662 * mc - 0.8086757660 * sc;
        double chroma = Math.sqrt(oa * oa + ob * ob);
        double hue = (Math.toDegrees(Math.atan2(ob, oa)) + 360) % 360;

        String deg = old[2].toLowerCase(Locale.ROOT).endsWith("deg") ? "deg" : "";
        return new String[]{
                old[0].endsWith("%") ? decimals(l * 100, 1) + "%" : decimals(l, 3),
                old[1].endsWith("%") ? decimals(chroma / 0.004, 1) + "%" : decimals(chroma, 3),
                chroma < 0.0005 ? "0" : decimals(hue, 1) + deg};
    }

    private static String decimals(double v, int places) { // (0.6370, 3) -> "0.637"
        return trimZeros(String.format(Locale.ROOT, "%." + places + "f", Math.max(0, v)));
    }

    private static String trimZeros(String number) { // "0.50" -> "0.5", "1.00" -> "1"
        return number.contains(".") ? number.replaceAll("0+$", "").replaceAll("\\.$", "") : number;
    }

    // ---- 4b. Tailwind: a row of the 11 shades; click one to swap the shade number ---------------

    // Returns false if the mark isn't a <colour>-<shade> class (then it's white/black, see 4c).
    private static boolean showShades(Editor editor, int start, MouseEvent click) {
        Matcher m = TW.matcher(editor.getDocument().getImmutableCharSequence());
        if (!m.find(start) || m.start() != start) return false;
        String name = m.group(1);
        String[] hexes = shadesFor(editor.getProject(), name);

        Color[] colors = new Color[SHADES.length];
        String[] tips = new String[SHADES.length];
        for (int i = 0; i < SHADES.length; i++) {
            colors[i] = parseHex(hexes[i].substring(1));
            tips[i] = name + "-" + SHADES[i] + "  " + hexes[i];
        }
        // "hover:bg-red-600/50" -> "hover:bg-red-500/50": only the shade number (group 2) changes.
        showRow(editor, start, click, SHADES, colors, tips, m.group(2), true,
                shade -> replaceGroup(editor, start, TW, 2, shade, "Change Tailwind Shade"));
        return true;
    }

    // ---- 4c. Tailwind white/black: two choices; click one to swap the word ----------------------

    private static void showBlackWhite(Editor editor, int start, MouseEvent click) {
        Matcher m = TW_BW.matcher(editor.getDocument().getImmutableCharSequence());
        if (!m.find(start) || m.start() != start) return;

        String[] words = {"white", "black"};
        // "text-white/80" -> "text-black/80": only the word (group 1) changes.
        showRow(editor, start, click, words, new Color[]{Color.WHITE, Color.BLACK},
                new String[]{"white  #ffffff", "black  #000000"},
                m.group(1), true, word -> replaceGroup(editor, start, TW_BW, 1, word, "Change Tailwind Colour"));
    }

    // ---- 4d. Tailwind sizes: a plain click on p-4, w-8, text-sm... shows a row of sizes ----------

    // Only when a size setting is on, and only for a plain single left click on text (not drag, Shift, double-click).
    // Looks at the clicked line only, so the file size doesn't matter.
    private static void onTextClick(Editor editor, EditorMouseEvent e) {
        closeSizeRow();
        MouseEvent click = e.getMouseEvent();
        if (click.getClickCount() != 1 || click.getButton() != MouseEvent.BUTTON1 || click.isShiftDown()
                || click.isControlDown() || click.isAltDown() || click.isMetaDown() || !e.isOverText()) return;
        Settings s = Settings.get();
        if (!s.spacing && !s.sizing && !s.look) return;

        Document doc = editor.getDocument();
        int offset = e.getOffset();
        int line = doc.getLineNumber(offset);
        int lineStart = doc.getLineStartOffset(line);
        CharSequence lineText = doc.getImmutableCharSequence().subSequence(lineStart, doc.getLineEndOffset(line));

        Pattern[] patterns = {SPACING, SIZING, TEXT_SIZE, ROUNDED_SHADOW};
        boolean[] on = {s.spacing, s.sizing, s.look, s.look};
        for (int i = 0; i < patterns.length; i++) {
            if (!on[i]) continue;
            Matcher m = patterns[i].matcher(lineText);
            while (m.find()) {
                if (m.start() <= offset - lineStart && offset - lineStart <= m.end()) {
                    showSizes(editor, lineStart + m.start(), click, patterns[i], m.group(1), m.group(2));
                    return;
                }
            }
        }
    }

    // The row for one class. Picking a size rewrites only group 2: "hover:-mt-2" -> "hover:-mt-4".
    private static void showSizes(Editor editor, int start, MouseEvent click, Pattern pattern, String prefix, String size) {
        boolean v3 = tableFor(editor.getProject()) == V3;
        String[] items = prefix.equals("text") ? TEXT_SIZES
                : prefix.startsWith("rounded") ? (v3 ? ROUNDED_V3 : ROUNDED_V4)
                : prefix.equals("shadow") ? (v3 ? SHADOW_V3 : SHADOW_V4)
                : SPACING_SCALE;

        String[] labels = new String[items.length];
        String[] tips = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            String[] parts = items[i].split(":");
            labels[i] = parts[0];
            String cls = prefix + (labels[i].equals("—") ? "" : "-" + labels[i]);
            tips[i] = cls + "  " + (parts.length > 1 ? parts[1] : spacingTip(labels[i]));
        }
        String current = size.isEmpty() ? "—" : size.substring(1);
        sizePopup = showRow(editor, start, click, labels, null, tips, current, false,
                label -> replaceGroup(editor, start, pattern, 2, label.equals("—") ? "" : "-" + label, "Change Tailwind Size"));
    }

    private static void closeSizeRow() {
        if (sizePopup != null) {
            sizePopup.cancel();
            sizePopup = null;
        }
    }

    // "4" -> "16px", "0.5" -> "2px", "px" -> "1px". Tailwind's spacing unit is 0.25rem = 4px.
    private static String spacingTip(String value) {
        return value.equals("px") ? "1px" : trimZeros(String.format(Locale.ROOT, "%.1f", Float.parseFloat(value) * 4)) + "px";
    }

    // ---- Shared by 4b, 4c and 4d ---------------------------------------------------------------

    // A popup row of boxes with labels; the current one is outlined; a click calls onPick with its label.
    // colors null = text-only boxes (sizes). Long rows wrap at 18. takeFocus false = the editor keeps the keyboard
    // and any key press closes the row, so the click that opened it still works as a normal click. It also doesn't
    // close itself on an outside click (that would swallow the click and break double-click word selection):
    // the editor's mousePressed closes it instead, and leaving the editor (focus lost) closes it too.
    private static JBPopup showRow(Editor editor, int start, MouseEvent click, String[] labels, Color[] colors,
                                   String[] tips, String current, boolean takeFocus, Consumer<String> onPick) {
        int columns = Math.min(labels.length, 18);
        int rows = (labels.length + columns - 1) / columns;
        JPanel row = new JPanel(new GridLayout(rows, columns, JBUI.scale(2), JBUI.scale(2)));
        row.setBorder(JBUI.Borders.empty(6));
        JBPopup[] popup = new JBPopup[1]; // filled in below; the cells need it to close the popup

        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            ShadeCell cell = new ShadeCell(colors == null ? null : colors[i], label, label.equals(current));
            cell.setToolTipText(tips[i]);
            cell.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent ev) {
                    onPick.accept(label);
                    popup[0].cancel();
                }
            });
            row.add(cell);
        }

        popup[0] = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(row, null)
                .setRequestFocus(takeFocus)
                .setFocusable(takeFocus) // size row: clicking a size mustn't pull the keyboard out of the editor
                .setCancelOnClickOutside(takeFocus)
                .createPopup();
        if (!takeFocus) {
            JComponent content = editor.getContentComponent();
            KeyAdapter closeOnKey = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent ev) {
                    popup[0].cancel();
                }
            };
            FocusAdapter closeOnLeave = new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent ev) {
                    Component to = ev.getOppositeComponent();
                    if (to != null && SwingUtilities.isDescendingFrom(to, row)) return; // clicked a size in the row
                    popup[0].cancel();
                }
            };
            content.addKeyListener(closeOnKey);
            content.addFocusListener(closeOnLeave);
            Disposer.register(popup[0], () -> { // removed when the row closes
                content.removeKeyListener(closeOnKey);
                content.removeFocusListener(closeOnLeave);
            });
        }
        popup[0].show(rowLocation(editor, start, click, popup[0].getContent().getPreferredSize().height));
        return popup[0];
    }

    // Where the row goes: just under the clicked line if it fits on screen, otherwise just above it,
    // so it never covers the colour being changed. x follows the mouse.
    private static RelativePoint rowLocation(Editor editor, int start, MouseEvent click, int popupHeight) {
        JComponent content = editor.getContentComponent();
        int x = SwingUtilities.convertPoint(click.getComponent(), click.getPoint(), content).x;
        int lineTop = editor.offsetToXY(start).y;
        int lineBottom = lineTop + editor.getLineHeight();

        RelativePoint below = new RelativePoint(content, new Point(x, lineBottom));
        Rectangle screen = ScreenUtil.getScreenRectangle(below.getScreenPoint());
        boolean fitsBelow = below.getScreenPoint().y + popupHeight <= screen.y + screen.height;
        return fitsBelow ? below : new RelativePoint(content, new Point(x, lineTop - popupHeight));
    }

    // Re-find the colour at `start` and replace one regex group in it (0 = all of it).
    // The group id makes every change from one picker drag undo as a single step.
    private static void replaceGroup(Editor editor, int start, Pattern pattern, int group, String text, String undoName) {
        Document doc = editor.getDocument();
        Matcher m = pattern.matcher(doc.getImmutableCharSequence());
        if (!m.find(start) || m.start() != start) return; // text moved or was edited meanwhile

        WriteCommandAction.writeCommandAction(editor.getProject())
                .withName(undoName)
                .withGroupId("hexswatch-" + start)
                .run(() -> doc.replaceString(m.start(group), m.end(group), text));
    }

    // One choice in the popup: a colour box with its label ("600", "white") underneath; the current one is outlined.
    // color null = a size: just the label ("4", "lg") in a box.
    static class ShadeCell extends JComponent {
        final Color color;
        final String shade;
        final boolean current;

        ShadeCell(Color color, String shade, boolean current) {
            this.color = color;
            this.shade = shade;
            this.current = current;
            int labelWidth = getFontMetrics(JBUI.Fonts.miniFont()).stringWidth(shade) + JBUI.scale(6);
            setPreferredSize(new Dimension(Math.max(JBUI.scale(30), labelWidth), JBUI.scale(color == null ? 24 : 42)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (color == null) { // size: label in an outlined box
                int w = getWidth() - JBUI.scale(4), h = getHeight() - JBUI.scale(4), x = JBUI.scale(2);
                g.setColor(current ? JBColor.foreground() : JBColor.GRAY);
                g.setStroke(new BasicStroke(current ? JBUIScale.scale(2f) : 1f));
                g.drawRoundRect(x, x, w, h, JBUI.scale(4), JBUI.scale(4));
                g.setColor(JBColor.foreground());
                g.setFont(JBUI.Fonts.miniFont());
                FontMetrics fm = g.getFontMetrics();
                g.drawString(shade, (getWidth() - fm.stringWidth(shade)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                return;
            }
            int box = getWidth() - JBUI.scale(4);
            int x = JBUI.scale(2);
            g.setColor(color);
            g.fillRoundRect(x, x, box, box, JBUI.scale(4), JBUI.scale(4));
            g.setColor(current ? JBColor.foreground() : JBColor.GRAY);
            g.setStroke(new BasicStroke(current ? JBUIScale.scale(2f) : 1f));
            g.drawRoundRect(x, x, box, box, JBUI.scale(4), JBUI.scale(4));

            g.setColor(JBColor.foreground());
            g.setFont(JBUI.Fonts.miniFont());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(shade, (getWidth() - fm.stringWidth(shade)) / 2, getHeight() - fm.getDescent());
        }
    }

    // ---- The mark in the editor: circle for hex, diamond for Tailwind --------------------------

    static class Swatch implements EditorCustomElementRenderer {
        final Color color;
        final boolean tailwind;

        Swatch(Color color, boolean tailwind) {
            this.color = color;
            this.tailwind = tailwind;
        }

        boolean sameAs(Swatch other) {
            return tailwind == other.tailwind && color.equals(other.color);
        }

        @Override
        public int calcWidthInPixels(@NotNull Inlay inlay) {
            return size(inlay) + 4; // mark plus a little gap before the text
        }

        @Override
        public void paint(@NotNull Inlay inlay, @NotNull Graphics2D g, @NotNull Rectangle2D area,
                          @NotNull TextAttributes attrs) {
            int s = size(inlay);
            double x = area.getX() + 1;
            double y = area.getY() + (area.getHeight() - s) / 2;
            Shape shape = tailwind ? diamond(x, y, s) : new Ellipse2D.Double(x, y, s, s);

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (color.getAlpha() < 255) {          // see-through colour: white/grey halves show the opacity
                Shape oldClip = g.getClip();
                g.clip(shape);
                g.setColor(Color.WHITE);
                g.fill(shape);
                g.setColor(Color.LIGHT_GRAY);
                g.fill(new Rectangle2D.Double(x, y, s / 2.0, s));
                g.setClip(oldClip);
            }
            g.setColor(color);
            g.fill(shape);
            g.setColor(Color.GRAY);
            g.draw(shape);
        }

        private static Shape diamond(double x, double y, double s) {
            Path2D p = new Path2D.Double();
            p.moveTo(x + s / 2, y);
            p.lineTo(x + s, y + s / 2);
            p.lineTo(x + s / 2, y + s);
            p.lineTo(x, y + s / 2);
            p.closePath();
            return p;
        }

        private static int size(Inlay inlay) {
            return Math.max(8, (int) (inlay.getEditor().getLineHeight() * 0.65));
        }
    }

    // ---- Settings: three checkboxes for the size rows, all off by default -----------------------
    // Saved by the IDE in hexswatch.xml (options folder). Registered in plugin.xml as an applicationService
    // (Settings) and an applicationConfigurable under Tools (SettingsPage).

    @State(name = "HexSwatch", storages = @Storage("hexswatch.xml"))
    public static class Settings implements PersistentStateComponent<Settings> {
        public boolean spacing = false; // p, m, gap, space
        public boolean sizing = false;  // w, h, size
        public boolean look = false;    // text size, rounded, shadow

        static Settings get() {
            return ApplicationManager.getApplication().getService(Settings.class);
        }

        @Override
        public Settings getState() {
            return this;
        }

        @Override
        public void loadState(@NotNull Settings saved) {
            XmlSerializerUtil.copyBean(saved, this);
        }
    }

    public static class SettingsPage implements Configurable {
        private JCheckBox spacing, sizing, look;

        @Override
        public String getDisplayName() {
            return "Hex Swatch";
        }

        @Override
        public JComponent createComponent() {
            spacing = new JCheckBox("Spacing: p, px, py, pt, pr, pb, pl, m, mx, my, mt, mr, mb, ml (and -m), gap, space-x/y");
            sizing = new JCheckBox("Width and height: w, h, size, min-w, min-h, max-h");
            look = new JCheckBox("Text size, rounded corners and shadows: text-sm, rounded-lg, shadow-md");
            JPanel boxes = new JPanel();
            boxes.setLayout(new BoxLayout(boxes, BoxLayout.Y_AXIS));
            boxes.add(new JLabel("Tailwind size rows: click a class (like p-4) to get a row of its sizes. Any key closes the row."));
            boxes.add(Box.createVerticalStrut(JBUI.scale(8)));
            boxes.add(spacing);
            boxes.add(sizing);
            boxes.add(look);
            JPanel page = new JPanel(new BorderLayout());
            page.add(boxes, BorderLayout.NORTH); // keep the checkboxes at the top
            return page;
        }

        @Override
        public boolean isModified() {
            Settings s = Settings.get();
            return spacing.isSelected() != s.spacing || sizing.isSelected() != s.sizing || look.isSelected() != s.look;
        }

        @Override
        public void apply() {
            Settings s = Settings.get();
            s.spacing = spacing.isSelected();
            s.sizing = sizing.isSelected();
            s.look = look.isSelected();
        }

        @Override
        public void reset() {
            Settings s = Settings.get();
            spacing.setSelected(s.spacing);
            sizing.setSelected(s.sizing);
            look.setSelected(s.look);
        }
    }
}
