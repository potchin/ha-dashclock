# HA DashClock

A minimal DashClock-API extension that shows the current state of a Home
Assistant entity (e.g. a template sensor) inside DashClock-compatible widget
hosts - built and tested against **Chronus**.

## How it works

- `HaDashClockExtension` (`app/src/main/java/uk/co/potchin/hadashclock/HaDashClockExtension.kt`)
  extends `com.google.android.apps.dashclock.api.DashClockExtension` and
  overrides `onUpdateData(reason: Int)`. This is still the correct/current
  entry point - the upstream `romannurik/dashclock` API is archived and
  unchanged since 2013, there is no newer lifecycle method.
- On every update (periodic, manual refresh, initial, settings-changed - all
  handled identically), it calls
  `GET {base_url}/api/states/{entity_id}` on your Home Assistant instance with
  `Authorization: Bearer <long-lived token>`, via OkHttp, and parses the
  `state` field out of the JSON response with `org.json`.
- The entity's `state` becomes the `expandedBody` (real `\n` newlines,
  preserved as-is - no HTML), its first non-blank line becomes
  `expandedTitle` and (truncated to ~20 chars) the collapsed `status`.
- Network/auth/missing-entity failures produce a clear `"HA error"` status
  and a descriptive `expandedBody` instead of crashing or leaving stale data.
- Settings (`SettingsActivity`) are stored in `EncryptedSharedPreferences`
  (`androidx.security:security-crypto`), not plaintext prefs.
- Tapping the widget's text (its `clickIntent`) launches the invisible
  `RefreshTapActivity`, which immediately calls
  `ContentResolver#notifyChange()` on a stable URI exposed by the no-op
  `RefreshSignalProvider`, then finishes without ever becoming visible. The
  extension registers that same URI with the host via
  `addWatchContentUris()` in `onInitialize()`, which is the documented way
  for a DashClock extension to trigger an out-of-band refresh
  (`UPDATE_REASON_CONTENT_CHANGED`) - there's no other public API for an
  extension to "push" a manual update.
- The `settingsActivity` meta-data value is the package-relative
  `.SettingsActivity` (not the fully-qualified class name). Some hosts
  (Chronus included) resolve this value by naively prepending the
  extension's package name rather than only doing so when the value starts
  with `.` - using a relative name keeps it working correctly everywhere.

## Why Chronus can host this extension

DashClock extensions are normally only bindable by the official DashClock
app, which the extension verifies by checking the caller's signing
certificate. Setting the `worldReadable` meta-data flag (alongside
`protocolVersion=2`) on the `<service>` disables that signature check, which
is exactly what lets a different host app like Chronus bind to it. See the
`<service>` declaration and comments in
`app/src/main/AndroidManifest.xml`.

## Dependency: the DashClock API itself

`romannurik/dashclock` is archived and its JitPack build is broken, but the
API jar it published to Maven Central in 2013
(`com.google.android.apps.dashclock:dashclock-api:2.0.0`) is still live and
unchanged - it's declared as a normal Gradle dependency in
`app/build.gradle.kts`, no vendoring of AIDL/source required.

## Building (via Podman - no local Android SDK/Gradle needed)

The project ships a standard Gradle wrapper (`./gradlew`), generated once
using the Podman build image below so no binary had to be hand-crafted. Two
ways to build:

```sh
# One-off: build the build-environment image
podman build -t ha-dashclock-build container/

# Build a debug APK (output: app/build/outputs/apk/debug/app-debug.apk)
bash container/build.sh

# Build a release APK (see "Signing" below for what key is used)
bash container/build.sh assembleRelease
```

`container/build.sh` bind-mounts the project into the container and runs
`gradle <task>` there (using the image's system-installed Gradle), so the
resulting APK appears directly under `app/build/outputs/apk/` on your host
machine. Since the checked-in `./gradlew` wrapper works too (and is what CI
uses), you can just as well run `./gradlew assembleDebug` directly inside the
container, or in any environment with JDK 17 + the Android SDK installed.

If you'd rather use Android Studio, that works too - just open the project
directory.

### Signing

- **Local/personal builds** (`assembleRelease` with no extra setup): falls
  back to the auto-generated debug keystore, so it just works with zero
  configuration. These builds are *not* upgrade-compatible with the signed
  releases published on GitHub (see below) - installing one over the other
  requires `adb uninstall` first.
- **CI-published releases**: signed with a dedicated, stable release key so
  every GitHub Release can be installed as an upgrade over the previous one.
  See "Releases & CI" below for how this key is stored and used.

## Releases & CI

Two GitHub Actions workflows live under `.github/workflows/`:

- **`ci.yml`** - runs on every push/PR to `main`. Builds a debug APK (no
  signing secrets involved) purely to catch build breakage early.
- **`release.yml`** - runs when you push a tag matching `v*` (e.g. `v1.2.0`).
  Builds a *signed* release APK using the dedicated release key (see below),
  then publishes it as a downloadable asset on a GitHub Release for that tag,
  with auto-generated release notes.

To cut a release:

```sh
git tag v1.2.0
git push origin v1.2.0
```

Then grab `ha-dashclock-v1.2.0.apk` from the repo's Releases page and
`adb install` it (or download straight to the phone and tap it - Android will
prompt to allow installs from that source).

### The release signing key

Google Play isn't used here, so there's no Play App Signing to lean on -
instead there's one self-signed key (`keystore/release.jks`, generated once
with `keytool`, ~27 year validity) that every published release is signed
with. This is the standard approach for a sideload-only app: it's not about
proving identity to a store, it's purely so Android will treat v1.2.0 and
v1.3.0 as the same app and let you upgrade in place instead of forcing an
uninstall.

**This keystore is never committed** (`.gitignore` excludes `keystore/`,
`*.jks`, `*.keystore`). Instead it lives as a base64 blob in a GitHub Actions
secret, decoded to a temp file at the start of the `release.yml` job and
deleted again at the end. `app/build.gradle.kts` reads four environment
variables to build the release `signingConfig`, and only does so when *all
four* are present - otherwise it falls back to the debug keystore, so local
builds never need to know this key exists:

| Env var (local) | GitHub secret (CI) |
|---|---|
| `RELEASE_KEYSTORE_PATH` | *(decoded from `RELEASE_KEYSTORE_BASE64` at runtime)* |
| `RELEASE_KEYSTORE_PASSWORD` | `RELEASE_KEYSTORE_PASSWORD` |
| `RELEASE_KEY_ALIAS` | `RELEASE_KEY_ALIAS` |
| `RELEASE_KEY_PASSWORD` | `RELEASE_KEY_PASSWORD` |

**If you ever need to regenerate this key** (e.g. it's lost - see warning
below), generate a new one and update all four GitHub secrets:

```sh
keytool -genkeypair -v -keystore release.jks -alias ha-dashclock-release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass '<choose a password>' -keypass '<same or different password>' \
  -dname "CN=HA DashClock, OU=ha-dashclock, O=Potchin, L=NA, ST=NA, C=GB"

base64 -w0 release.jks | pbcopy   # or xclip/wl-copy/etc - paste into RELEASE_KEYSTORE_BASE64
```

Set the four secrets under the repo's **Settings → Secrets and variables →
Actions → New repository secret**.

> [!WARNING]
> **Back up `keystore/release.jks` and its password somewhere safe (password
> manager, offline backup) outside of git.** If it's lost, there is no way to
> publish an update that existing installs can upgrade to - every user would
> need to uninstall and reinstall from scratch. GitHub Actions secrets are
> write-only (you can't read them back from the UI once saved), so the GitHub
> secret alone is not a backup.

## Installing

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open the **HA DashClock** app (it has its own launcher icon) to
configure it, or add it as an extension inside Chronus and use its "Configure"
entry - both reach the same `SettingsActivity`.

## Configuration

- **Home Assistant base URL** - e.g. `https://ha.example.com` or
  `http://192.168.1.10:8123`. (Cleartext HTTP is explicitly allowed in the
  manifest for LAN-only HA setups without a TLS reverse proxy.)
- **Long-lived access token** - create one under your HA user profile
  (Settings → your profile → Security → Long-lived access tokens).
- **Entity ID** - defaults to `sensor.dashboard_text`. Only the entity's
  `state` field is used (not a specific attribute).

Use the **Test connection** button on the settings screen to verify
everything works before adding the extension to your widget host.
