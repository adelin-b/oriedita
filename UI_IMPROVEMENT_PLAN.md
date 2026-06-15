# Oriedita UI Improvement Plan

A phased process to turn the current dense, unlabeled icon-grid interface into a
comprehensible, sectioned, attractive editor — without sacrificing the power-user
density that serious crease-pattern work needs.

> Status: planning document. Nothing here is implemented yet. Phase 0 is the gate
> for everything after it.

> **Decisions locked (2026-06-15):**
> 1. **Strategy = retrofit the existing `tab/` redesign** (`DrawingTab`/`FoldingTab`/
>    `ReferencesTab`/`SettingsTab`). Finish and adopt that path rather than rewriting;
>    lowest risk, reuses in-progress work, ships as incremental per-panel PRs. Stay on
>    Swing + FlatLaf.
> 2. **Default view = power-density + progressive disclosure.** Keep most tools
>    reachable; demote only rarely used ones behind "More…" / search. Serves serious
>    crease-pattern work and honors Tesler's Law. Ship an optional "Classic layout"
>    toggle so veterans lose nothing.

> **Phase 0 executed (2026-06-15) — premise corrections.** A code audit revised three
> assumptions in §1/§2 below. See the **Phase 0 Findings** appendix (§10) for the full
> result and the first concrete fix already applied:
> - Tooltips are **already centrally wired** — `ButtonServiceImpl.setTooltip()` builds
>   `<i>Name</i> + tooltip + Hotkey` for every registered button. The earlier "only 7
>   tooltips" reading counted manual call sites, not the central one. So Phase 1 is
>   **not** "add tooltips"; it is "fill the empty `tooltip.properties` + fix the visible
>   labels."
> - The cryptic codes (`ckO`, `fxO`, `cAMV`, `CP_rcg`, `S_face`, `ad_fnc`, `a_s`,
>   `AS100`, `FC`/`BC`/`LC`) are **already decoded** in `oriedita/name.properties`
>   ("Check Coincident Line Errors", "Fix Coincident Lines", "Recognize Crease
>   Pattern"…). They render cryptically because (a) those buttons have no icon glyph and
>   fall back to hardcoded `.form` text, and (b) a **stale duplicate**
>   `oriedita-ui/name.properties` was shadowing the good names with empty/wrong values.
> - **Root-cause bug fixed:** the duplicate `name.properties` is reconciled (both copies
>   now identical, 229 keys, 0 empty, swapped Increase/Decrease labels corrected,
>   `as100Action` added).

---

## 1. Diagnosis — what's actually wrong

The current editor (see the standard launch screenshot) presents the user with
roughly **214 distinct actions** (count of `name.properties` keys) rendered as
**bare icon buttons packed into undifferentiated grids**, plus columns of cryptic
text codes: `ckO`, `ckT`, `cAMV`, `fxO`, `fxT`, `CP_rcg`, `S_face`, `ad_fnc`,
`a_s`, `AS100`, `FC` / `BC` / `LC`, `C_col`, `L1=…A3=`. There are **no section
boundaries**, almost **no hover help** (only 7 `setToolTipText` calls in the entire
Swing module, 6 of them inside one dialog), and identical-looking glyphs sit
edge-to-edge.

Mapped to the **Laws of UX** (lawsofux.com) and Nielsen's heuristics, the concrete
failures are:

| Problem | Law / heuristic violated | Effect on user |
|---|---|---|
| ~214 actions visible at once, no progressive disclosure | **Hick's Law** | Decision paralysis; can't find the tool |
| Cryptic codes (`ckO`, `AS100`, `CP_rcg`…) | **Recognition over recall**; Match between system & real world | User must memorize an undocumented vocabulary |
| Tiny, edge-to-edge, identically sized icons | **Fitts's Law** | Slow targeting, frequent mis-clicks |
| One flat grid, no grouping | **Law of Proximity / Common Region**; **Miller's Law (7±2)** | No mental model of "where things live" |
| Almost no tooltips, help buried in a separate dialog | Nielsen: **Help & documentation**; **Recognition** | No in-context learning path |
| Layout unlike any mainstream editor | **Jakob's Law** | Existing Illustrator/Figma/Inkscape skills don't transfer |
| Inconsistent / ambiguous iconography | **Law of Prägnanz**; Consistency & standards | Icons don't communicate meaning |
| Dense but visually flat / unpolished | **Aesthetic–Usability Effect** | Perceived as harder to use than it is |

The non-negotiable counterweight is **Tesler's Law (conservation of complexity)**:
crease-pattern editing is genuinely complex. The goal is **not** to delete features
or dumb the tool down — it is to *relocate* complexity into sensible defaults,
grouping, and progressive disclosure, while keeping every existing capability one
click or shortcut away.

---

## 2. The hidden asset — we are not starting from zero

The codebase already contains almost everything needed to fix discoverability; it
is simply **not surfaced**. Five parallel, action-keyed resource bundles exist in
`oriedita/src/main/resources/`:

| Bundle | Purpose | Status |
|---|---|---|
| `name.properties` | Human-readable label per action (214 entries: "Angle Bisector", "Rabbit Ear", "Flatfold Vertex"…) | Exists, EN |
| `tooltip.properties` | Tooltip text per action | **Already exists — barely wired** |
| `help.properties` / `help_jp.properties` | Long description per action | Exists, EN + JP |
| `gif.properties` | Demo animation path per action | Exists |
| `icons.properties` | Icon path per action | Exists |
| `hotkey.properties` | Keystroke per action | Exists |

`HelpDialog.java` already *assembles* name + gif + description for a given action
key — but that rich help lives inside a modal dialog instead of on hover. Buttons
are built from a central registry (`ToolsPanel.java:88` looks up `icons` by key),
so a **single change at button-construction time can attach tooltip + hotkey + help
to all ~214 buttons at once**.

Structure also already partially exists: the redesigned `tab/` package
(`DrawingTab`, `FoldingTab`, `ReferencesTab`, `SettingsTab`) groups actions into
tabs, and `DrawingTab` even defines section labels ("Draw", "Select", "Edit"). The
look-and-feel is **FlatLaf** (`themes/FlatLaf.properties`), which supports modern
theming, rounded components, and design tokens.

**Implication:** this is a *surfacing + restructuring + visual-system* effort built
on existing infrastructure, not a ground-up rewrite. That is what makes "improve it
immensely" realistic.

---

## 3. Design principles (the rules we hold every change against)

1. **Recognition, not recall.** Every interactive element exposes a plain-language
   name on hover. No user should ever face an undocumented code.
2. **Progressive disclosure.** Show the ~20 % of tools used 80 % of the time by
   default; everything else is one expand/search away. (Hick's Law.)
3. **Separate the tool from its parameters.** A tool is *what you do*; its settings
   are *how*. These get distinct, predictable homes (Illustrator/Inkscape pattern).
4. **Group by meaning, with visible regions.** Whitespace, dividers, and bordered
   cards make groups perceptible. Max ~5–7 items per group (Miller). (Proximity /
   Common Region.)
5. **Meet user expectations.** Adopt the conventional editor layout — left tool
   rail, contextual top options bar, right properties panel, bottom status bar —
   and conventional iconography. (Jakob's Law.)
6. **Bigger, well-spaced targets**, with the most frequent tools placed for fast
   access (edges/corners are "infinitely large" targets). (Fitts's Law.)
7. **Aesthetic consistency.** One icon set, one spacing scale, one type ramp, one
   color system. (Aesthetic–Usability Effect.)
8. **Preserve power.** Keep density, hotkeys, and an optional "classic layout" so
   existing muscle memory survives. (Tesler's Law.)

---

## 4. Inspiration — what mainstream tools solved, and what we borrow

| Tool | Pattern worth stealing |
|---|---|
| **Adobe Illustrator / Inkscape** | Left **tool rail** (one single-purpose tool per slot) + a **contextual options bar** at the top that changes to show *only the active tool's* parameters. This is the single highest-leverage structural idea for Oriedita: it directly removes the wall of always-visible parameter widgets. |
| **Figma** | Quiet canvas; a **right-side properties panel** that's contextual and fully labeled; a **command palette** (⌘/Ctrl+K) to jump to any action by name. |
| **Blender** | **Tabbed properties**, **collapsible panels**, and **search (F3)** for the long tail — but also a cautionary tale on density; its "Quick Favorites" idea is worth copying. |
| **Affinity Designer** | Clean **personas/modes** (e.g., a Draw mode vs. a Fold mode) and tidy grouped "studio" panels. |
| **Fusion 360 / CAD ribbons** | **Named groups** with icon + text labels; contextual ribbon tabs per workflow stage. |
| **ORIPA (Oriedita's ancestor)** | Origami-specific domain vocabulary and the flat-foldability/fold-estimation workflow — keep the domain depth, replace the dated presentation. |

Convergent layout (the "Jakob's Law" target Oriedita should adopt):

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Menu bar:  File   Edit   View   Draw   Fold   Help                        │
├──────────────────────────────────────────────────────────────────────────┤
│  Contextual Options Bar  ← shows ONLY the active tool's parameters         │
├────────┬─────────────────────────────────────────────────────┬───────────┤
│ Tool   │                                                       │ Properties│
│ Rail   │                    CANVAS                             │  (right): │
│ (left, │              (crease pattern)                         │ grouped,  │
│ icons, │                                                       │ collapsi- │
│ 1/tool,│                                                       │ ble cards │
│ tooltip│                                                       │ — Line    │
│ +hotkey│                                                       │  Type,    │
│ )      │                                                       │  Grid,    │
│        │                                                       │  Fold,    │
│        │                                                       │  View     │
├────────┴─────────────────────────────────────────────────────┴───────────┤
│ Status bar: mouse (461, 98) · L=4 · 1/8 · flat-foldable ✓ · zoom 0.8×      │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Target information architecture (sectioning)

Collapse the ~214 actions into a small, named taxonomy. Each top-level group becomes
a tool-rail cluster, a menu, and/or a right-panel card. Proposed groups (each ≤ ~7
primary items, rest behind "More…"):

1. **Draw** — free line, restricted line, perpendicular, angle bisector, axioms,
   rabbit ear, parallel, mirror line, polygon, voronoi, equally-divided line.
2. **Select** — select / unselect / select-all, box, lasso, select-by-type.
3. **Transform** — move, 4-point move, copy, 4-point copy, reflect, rotate, delete,
   trim branches.
4. **Line Type** — Mountain / Valley / Edge / Aux assignment + per-type color
   (today's `M / V / E / A` strip and `C_col`).
5. **Grid & Reference** — grid divisions & snap, diagonal gridlines, import
   reference image, symmetry guides.
6. **Fold & Check** — estimate fold, flat-foldability check, folded view, layer
   ordering (today's `ckO` / `ckT` / `cAMV` / `fxO` / `fxT` family — *decode in
   Phase 0*).
7. **View** — zoom, pan, antialias, line width, point size, transparency, dark/light.
8. **File / IO** — `.cp`, `.fold` import/export, save, image export.

> The cryptic codes (`ckO`, `AS100`, `CP_rcg`, `S_face`, `ad_fnc`, `a_s`, `FC/BC/LC`,
> `L1..A3`) are **not guessed here** — Phase 0 decodes each from its action class and
> `name.properties`/`help.properties` entry, producing an authoritative glossary
> before any are renamed in the UI.

---

## 6. Phased roadmap

Ordered by **value ÷ risk**. Each phase ships independently and leaves the app
working. Early phases are cheap, central, and high-impact; later phases are
structural.

### Phase 0 — Audit & decode  *(gate; ~1–2 days)*
- Build the **action glossary**: every action key → current label, decoded meaning,
  source class, current hotkey, group. Cross-reference `name`/`help`/`tooltip`
  bundles against the actual `Action` classes.
- Decode every cryptic on-screen code (`ckO`, `AS100`, `CP_rcg`, …) to a real name;
  fill gaps in `name.properties` / `tooltip.properties`.
- Inventory every panel/button against the screenshot. Output: a glossary doc +
  group assignment table that drives every later phase.
- **No UI change yet.** Deliverable is knowledge.

### Phase 1 — Recognition layer  *(quick win, highest leverage)*
- One central change at button construction: attach **tooltip = `name` + `hotkey`**,
  and an extended hover help-card using the existing `help` + `gif` bundles
  (reuse `HelpDialog`'s assembly logic as a hover popover). Fixes ~214 buttons at once.
- Add accessible names for screen readers while there.
- Result: nothing moves, but every control becomes self-explanatory on hover.
  Immediate, dramatic comprehensibility gain at minimal risk.

### Phase 2 — Grouping & visible regions
- Replace flat grids with **labeled sections**: section headers, dividers, and
  bordered/`Common Region` cards. Apply Miller's chunking (≤7 primary per group).
- **Reorder by frequency**; demote rarely used actions behind a "More…" expander
  (progressive disclosure → directly attacks Hick's Law).
- Lean on the existing `tab/` redesign (`DrawingTab` etc.) rather than the legacy
  flat panels; finish and adopt that path.

### Phase 3 — Tool ↔ parameter split (contextual options bar)
- Introduce the **left tool rail** (one labeled tool per slot) and a **top
  contextual options bar** that renders only the active tool's settings (the
  `toolsetting/*` UIs already model per-tool settings — route them here).
- Removes the permanent wall of parameter widgets (`L1=…A3=`, ratio, precision,
  angle-system fields) from always-on view.

### Phase 4 — Visual system  *(the "make it pretty" phase)*
- One **icon set** (replace ambiguous glyphs; add text labels where space allows).
- **Design tokens** via FlatLaf: spacing scale (4/8 px), type ramp, radius, an
  accent color, semantic M/V/E colors. Dark **and** light parity.
- Consistent control sizing and padding → bigger Fitts targets + Aesthetic–Usability
  lift.

### Phase 5 — Search / command palette
- ⌘/Ctrl+K (and/or F3) action search over `name`/`help`. An escape hatch for the
  long tail and the ultimate Hick's-Law mitigation — any of 214 actions reachable by
  typing its name.

### Phase 6 — Onboarding & validation
- First-run layout tour, empty-state hints on the canvas, "Quick Favorites".
- **Usability test** with 3–5 origami users (task: "draw an angle bisector, set it
  as a mountain fold, check flat-foldability"). Measure time-to-find and error rate
  before/after. Iterate.

---

## 7. Risks & mitigations

- **IntelliJ GUI-Designer `.form` files are hand-laid** → restructuring is tedious
  and merge-fragile. *Mitigation:* do structural work in the `tab/` redesign classes;
  change layout incrementally, one panel per PR.
- **Power-user muscle memory** → relocating tools frustrates veterans.
  *Mitigation:* preserve all hotkeys; ship an optional **"Classic layout"** toggle;
  keep density as a setting.
- **Hotkey/action regressions** during rewiring. *Mitigation:* the Phase 0 glossary
  doubles as a regression checklist; snapshot tests where feasible.
- **i18n / translations** must not break (`TRANSLATIONS.md`, `help_jp`). *Mitigation:*
  keep all user-facing strings in bundles; never hard-code labels in the new UI.
- **Scope creep.** *Mitigation:* every phase is independently shippable; stop after
  any phase and the app is still better than before.

---

## 8. Definition of done (per the original ask)

- **Comprehensible:** zero undocumented codes; every control names itself on hover;
  in-context help with demo GIFs. (Recognition, Help heuristic.)
- **Sectioned:** a small named taxonomy with visible regions, not one flat grid.
  (Proximity, Miller.)
- **Inspired by other tools:** conventional tool-rail + contextual-options + right
  properties layout; command palette. (Jakob's Law.)
- **Pretty:** one icon set, design tokens, dark/light parity, generous spacing.
  (Aesthetic–Usability.)
- **Grounded in UX law:** each change traces to a specific law in §1/§3.
- **Without losing power:** every existing action still reachable; hotkeys intact;
  classic layout available. (Tesler's Law.)

---

## 9. Recommended first move

**Phase 0 → Phase 1.** The decode/glossary pass plus wiring the existing
`name`/`tooltip`/`help`/`gif`/`hotkey` bundles into hover tooltips is low-risk,
touches one central construction point, and converts the most-complained-about
problem (unlabeled cryptic buttons) into a self-documenting UI in days — before any
pixels move.

---

## 10. Phase 0 Findings (executed 2026-06-15)

### 10.1 Architecture (how a button becomes a button)
- Every action is an entry in the **`ActionType` enum** (key = `xxxAction`).
- Six parallel `.properties` bundles in `oriedita/src/main/resources/` are keyed by that
  action key: `name` (label), `tooltip` (short desc), `help` (multi-line HTML),
  `icons` (private-use glyph from a custom font), `hotkey`, `gif`.
- **`ButtonServiceImpl`** is the central registrar. `addDefaultListener(root)` walks each
  tab's component tree; for each button it reads the bundles by `getActionCommand()` and
  sets the icon (`GlyphIcon`), binds the hotkey, attaches the action, and calls
  `setTooltip(key)` → builds `<html><i>Name</i><br>tooltip<br>Hotkey: …` and
  `setToolTipText(...)`. **Single injection point** for all future per-button work.
- So glyph-only appearance = the `icons` font glyph; cryptic *text* labels appear only on
  buttons that have **no** glyph and fall back to hardcoded `.form` `setText`.

### 10.2 The bundle problem
- `tooltip.properties`: **228 stub keys, all empty.** The richer `help.properties` (238
  multi-line HTML entries) exists but `setTooltip()` does not fall back to it. → hover
  shows only name + hotkey, never *what the tool does*.
- `name.properties` existed in **two** modules. `oriedita/` = correct & near-complete (2
  empty). `oriedita-ui/` = stale: 29 empty, 186 values differing, several **wrong**
  (`lineWidthDecreaseAction` read "Increase the line width"). One-way module dep
  (`oriedita` → `oriedita-ui`) means both sit on the assembled classpath → shadowing
  hazard.

### 10.3 Decoded glossary (the on-screen cryptic codes)
| On-screen | Action key | Real meaning (from `name.properties`) |
|---|---|---|
| `ckO` | `ckOAction` | Check Coincident Line Errors |
| `ckT` | `ckTAction` | Check T-Intersecting Line Errors |
| `cAMV` | `cAMVAction` | Check Flat Foldability Errors |
| `fxO` | `fxOAction` | Fix Coincident Lines |
| `fxT` | `fxTAction` | Fix T-Intersecting Lines |
| `CP_rcg` | `suitei_01Action` | Recognize Crease Pattern |
| `S_face` | `koteimen_siteiAction` | Set Starting Face |
| `a_s` | `anotherSolutionAction` | Find Next Available Solution |
| `AS100` | `as100Action` | Search Up To 100 Solutions *(was nameless — added)* |
| `FC` | `frontColorAction` | Fold Front Color |
| `BC` | `backColorAction` | Fold Back Color |
| `LC` | `lineColorAction` | Fold Line Color |
| `ad_fnc` | `ad_fncAction` | Open Additional Functions |
| `C_col` | `c_colAction` | Auxiliary Color |

### 10.4 Coverage (weakest-link probe → resolved)
The `tab/` redesign is **not skeletal**: 117 distinct labels across 4 tabs with real
section headers ("Circles", "Measure", "Background", "Misc", "Select"). The retrofit
strategy stands; the weakest link the §9 reasoning flagged is **low-risk**.

### 10.5 Fix applied in this pass (zero code-logic risk, data only)
- Filled the 2 empty `name` values (`gridConfigureAction` = "Configure Grid",
  `gridConfigureOkAction` = "Confirm Grid Configuration").
- Added the missing `as100Action` = "Search Up To 100 Solutions".
- Reconciled the duplicate: `oriedita-ui/name.properties` overwritten with the corrected
  canonical file. **Both now byte-identical, 229 keys, 0 empty.** The 8 ui-only keys were
  verified dead (0 code refs) → nothing lost.
- **Not yet built with Maven** (properties-only change, parse-verified + key-parity
  verified; a module build is still recommended before merge).

### 10.6 Re-scoped Phase 1 (next)
1. **`setTooltip()` → fall back to `help` when `tooltip` empty** (careful with the nested
   `<html>` wrapper). Instantly upgrades all ~219 help-having tooltips from name-only to
   name + description + hotkey. Small, central, ~1 method.
2. **Show real text on the no-icon fallback buttons** (`ckO`/`fxO`/`cAMV`/…): set their
   label from `name` instead of the hardcoded `.form` abbreviation, or give them glyphs.
   This is what finally removes the *visible* cryptic codes.
3. Optionally begin populating `tooltip.properties` with concise one-liners (curated,
   not machine-bulk) for the highest-traffic tools.
