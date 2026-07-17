# Final QA — Accessibility Report

Method: static review of the Daily Challenge layouts (`activity_daily_challenge*.xml`, the home card in
`activity_home.xml`) and the rendering code. **No TalkBack / on-device AT testing performed** (no device);
those checks are in the manual script. Findings below are from source.

## Passing
- **Touch targets** — option `RadioButton`s `minHeight=56dp`; primary buttons 52–56dp; both exceed the
  48dp minimum.
- **Text labels for controls** — options carry visible text ("A) …"), so TalkBack announces them; the
  primary CTA text changes with state ("Devam"/"Bitir"/"Kontrol Et").
- **Progress is textual, not just a bar** — `dcProgressText` ("2/5") accompanies the `ProgressBar`, so
  progress is announced as text.
- **Information not by colour alone** — review reveal now marks the correct option "✓" and wrong pick "✗"
  (icon markers) *in addition to* colour (fixed this phase, `866cb03`); the verdict is also explicit text
  ("Doğru ✓" / "Yanlış ✗").
- **Decorative vs meaningful images** — the question figure sets a content description
  (`dc_cd_question_figure`), so essential figures are focusable/announced.
- **Contrast** — text tokens (`#111827` on `#FFFFFF`, emerald `#059669` headings) meet WCAG AA for normal
  text; muted `#6B7280` on white ≈ 4.8:1 (AA for normal text).
- **Logical order** — layouts are top-to-bottom linear (`ScrollView`/`ConstraintLayout` chains), giving a
  sensible focus order.

## Findings (documented, not release-blocking)
| # | Severity | Finding | Recommendation |
|---|---|---|---|
| A11Y-1 | P3 | Question figure content description is generic ("Soru görseli") for all figures. | Author per-figure alt text in the bank (content task). |
| A11Y-2 | P3 | Countdown / streak lines are plain `TextView`s with no `contentDescription` refinement; TalkBack reads the raw string (e.g. "8s 42dk"), which is abbreviated. | Consider a spoken-friendly `contentDescription` ("8 saat 42 dakika"). |
| A11Y-3 | P3 | The correct/wrong option markers set on `RadioButton` text update the label, but no `announceForAccessibility` is fired on reveal, so a TalkBack user must re-read the options to discover the result. | Fire `announceForAccessibility` with the verdict on reveal. |
| A11Y-4 | P2→verify | Large-font behaviour (130/150/200%) and long-Turkish-string wrapping in the fixed-height cards/buttons (52–56dp) can clip. Static layouts use `wrap_content` for text containers, but button heights are fixed. | **Verify on device** at 200% font (manual script §Accessibility); switch fixed button heights to `wrap_content`+minHeight if clipping is observed. |

## Font scaling
Text sizes are in `sp` (scale with user font size) — good. Risk is clipping in fixed-height buttons at
extreme scales; this needs on-device confirmation (A11Y-4). No code change made without a reproduced clip.

## Layout breadth
Portrait is the primary orientation; activities declare `configChanges` for orientation so they don't
recreate on rotation. Landscape/tablet rendering is **not verified** (no device) — see manual script.

## Net
No accessibility **blocker** found in source; one item (A11Y-4, large-font clipping) must be confirmed on a
device during internal QA. The colour-only-information risk was found and fixed this phase.
