# EDUmio — Package / Namespace Migration Report

## Decision
Owner directed a full package migration (app unreleased): namespace **and** applicationId →
**`com.edumio.app`**, application class → **`EDUmioApp`**. Since there are no production installs, this
does not strand existing users, and there is no Play Store identity to preserve.

## What changed
| Item | Old | New |
|---|---|---|
| Gradle `namespace` | `com.mioacademy.app` | `com.edumio.app` |
| Gradle `applicationId` | `com.mioacademy.app` | `com.edumio.app` |
| Source tree | `app/src/{main,test,androidTest}/java/com/mioacademy/app/**` | `…/com/edumio/app/**` |
| Package declarations / imports / FQNs / `R` refs | `com.mioacademy.app…` | `com.edumio.app…` |
| Application class + file | `MioAcademyApp` / `MioAcademyApp.kt` | `EDUmioApp` / `EDUmioApp.kt` |
| Manifest `android:name` | `.app.MioAcademyApp` | `.app.EDUmioApp` |
| Manifest `tools:context` (layouts) | `com.mioacademy.app…` | `com.edumio.app…` |
| FileProvider authority | `${applicationId}.provider` | unchanged token → now `com.edumio.app.provider` |
| Instrumentation runner | AndroidX default (no brand) | unchanged |
| Startup log tag | `MioAcademyStartup` | `EDUmioStartup` |

Manifest components use relative names (`.HomeActivity`, `.dailychallenge.*`, etc.), so they follow the
new namespace automatically. All `<activity>/<provider>` declarations resolve against `com.edumio.app`.

## Verified in the built APK
- `aapt dump badging` → `package: name='com.edumio.app'`, `application-label:'EDUmio'`.
- Binary scan of `app-debug.apk` for `com/mioacademy`, `com.mioacademy`, `brainbuddy` → **0 matches**.
- Compile + 114 unit tests + `assembleDebug` green.

## Consequence note
`com.mioacademy.app` never contained a *forbidden brand token* (the eradication list did not originally
include "mioacademy"); the owner explicitly added MioAcademy to the forbidden set and approved the
applicationId change. As the app is unreleased, changing applicationId has **no** installed-base impact.
Had the app shipped, this would create a new Play Store identity — flagged and approved.
