# CreamTranslator

> High-performance, zero-cost, on-device Android screen translator engineered specifically for mobile gaming and foreign language content.

## Key Features
- **100% On-Device & Free Forever:** Powered by Google ML Kit On-Device OCR and offline English <-> Vietnamese translation. Zero API keys, zero token fees, zero subscription costs.
- **Immune to Game Anti-Cheat Bans:** Completely avoids `AccessibilityService`. Uses standard, non-intrusive `MediaProjection` and floating overlays.
- **In-Place Game Text Replacement:** Translates foreign game dialogue directly inside native text boxes with automatic background color sampling (smart inpainting) and dynamic font sizing.
- **Tap-to-Toggle:** Tap any translated dialogue to instantly flip between original and translated text.
- **Region Drag Translate:** Drag a selection rectangle over specific dialogue boxes, quest logs, or subtitles.

## Enterprise DevOps & Quality Assurance
- **Continuous Integration:** GitHub Actions (`.github/workflows/ci.yml`) runs Spotless, Ktlint, Detekt static analysis, and automated unit test suites on every push and pull request.
- **Git Hooks:** Pre-commit hooks (`.githooks/pre-commit`) ensure zero-defect code commits.
- **Architecture Documentation:** Formal Architecture Decision Records (ADRs) available in `docs/adr/`.

## Setup & Building
```bash
# Clone the repository
git clone https://github.com/cream/CreamTranslator.git
cd CreamTranslator

# Configure git hooks
git config core.hooksPath .githooks

# Run static analysis and lint
./gradlew spotlessCheck detekt lintDebug

# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```
