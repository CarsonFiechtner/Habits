# KeepUp

A personal, offline-first Android habit tracker. Track any number of daily, weekly or monthly habits in
units you define, with strict streaks. Steps (via Health Connect) and screen time (via Usage Stats) are
planned as automatic sources.

See **[docs/DESIGN.md](docs/DESIGN.md)** for the full design.

## Install (Obtainium)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium) on the phone.
2. **Add app** → paste `https://github.com/CarsonFiechtner/KeepUp` → **Add**.
3. Obtainium installs the latest release APK and notifies you of new ones.

Releases are signed with the same key every time, so updates install over the previous version and keep
your data.

## Development

Requirements: JDK 21 and an Android SDK with `platforms;android-37.2` and `build-tools;37.0.0`
(`ANDROID_HOME` set, or `sdk.dir` in `local.properties`).

```bash
./gradlew test            # unit tests (domain logic lives in :core:domain)
./gradlew lintDebug       # Android lint
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

The debug build has application ID `io.github.carsonfiechtner.keepup.debug` and the label "KeepUp Dev",
so it can be installed next to the release build. Every push runs CI, which uploads the debug APK as a
workflow artifact.

### Modules

| Module | Contents |
|---|---|
| `:app` | Android app: Compose UI, Hilt DI, DataStore settings, (later) Room, sync and reminders |
| `:core:domain` | Pure Kotlin domain logic: period math, evaluation and streaks. Fast JVM tests |

## Releases

Pushing a tag `vX.Y.Z`, or running the **Release** workflow manually (*Actions → Release → Run
workflow*, version `X.Y.Z`), builds a signed release APK and publishes it as a GitHub Release. A manual
run creates the tag itself. `versionName` is the tag without the `v`, and
`versionCode = major*10000 + minor*100 + patch`. Tags containing `-` (e.g. `v0.2.0-beta1`) are marked as
pre-releases.

One-time setup: add these **repository secrets** (*Settings → Secrets and variables → Actions*):

| Secret | Value |
|---|---|
| `KEEPUP_KEYSTORE_BASE64` | The release keystore (`.p12`), base64-encoded |
| `KEEPUP_KEYSTORE_PASSWORD` | Keystore password |
| `KEEPUP_KEY_ALIAS` | `keepup` |
| `KEEPUP_KEY_PASSWORD` | Key password (same as the keystore password for PKCS12) |

Keep a copy of the keystore and password somewhere safe outside GitHub (e.g. a password manager). If the
key is lost, future releases can't update the installed app: you'd have to export data, uninstall and
reinstall.
