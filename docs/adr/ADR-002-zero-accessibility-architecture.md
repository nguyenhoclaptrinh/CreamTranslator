# ADR-002: Strict Exclusion of AccessibilityService for Anti-Cheat Compatibility

## Status
Accepted

## Context
Many mobile screen translators use Android's `AccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`) to inspect view hierarchies and scrape text. However, in mobile gaming and financial environments:
1. Anti-cheat engines (Tencent ACE, miHoYo HoYoverse, NetEase, EasyAntiCheat) detect active Accessibility Services as malicious clickers, bots, or memory inspection tools, resulting in immediate game bans.
2. Banking and enterprise security applications flag accessibility permissions as high-risk and terminate execution.

## Decision
We strictly exclude `AccessibilityService`. Instead, we rely exclusively on:
1. Standard Android `MediaProjection` initiated by explicit user approval.
2. `SYSTEM_ALERT_WINDOW` configured with `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCH_MODAL` so underlying game touch inputs are unaffected.

## Consequences
### Positive
- 100% compliance with mobile game anti-cheat systems. Zero risk of account bans.
- Banking and protected enterprise applications will not flag the device as compromised.

### Negative / Trade-offs
- Screen translation is initiated via on-demand triggers (tap floating button or region drag) rather than passive automated view hierarchy scraping.
