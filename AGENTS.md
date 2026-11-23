# AGENTS.md - Developer & Agent Guidelines for CreamTranslator

This document serves as the operational guide and system instruction manual for AI coding agents (Antigravity, Cursor, Claude Code, OpenCode, Codex) working on the **CreamTranslator** repository.

---

## 1. Project Overview & Architecture

**CreamTranslator** is a privacy-first, zero-cloud-cost on-device real-time screen translation tool for Android (specialized for mobile games, visual novels, and manga).

### Key Architectural Components
- **Engine Core** (`com.cream.translator.core`):
  - `ScreenCaptureManager`: Captures screen frames via Android `MediaProjection` VirtualDisplay. Employs non-blocking coroutines and explicit `bitmap.recycle()` calls to prevent native graphic memory leaks.
  - `TextInpainter`: Calculates contrasting text color using pure bitwise RGB luminance (independent of unmocked framework methods) and dynamic font sizing.
- **Service Layer** (`com.cream.translator.service`):
  - `OverlayService`: Foreground service hosting full-screen transparent overlays. Manages translation lifecycle, state machines, and floating control views.
- **Overlay UI** (`com.cream.translator.ui.overlay`):
  - `InPlaceTranslationOverlay`: Custom Android `View` using `StaticLayout` for multiline text alignment within detected text bounding boxes. Consumes `ACTION_DOWN` with `isClickable = true` for reliable touch dispatch.
- **Self-Update Engine** (`com.cream.translator.update`):
  - `UpdateEngine`: Native in-app self-updater querying GitHub Releases API with zero external libraries.
  - Hardened with HTTP 307/308 redirect handling, cleartext HTTP protocol downgrade prevention (HTTPS-only), `.tmp` stream length validation, and semantic version comparison (`isNewerVersion()`).

---

## 2. CI/CD Pipeline & Quality Gate Invariants

The project enforces an automated **Fail-Fast Tiered Verification Model** in GitHub Actions.

### Branch Protection Policy
- **Branch `main` is strictly protected** with `enforce_admins: true`.
- **Direct pushes to `main` are prohibited.**
- Every change must be submitted via a **Pull Request (PR)** targeting `main`.
- Every PR must pass two mandatory required status checks before merging:
  1. `Code Quality & Unit Tests`
  2. `Build Assemble Debug APK`

### Parallel CI Execution (`.github/workflows/ci.yml`)
- Triggers on `pull_request` targeting `main` or `develop`.
- Documentation changes (`*.md`, `docs/**`, `.gitignore`, `LICENSE`) are ignored via `paths-ignore`.
- Active PR runs cancel superseded runs automatically via `concurrency: cancel-in-progress: true`.
- **Parallel Runners**: Both required jobs run concurrently from the exact same second:
  - `Code Quality & Unit Tests`: Mounts a 2GB `tmpfs` RAM disk, checks formatting (Spotless Ktlint 1.2.1), static analysis (Detekt 1.23.6), Android Lint, and unit tests. Publishes inline test results to the PR via `dorny/test-reporter@v1`.
  - `Build Assemble Debug APK`: Mounts a 2GB `tmpfs` RAM disk and runs `./gradlew assembleDebug -x test -x lint` in ~1m30s.

### Automated Continuous Delivery (`.github/workflows/release.yml`)
- Triggers automatically upon squash-merging into `main`.
- Streamlined single job on RAM Disk `tmpfs` (~1m24s):
  - Compiles release APK (`assembleRelease`).
  - Reads monotonic version from Gradle (`printAppVersion`).
  - Generates SHA-256 verification hash: `CreamTranslator-v<version>.apk.sha256`.
  - Publishes official release to GitHub Releases with attached binary and checksum assets.

---

## 3. Versioning Standards

Do **not** hardcode version strings in shell scripts or Kotlin files.

- **Single Source of Truth**: Defined in [gradle.properties](file:///d:/Workspaces/Tools/CreamTranslator/gradle.properties):
  ```properties
  appVersionMajor=1
  appVersionMinor=0
  ```
- **Monotonic Version Code**:
  ```kotlin
  val appVersionCode = (major * 10000) + (minor * 1000) + GITHUB_RUN_NUMBER
  ```
  Strictly monotonically increases with every CI build to avoid Android OS `INSTALL_FAILED_VERSION_DOWNGRADE` errors.
- **Semantic Version Name**:
  ```kotlin
  val appVersionName = "$major.$minor.$GITHUB_RUN_NUMBER"
  ```
- Query version locally/in scripts via Gradle:
  ```bash
  ./gradlew -q printAppVersion
  ```

---

## 4. Coding Standards & Pre-commit Hooks

- **Language**: Kotlin 1.9.x / Java 17.
- **Code Style**: Official Kotlin style enforced by Ktlint (`1.2.1`).
- **Local Hook**: `.githooks/pre-commit` runs `./gradlew spotlessCheck --daemon`.
  - To format code automatically before committing:
    ```bash
    ./gradlew spotlessApply
    ```
- **Unit Testing Guidelines**:
  - Always keep unit test business logic pure Kotlin or mock framework classes.
  - In `app/build.gradle.kts`, `testOptions { unitTests.isReturnDefaultValues = true }` is enabled.
  - Never call unmocked Android framework methods (e.g., `Color.rgb()`) in JVM unit tests; use standard bitwise color math (`(color shr 16) and 0xFF`).

---

## 5. Agent Workflow Cheatsheet

When tasked with implementing features, fixes, or refactors, agents must follow this workflow:

```bash
# 1. Always branch from updated main
git checkout main && git pull origin main
git checkout -b feature/your-feature-name

# 2. Make your code modifications...

# 3. Format and verify quality locally before committing
./gradlew spotlessApply
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest --continue

# 4. Commit using Conventional Commits
git commit -m "feat(module): description of changes"

# 5. Push branch and open a Pull Request
git push -u origin feature/your-feature-name
gh pr create --title "feat(module): description" --body "Summary of changes"

# 6. Verify CI checks pass
gh pr checks <pr-number>

# 7. Squash merge once approved and green
gh pr merge <pr-number> --squash --delete-branch
```
