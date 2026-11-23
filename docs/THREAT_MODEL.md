# Threat Model & Security Assurance: CreamTranslator

## 1. Security Invariants
- **Zero Remote Exfiltration:** All OCR and translation occurs locally. No screen bitmap, recognized string, or telemetry is transmitted across the network.
- **Anti-Cheat Safe:** Does not inject code, hook into runtime processes (ptrace), or utilize `AccessibilityService`.
- **Permission Minimality:** Requests only `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `SYSTEM_ALERT_WINDOW`, and `INTERNET` (strictly for offline model download).

## 2. Threat Analysis & Mitigations

| Threat Vector | Severity | Mitigation Strategy |
| :--- | :--- | :--- |
| **Protected / DRM Screen Capture (`FLAG_SECURE`)** | Low | Detection of black frame buffers. Display polite user alert rather than crashing. |
| **Touch Hijacking / Overlay Abuse** | Medium | Overlay uses `FLAG_NOT_FOCUSABLE`. Interactive areas are restricted strictly to the floating pill and in-place translated plates. |
| **Model Tampering** | Medium | Language packs are managed and checksummed through Google Play Services ML Kit infrastructure. |
