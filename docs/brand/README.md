# Mogulei V1 — visual assets for review

These are editable, hand-authored geometric vector drafts developed from the owner-approved V1 visual direction on 2026-11-26. They are stored under docs deliberately, outside Android and Web runtime sources. No trademark clearance, app rebrand, Play listing, or production release is implied.

- [Colour mark](mogulei-mark.svg), [lockup](mogulei-lockup.svg), [single-colour mark](mogulei-monochrome.svg), [favicon](favicon.svg).
- android/res/ contains review-only Android adaptive foreground, background, API 26/33 adaptive resources, themed monochrome and 24dp notification silhouette.
- [Brand design and migration](../MOGULEI-BRAND.md) and [Issue #169](https://github.com/Naos2w/PikminBee/issues/169) remain the acceptance authority.

Palette: forest #24493E, terracotta #C17B55, cream #F2EBDD, ink #202824; secondary map green #8EAA72 is a proposed accent. The logo paths are drawn directly, not copied from a third-party image, font file or game icon. The lockup uses a system font as a review placeholder; actual wordmark outlines/font licence must be decided before distribution. No font binary is bundled. The favicon and monochrome/notification variants simplify the folded map to retain recognition at small sizes. Android foreground uses scaled artwork within the 108dp viewport; launcher-mask previews and physical device tests remain required.

Validation on 2026-11-26: all four SVG files parsed as XML; all six Android XML files parsed; SDK build-tools 36.0.0 aapt2 compiled and linked the isolated draft resources against the installed android-37.0/android.jar (resource-only temporary package, minSdk 28, targetSdk 36). Resource table contains the background, three drawables and API-qualified launcher resource. Temporary output removed. This verifies resource syntax/linking, not visual rendering, mask behaviour, accessibility, trademark clearance, or actual device integration.

Before runtime integration: owner review of the specific vector draft and font/wordmark, official near-mark/figurative trademark review, compliant product scope, launcher mask/contrast checks and device appearance review. Do not copy these resources into app/src/main or publish them as approved store assets while #169 B1/B2 and G1 are unresolved.

No applicationId, manifest, Gradle settings, stored data, signing key, Web page or app version is changed by this draft.
