# phyphox-android AGENTS.md

## Project Overview
Android app for physics experiments using smartphone sensors. Uses Gradle, Kotlin (plugin 2.2.10), and NDK (CMake with FFTW and custom analysis module).

**Package**: `de.rwth_aachen.phyphox`

## Essential Commands

**Build & Test**
```bash
./gradlew test                    # JVM unit tests
./gradlew connectedAndroidTest   # Instrumented tests on device/emulator
```

**Instrumented Tests (T1 suites)**
```bash
tools/t1_instrumented.sh . chrome        # View, graph, accessibility, chrome suites
tools/t1_instrumented.sh . translations  # Language rendering test only (222 of 492s)
tools/t1_instrumented.sh . all           # Both
```

**Screenshot Generation**
```bash
./gradlew assembleScreenshotDebug assembleScreenshotDebugAndroidTest
# Uses Screengrabfile config; locales: cs,de,el,en,es,fr,hi,it,ja,ka,nl,pl,pt,ru,sr,tr,vi,zh-CN,zh-TW
```

**Native Code**
- CMake at `app/src/main/cpp/CMakeLists.txt`
- FFTW in `fftw3/` subdirectory (single-precision enabled)
- Analysis module in `analysis/` subdirectory

## Architecture Notes

**Module Structure**
- Main app: `app/` (single Android library module)
- Submodules: experiments, webinterface (git submodules in `assets/`)
- Bluetooth: custom MQTT 3.1.1 client (`NetworkConnection/Mqtt/MqttClient.java`); Paho dependency removed
- Camera: CameraX 1.4.2 (requires minSDK>=23 for 1.5.0+)
- Analysis: JNI library via CMake → FFTW

**Flavors**
- `regular`: Standard build (default)
- `screenshot`: For automated screenshot generation (no permission requirements)

**Test Organization**
- JVM tests: `app/src/test/java/` (Robolectric, Truth, coroutines-test)
- Instrumented tests: `app/src/androidTest/java/`
  - T1 suites in groups: chrome, translations, permission
  - Golden images in `app/src/androidTest/goldens/`
  - Fixtures from sibling `phyphox-docs/` checkout (same parent dir)

**Key Dependencies**
- Kotlin plugin: 2.2.10
- AGP: 9.2.1
- Compile SDK: 37, Min SDK: 21, Target SDK: 37
- Material: 1.13.0 (1.14.0+ requires minSDK>=23)
- CameraX: 1.4.2 (1.5.0+ requires minSDK>=23)

## Workflow & Conventions

**Branch Strategy**
- `master`: Current published version
- `development`: Active minor development
- `dev-next`: Future large changes converging from feature branches
- `translation` branches: Synced with translation system (often identical to dev/next)

**Contributing Constraints**
- UI changes rare—many teachers use fixed worksheets
- Android/iOS parity essential; new features must port to both platforms
- Translation via external system (not git directly)
- Contact maintainers before major changes

**Code Style**
- Mixed quality (students/researchers without CS backgrounds)
- Contributions welcome for refactoring

## Gotchas & Quirks

1. **Corpus/fixture tests need sibling repos**: `phyphox-docs/corpus`, `fixtures/views`, `fixtures/containers` must exist next to this repo; otherwise tests skip
2. **Gradle up-to-date checks break** with external fixtures—declare as inputs in `app/build.gradle:136-150`
3. **Logcat loss on failure**: Use `tools/t1_ci_run.sh` wrapper to capture logs when tests fail mid-run
4. **Permission tests require two runs**: One revoked, one granted (app restarts between)
5. **Test assertions drop messages by default**: Custom logging config in `app/build.gradle:105-127` preserves assertion messages
6. **Ignore assets pattern** excludes all README.md files to avoid asset collisions

## Setup Prerequisites

- Android SDK with NDK 28.0.13004108
- Submodules: `git submodule update --init --recursive`
- For tests: `phyphox-docs` sibling repo with fixtures/corpus
