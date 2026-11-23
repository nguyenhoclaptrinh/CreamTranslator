# ADR-001: Selection of On-Device ML Kit Over Cloud Translation APIs

## Status
Accepted

## Context
The user requires a one-time purchase Android screen translation tool with:
1. Zero ongoing subscription or API usage fees.
2. Complete immunity to third-party cloud translation API billing changes or service termination.
3. Offline execution for privacy and network independence during mobile gaming.

## Decision
We adopt **Google ML Kit On-Device Text Recognition v2** and **Google ML Kit On-Device Translation** as the foundational AI processing stack.
- Language models (English <-> Vietnamese, ~30MB) are downloaded on-demand once and stored locally.
- Inference occurs entirely via local Android neural/CPU routines.

## Consequences
### Positive
- Zero recurring costs to the user or developer.
- Runs without internet connectivity.
- Zero network latency for translation round-trips.
- Guarantees total user privacy (screen content never leaves the device).

### Negative / Trade-offs
- Initial one-time download of language pack (~30MB) requires device storage.
- Translation quality is optimized for general dialogue rather than domain-specific colloquial slang.
