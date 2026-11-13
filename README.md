# Yuubin

Yuubin is a small Android app that installs and updates a fixed set of my own apps from their GitHub Releases. The list of apps is compiled into Yuubin. Each download is checked against the release's SHA-256 and the expected signing certificate before it is handed to Android's package installer.

It is not a general app store. Adding an app means building a new Yuubin release.

## Build

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On Windows use `.\gradlew.bat`. This needs a JDK 17 or newer and the Android SDK (API 37). The debug APK is written to `app/build/outputs/apk/debug/`, and installs next to a release build as `io.github.yuubiz.yuubin.debug`.

A release build reads its signing key from a git-ignored `keystore.properties` in the repository root; copy `keystore.properties.example` and fill it in. Without that file only debug builds, tests and lint work, and `assembleRelease` stops with an error.

## Releases

Releases are signed with the owner's key. Do not install a Yuubin APK signed by anyone else.
