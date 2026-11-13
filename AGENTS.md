# Yuubin: instructions for coding agents and contributors

Yuubin is an Android app (Kotlin, Jetpack Compose, one `:app` module) that installs and updates a fixed, compiled-in
catalog of the owner's own apps from GitHub Releases. This file holds the conventions that apply to every contributor,
human or AI agent. Machine-specific notes, work status and personal preferences do **not** belong here.

## Build and test

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug   # JVM tests, lint, debug APK
```

On Windows use `.\gradlew.bat`. Run the command locally before committing. A test or tool that was not run is reported as not run.

## Where things are

| Path | Contents |
|---|---|
| `app/src/main/java/io/github/yuubiz/yuubin/core/` | Domain types and pure logic (versions, hashes, catalog entries, status, install state) |
| `app/src/main/java/io/github/yuubiz/yuubin/ui/` | Compose screens and theme |
| `app/src/test/` | JVM unit tests |
| `gradle/libs.versions.toml` | The only place dependency versions are set |
| `docs/` | Background and design notes |

## Engineering rules

- There is no DI framework and no database. Construct dependencies by hand.
- Keep `core/` free of Android classes so it stays testable on the JVM.
- User-visible text lives in `res/values/strings.xml` (English, the default) and `res/values-ja/strings.xml` (Japanese).
  Add both together; lint fails on a missing translation.
- Match the surrounding code. Comments explain a non-obvious why, in English.
- Do not copy source from GPL-licensed projects; this repository is meant to be published under a permissive licence.

## Security rules

- Never install an APK that failed the SHA-256 check, whose package name differs from the catalog entry, or whose signing
  certificate differs from the catalog's pinned SHA-256.
- Keep TLS verification on and cleartext traffic off.
- Never commit a signing key, a keystore or `keystore.properties`.
- Never open, read, print or search `keystore.properties`, `*.jks` or `*.keystore`; check signing only with
  `apksigner verify --print-certs`.

## Commits

- Small, focused commits with an imperative subject line (`Add ...`, `Fix ...`). Explain the reason in the body when it is not obvious.
- Do not push, tag or publish releases unless the maintainer asked for it.
- Do not commit build output, machine paths, personal data or work-status notes.
