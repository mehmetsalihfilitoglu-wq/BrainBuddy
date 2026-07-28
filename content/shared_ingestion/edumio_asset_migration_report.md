# EDUmio Brand — Asset Migration Report

## Wordmark / logo
Official wordmark **EDUmio** — "EDU" `#25D366`, "mio" `#000000`, clean sans-serif. Rendered in the
rebranded legal HTML headers (`.logo .edu` / `.logo .mio`). No standalone raster logo asset existed in
the app previously; in-app headers use styled text.

## Launcher / adaptive icon
| Asset | Before | After |
|---|---|---|
| `res/drawable/ic_launcher_background.xml` | stock Android green `#3DDC84` + grid | **solid EDUmio `#25D366`** |
| `res/drawable/ic_launcher_foreground.xml` | stock Android robot head | **white "E" monogram** vector, within the 66dp adaptive safe zone |
| `res/mipmap-v26/ic_launcher.xml`, `ic_launcher_round.xml` | reference bg+fg | unchanged wrappers → now render EDUmio |
| `monochrome` layer | robot | white "E" (themed-icon ready) |

The actual vector **paths/colours** were changed (not just filenames): the icon now shows an EDUmio "E"
on brand green, verified in source and by resource-merge/`assembleDebug`.

### Known limitation (flagged, not silently skipped)
`res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` + `_round.webp` are **binary raster**
placeholders used **only on API < 26**. They cannot be hand-authored in this environment and still show
the old placeholder pixels on very old devices. The adaptive vector covers API 26+ (the target range).
**Action required:** a designer must regenerate these `.webp` from the EDUmio icon before public release.

## Color system
Brand-semantic tokens only (single source of truth in `colors.xml`; whole app inherits via `@color`):
| Token | Before | After |
|---|---|---|
| `brand_primary` | `#10B981` | **`#25D366`** |
| `brand_primary_dark` | `#059669` | **`#1DA851`** (accessible darker green for pressed/headings) |
| `brand_primary_light` | `#ECFDF5` | **`#E8FAEE`** |
| `brand_success` | `#10B981` | **`#25D366`** |
Preserved (NOT touched): `color_warning`, `color_error`, information/neutral tokens, text `#111827`,
surfaces white/`#F8FAFC`, and any chart palettes. Accessibility: white on `#25D366` and `#1DA851` remains
legible for the icon monogram and CTAs; dark text on light surfaces is unchanged. On-device contrast at
large font is part of the manual QA script.

## Legal / info HTML (visual + brand)
Rebranded with the EDUmio wordmark header and green accent, content reduced to honest notices (see legal
audit): `assets/{privacy_policy,terms_of_use,data_usage,ad_info,parent_info}_tr.html`,
`docs/{privacy,terms}.html`.

## Screenshots / mockups
The prior UI-evidence artifact (Daily Challenge screens) used the old emerald `#10B981`; it is a static
render, not shipped, and does not affect the app. A refreshed EDUmio-green render can be produced on
request; not a release gate.

## Verification
`assembleDebug` SUCCESSFUL; resource merge clean; launcher references (`@mipmap/ic_launcher`,
`@mipmap/ic_launcher_round`) resolve; `BrandComplianceTest` scans the icon vectors and finds no old brand.
