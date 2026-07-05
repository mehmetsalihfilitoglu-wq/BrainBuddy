# BrainBuddy Global Design System & Polish Plan

## Context

The app has grown organically, and while a partial design system exists (dimens.xml spacing scale, some shared styles), many screens bypass it with hardcoded values. This creates visual inconsistency: different corner radii (16dp, 20dp, 22dp, 24dp), mixed padding (20dp vs 24dp), typography not using shared styles, icon sizes varying (24dp vs 40dp vs 48dp), and hero chips without rounded corners. The goal is to standardize everything into one coherent design system and apply it across all ~50+ layout files.

---

## Phase 1: Expand the Design System Foundation

### 1.1 `dimens.xml` - Add missing tokens

```
space_2  = 2dp     (micro gaps)
space_6  = 6dp     (chip padding)
space_20 = 20dp    (card inner padding)
space_40 = 40dp    (section gaps)
space_48 = 48dp    (large sections)

# Standardized card radii
radius_sm  = 12dp   (chips, inputs, choice buttons)
radius_md  = 16dp   (inner cards, question cards)
radius_lg  = 20dp   (main cards, all screens)
radius_xl  = 24dp   (hero banner only)

# Icon sizes
icon_sm   = 20dp
icon_md   = 24dp
icon_lg   = 32dp
icon_xl   = 40dp

# Elevation
elevation_none = 0dp
elevation_sm   = 2dp
elevation_md   = 4dp

# Stroke
stroke_thin = 1dp
stroke_default = 1.5dp
```

### 1.2 `colors.xml` - Add hero chip shape color
- `chip_hero_bg_solid` = existing `#26FFFFFF` (keep)
- No new colors needed, palette is already good

### 1.3 `styles.xml` - Standardize & expand typography + components

**Typography hierarchy (replace scattered hardcoded sizes):**
- `BB.Text.DisplayLarge` - 32sp bold (lock screen title, special headings)
- `BB.Text.DisplayMedium` - 26sp bold (result screen score title)
- `BB.Text.HeadlineLarge` - 24sp bold (page titles like "Gelisim")
- `BB.Text.HeadlineMedium` - 20sp bold (section titles, percentage)
- `BB.Text.TitleLarge` - 18sp bold (greeting, question text, card titles)
- `BB.Text.TitleMedium` - 16sp bold (list item titles, stats)
- `BB.Text.BodyLarge` - 16sp (body text, option buttons)
- `BB.Text.BodyMedium` - 14sp (descriptions, stats)
- `BB.Text.BodySmall` - 13sp (daily goal, subtitles)
- `BB.Text.Caption` - 12sp (chips, badges, small labels)
- `BB.Text.ScoreLarge` - 48sp bold emerald (result score)

**Card styles consolidation:**
- Consolidate `Widget.BrainBuddy.AppCardWhite` as THE standard card
- Change radius from 22dp to `@dimen/radius_lg` (20dp) for consistency
- Change elevation from 5dp to `@dimen/elevation_md` (4dp) - less heavy
- Remove legacy `CardStyle`, `BB.Card` duplicates
- Keep `Widget.BrainBuddy.Card` for reports (already works)

**New component styles:**
- `BB.ListCard` - Full-width row card (growth hub items)
- `BB.HeroChip` - Rounded chip for hero banner badges
- `BB.SectionTitle` - Standard section header

**Drawable updates:**
- `bg_hero_chip.xml` - Rounded shape (radius_sm) with chip_hero_bg fill
- `bg_card.xml` - Update to use `radius_lg`
- `bg_choice_button.xml` - Already 16dp radius, keep as `radius_md`

### 1.4 `themes.xml` - Minor tweaks
- windowBackground stays as gradient (most screens override anyway)

---

## Phase 2: Home Screen Full Polish

**File:** `activity_home.xml`

Changes:
1. **Hero Banner**:
   - Add `radius_xl` (24dp stays)
   - Badge chips: use new `bg_hero_chip.xml` drawable (rounded corners)
   - Chip padding standardize to `space_8` h, `space_4` v
   - Progress bar margin standardized

2. **Grid cards (2x2)**:
   - Gap between cards: `space_8` (was `space_4` marginEnd, creating asymmetry)
   - Both rows use identical margins
   - Icon size: `@dimen/icon_xl` (40dp - keep current, it works)
   - Title marginTop: `space_8` (keep)
   - Subtitle marginTop: `space_4` (keep)
   - Card inner padding: `space_16` (keep)
   - Fix: first row has `marginEnd=space_4` on left card + `marginHorizontal=space_4` on right card = unequal gaps. Standardize to `space_4` between all.

3. **Wrong pool card**:
   - Icon size: `@dimen/icon_xl`
   - Consistent spacing

---

## Phase 3: Screen-by-Screen Standardization

### 3.1 Quiz Screen (`activity_quiz.xml`)
- Root padding: `20dp` → `@dimen/space_20`
- Question card radius: `16dp` → `@dimen/radius_md`
- Question card elevation: `4dp` → `@dimen/elevation_md`
- Question text: use `BB.Text.TitleLarge` style
- Progress text: use `BB.Text.TitleMedium` style
- Option text: use `BB.Text.BodyLarge` style
- Margin values: hardcoded `12dp`→`space_12`, `16dp`→`space_16`, `20dp`→`space_20`

### 3.2 Quiz Result (`activity_quiz_result.xml`)
- Padding: `24dp` → `@dimen/space_24`
- Title: 26sp → use `BB.Text.DisplayMedium`
- Score: 48sp → use `BB.Text.ScoreLarge`
- Percentage: 20sp → use `BB.Text.HeadlineMedium`
- Points: 18sp → use `BB.Text.TitleLarge`
- Streak: 16sp → use `BB.Text.TitleMedium`
- Card inner padding: `28dp` → `@dimen/space_24`
- Card margins/spacing: use dimen refs
- Wrong answers title: 18sp → use `BB.Text.TitleLarge`

### 3.3 Student Profile (`activity_student_profile.xml`)
- Padding: `20dp` → `@dimen/space_20`
- Card radius: `20dp` → `@dimen/radius_lg`
- Card elevation: `4dp` → `@dimen/elevation_md`
- Card padding: `24dp` → `@dimen/space_24`
- Name EditText: use dimen refs
- Section title: 16sp bold → use `BB.Text.TitleMedium`
- Stats text: 14sp → use `BB.Text.BodyMedium`
- All margins: use dimen refs

### 3.4 Growth Hub (`activity_growth_hub.xml`)
- Padding: `20dp` → `@dimen/space_20`
- Page title: 24sp → use `BB.Text.HeadlineLarge`
- Subtitle: 14sp → use `BB.Text.BodyMedium`
- List card titles: 16sp → use `BB.Text.TitleMedium`
- List card subtitles: 13sp → use `BB.Text.BodySmall`
- Card inner padding: `20dp` → `@dimen/space_20`
- Emoji container: 48dp → `@dimen/space_48`
- Chevron: use drawable icon instead of text `›`
- All margins: dimen refs

### 3.5 Lock Screen (`activity_lock_screen.xml`)
- Title: `32sp` → use `BB.Text.DisplayLarge`
- Message: `16sp` → use `BB.Text.BodyLarge`
- Card padding: `32dp` → `@dimen/space_32`
- All margins: dimen refs

### 3.6 Parent Hub (`activity_parent.xml`)
- Already well-structured with dimen refs
- Summary card icons: `24dp` → `@dimen/icon_md` (keep small, 4-column)
- Category grid spacing: verify consistency

### 3.7 Onboarding Wizard (`activity_onboarding_wizard.xml`)
- Step text: use `BB.Text.Caption`
- Already minimal, keep

### 3.8 Remaining screens (batch)
All remaining layout files need:
- Hardcoded dp → dimen refs
- Hardcoded text sizes → style refs where practical
- Card radii → dimen refs
- Consistent padding pattern

Target files (grep for hardcoded values):
- `activity_settings.xml`, `activity_security.xml`
- `activity_schedules.xml`, `activity_time_limits.xml`
- `activity_blocked_apps.xml`, `activity_permissions_checklist.xml`
- `activity_avatar_shop.xml`, `activity_league.xml`
- `activity_stats.xml`, `activity_reports.xml`
- `activity_boss_test.xml`, `activity_gate.xml`
- `activity_quiz_cooldown.xml`, `activity_quiz_retry.xml`
- `activity_wrong_answer_review.xml`, `activity_wrong_answers_list.xml`
- `activity_pin_lock.xml`, `activity_login.xml`, `activity_register.xml`
- `activity_onboarding.xml`
- All `item_*.xml` files
- All `wizard_step_*.xml` files
- Junior module layouts
- Bottom sheets

---

## Phase 4: Drawable Cleanup

- `bg_hero_chip.xml` (NEW) - Rounded pill shape for hero badges
- `bg_card.xml` - Update radius to `@dimen/radius_lg`
- `bg_edit.xml` - Update radius to `@dimen/radius_sm`
- `bg_choice_button.xml` - Update to use `@dimen/radius_md`
- `bg_button.xml` - Update to use `@dimen/radius_md`

---

## Phase 5: Remove Legacy/Dead Styles

Clean up `styles.xml`:
- Remove `PrimaryButtonStyle` (duplicate of BBButton)
- Remove `BB.Card` (replaced by AppCardWhite)
- Remove `CardStyle` (replaced by AppCardWhite)
- Remove `BB` theme base (unused standalone)
- Keep `Widget.BrainBuddy.ParentEmeraldCard` if still referenced, else remove

---

## Execution Order

1. **dimens.xml** - Add all new tokens
2. **New drawable** - `bg_hero_chip.xml`
3. **Update drawables** - `bg_card.xml`, `bg_edit.xml`, etc.
4. **styles.xml** - Add typography scale, update card styles, remove legacy
5. **themes.xml** - No changes needed
6. **activity_home.xml** - Full polish
7. **Core screens** - quiz, result, profile, growth hub, lock screen
8. **Secondary screens** - parent, settings, avatar shop, etc.
9. **Item layouts** - list items, wizard steps
10. **Junior module** - junior layouts

---

## Key Design Decisions

| Token | Before | After | Rationale |
|-------|--------|-------|-----------|
| Card radius | 16/20/22/24dp | 20dp (radius_lg) | One standard for all main cards |
| Card elevation | 2/4/5dp | 4dp (elevation_md) | Less aggressive, more modern |
| Card stroke | 1/1.5dp | 1dp | Subtler, cleaner |
| Screen padding | 20/24dp mixed | 20dp standard | Consistent, enough breathing room |
| Card gap | 4/8/12dp mixed | 8dp grid, 12dp list | Grid=tight, list=comfortable |
| Button height | 48/56dp | 56dp always | One size, no confusion |
| Title text | 16/17/18sp | 16sp (TitleMedium) | Material3 standard |
| Page heading | 24/26sp | 24sp (HeadlineLarge) | Consistent page titles |

---

## Files to Change (Summary)

**Resource files (5):**
- `res/values/dimens.xml`
- `res/values/styles.xml`
- `res/values/colors.xml` (minor)
- `res/drawable/bg_hero_chip.xml` (NEW)
- `res/drawable/bg_card.xml`, `bg_edit.xml`, `bg_choice_button.xml`, `bg_button.xml`

**Layout files (~50+):**
- All `activity_*.xml` files
- All `item_*.xml` files
- All `wizard_step_*.xml` files
- Junior layouts
- Bottom sheets
- Include layouts

---

## Verification

1. Build: `./gradlew assembleDebug` must succeed
2. Visual check: Launch on emulator/device and navigate through:
   - Home screen → verify hero + grid cards look balanced
   - Quiz → verify question card, options, buttons
   - Result → verify score card, buttons
   - Profile → verify card, avatar, stats
   - Growth Hub → verify list cards
   - Parent Hub → verify summary cards + grid
   - Lock Screen → verify centered card
3. Check no hardcoded dp/sp remain in layouts (grep verification)
