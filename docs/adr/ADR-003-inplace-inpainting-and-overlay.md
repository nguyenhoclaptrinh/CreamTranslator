# ADR-003: In-Place Text Replacement via Smart Inpainting and Dynamic Sizing

## Status
Accepted

## Context
In mobile games, traditional subtitle cards or floating dialogue boxes cover large portions of the screen and break immersion. Gamers require in-place translation that seamlessly covers original foreign dialogue within its native speech bubble or text box.

## Decision
We implement a smart inpainting canvas renderer (`InPlaceTranslationOverlay`):
1. Samples edge border pixels of detected text bounding boxes to calculate the median background color.
2. Draws a rounded inpainting plate matching the game UI background.
3. Automatically computes dynamic font size based on bounding box dimensions and character count.
4. Allows touch toggling to flip between original and translated text.

## Consequences
### Positive
- High visual immersion matching the game aesthetic.
- Instant user verification of translation accuracy by tapping on any text block to view the original.
