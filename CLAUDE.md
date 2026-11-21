# blossom — working notes

- Plan: `docs/plans/2026-11-21-blossom.md` (tick as you go). State: `docs/progress.md`.
- JDK 21. Android SDK path goes in `local.properties` (`sdk.dir=...`, gitignored).

## Commands
- Quick check (run first every session): `./gradlew desktopTest`
- Full check: `./gradlew check`
- One module: `./gradlew :core:designsystem:desktopTest`
- One test: `./gradlew :core:model:desktopTest --tests "*TrackIdTest*"`
- Live-network tests (classes named `*LiveTest`, skipped by default): add `-PliveTests=true`
- Snapshots: record `./gradlew :<module>:recordRoborazziDesktop`, verify `./gradlew :<module>:verifyRoborazziDesktop`
  (goldens live in `<module>/src/desktopTest/snapshots/`)
- Run desktop: `./gradlew :composeApp:run`
- APK: `./gradlew :androidApp:assembleDebug`
- Desktop app image: `./gradlew :composeApp:createDistributable`

## Rules
- Every new source file starts with `// SPDX-License-Identifier: AGPL-3.0-or-later`.
- Copied upstream files keep their header and add `// Modified for blossom, <year>.` below it.
- Never commit `secrets.properties`. Stage specific paths, never `git add -A`.
- UI strings live in `composeResources/values/strings.xml`.
