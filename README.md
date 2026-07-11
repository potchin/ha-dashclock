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

The project intentionally ships without a checked-in Gradle wrapper jar
(binary blob), and instead builds inside a Podman container that has JDK 17 +
system Gradle 8.9 + the Android SDK preinstalled.

```sh
# One-off: build the build-environment image
podman build -t ha-dashclock-build container/

# Build a debug APK (output: app/build/outputs/apk/debug/app-debug.apk)
bash container/build.sh

# Build a release APK (self-signed with the debug keystore - see below)
bash container/build.sh assembleRelease
```

`container/build.sh` bind-mounts the project into the container and runs
`gradle <task>` there, so the resulting APK appears directly under
`app/build/outputs/apk/` on your host machine.

If you'd rather use Android Studio, that works too - just open the project
directory; Studio will offer to generate a Gradle wrapper for you
automatically on import.

### Signing

`buildTypes.release` reuses the auto-generated debug keystore
(`signingConfigs.getByName("debug")`), so `assembleRelease` produces an
installable, self-signed APK with no extra keystore setup - appropriate for a
personal sideload app. If you want a stable signing identity across
reinstalls/machines, add your own `signingConfigs.release { ... }` block.

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
