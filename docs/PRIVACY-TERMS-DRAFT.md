# Commercial privacy and terms — review draft (#170)

**Status: draft only, not approved for publication or user acceptance.** Source audit: remote develop `f0eb7ce03fdfd8d5454d4fbdc1758bcf53c1b2d6` (PR #186); local draft base `d8170aa3e4ae89d3efe50aad5be97900f32edb5a`. #168 is the release gate; #170 is the task record. This document describes observed source behavior and proposed contractual disclosures, not a completed legal review or a claim that commercial functionality has been approved.

## Source data inventory

| Data / action | Observed storage or destination | Trigger and retention / limitations |
| --- | --- | --- |
| Saved places, names, coordinates, metadata, route library and drafts | Android private SharedPreferences, `AppPreferences.kt` | User saves/edits; persisted until deletion or app-data removal; historical data must not be silently deleted. No claim of encrypted storage. |
| Place and time-zone lookup | HTTPS Photon (photon.komoot.io), TimeAPI (timeapi.io); private `pikminbee_place_lookup` preferences | Selected coordinates to 6 decimals on lookup; physical coordinate can also reach TimeAPI from the explicitly authorised LAN real-location path. Cache keys rounded to 4 decimals; 30-day validity window, not guaranteed physical eviction. Third-party retention unknown. |
| Road routing | HTTPS valhalla1.openstreetmap.de | Ordered selected waypoints sent in JSON for requested route; local in-memory route cache, up to eight entries, one-hour reuse window. Service retention / commercial capacity unresolved (#173). |
| Map rendering | OpenFreeMap style/tile requests in Android and browser; optional Google Maps Android SDK | Map view implies area/tile requests and network metadata. SDK-specific telemetry, provider retention and commercial terms require #173 review; no blanket assertion that no location leaves the device. |
| GPX | User-selected local document via Android file interface | Explicit import/export; exported coordinates may be shared with another app or storage chosen by the user. Verify actual import/export and deletion behavior (#174). |
| LAN control | Paired browser, local Wi-Fi HTTP | Route and current test state visible to authorised session; real location separate affirmative session choice. Memory-only session token, renewal rotates grant; plaintext transport is not commercial-safe (#172). |
| Location permissions | Android location APIs and user-started foreground services | Precise/approximate location and location FGS remain; notifications/overlay optional; revocation and no-permission behavior require device tests (#171/#172). |
| Historical health-related local records | Legacy preferences/ledger retained after #185 | Health Connect SDK, read/write and physical step tracking removed from develop; do not claim historical data erased. Export/migration/deletion decisions require #174. |
| Backup / transfer | Manifest allowBackup=false; data extraction rules exclude SharedPreferences from cloud backup and device transfer | Source configuration, not proof for all OEM or file paths. User-directed exports remain possible. |
| Ads/account/backend | No AdMob direct dependency in audited source | Proposed ads are not implemented or authorised; re-audit actual SDK/data flows and obtain required separate consent before #176. |

Source references: `AppPreferences.kt` (private preferences, setup flag, route storage); `PlaceLookup.kt` (coordinate requests, cache); `MainActivity.kt` (save/refresh lookup calls); `ValhallaPlanner.kt` (routing endpoints); `LanController.kt` (real-location token and time-zone request); `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml`, `docs/LAN-CONTROL.md`, `docs/THIRD-PARTY.md`. The complete SDK and runtime network audit remains pending. No real coordinates, secrets or personal data are included here.

## Proposed first-use disclosures (Traditional Chinese review text)

用途：本工具提供地圖瀏覽、收藏、GPX 與經授權的定位測試。僅可在你有權使用的裝置、資料及測試環境中操作，不得影響第三方服務正常運作或偽造健康紀錄。服務不保證第三方地圖或路由的正確性與可用性；本條款不排除依法不得排除的權利及責任。

隱私：收藏和路線主要儲存在本機。當你使用線上地圖、地點查詢或道路規劃時，相關地圖區域、選取座標或路點及網路請求資訊可能傳送至提供服務的第三方。配對的裝置可能接收你明確授權分享的資料；真實位置分享需另外同意，並可撤銷。現階段未加密的 LAN 功能不得視為已通過商用安全驗收。匯出的檔案可能含有座標，請確認儲存位置及接收者。拒絕條款可離開，不會因此清除舊資料。

**Publication blockers:** owner-approved legal entity/contact channel, jurisdiction, rights request/deletion procedure, actual data retention and third-party terms, final product scope, English translation/review, final legal text and revision IDs. Do not turn the draft into an acceptance prompt or publish it until these are resolved; draft does not certify Google Play compliance.

## Versioned consent contract

- Terms and privacy revisions are distinct identifiers tied to the exact approved texts; no default acceptance, no upgrade of the legacy `setup_completed` flag into a receipt.
- An explicit confirmation stores the exact pair and a positive local acceptance timestamp together, atomically; persistence failure must not unlock functionality. On restart, only a valid exact matching receipt is accepted; a missing, corrupt or mismatched record requires review.
- Refusal does not create a receipt or request permissions; user may leave. Do not delete existing saved data. Withdrawal and major changes must require fresh confirmation before further covered functionality; a previously stored receipt must not be reused after withdrawal.
- Local review and rejection must work offline; no server call or account is required to check the receipt. Store only receipt versions and timestamp, not location, device ID or a new analytics identifier.
- Source foundation: `LegalConsentPolicy` and `LegalConsentReceiptCodec` define the exact revision check and a single JSON record (schema 1, terms, privacy, acceptedAtMillis). Missing or malformed records, unsupported schema, invalid types, invalid timestamp and stale document revisions require review. This codec does not yet persist data, implement withdrawal or enforce the runtime entry-point gate; the atomic storage adapter and failure handling are still required. No approved document revision or default consent is embedded.
- Terms/privacy acceptance is not permission consent, LAN location-sharing consent or advertising consent. No new permission, sharing grant or network traffic is triggered by the receipt alone.
- Integrate into the existing #144 first-use flow only after owner review, before any sensitive feature or external network initialisation requiring the disclosure. Audit all entry points (including service restoration / LAN) rather than gating only the main Compose page.
- Test matrix: new install, old wizard-complete install without receipt, matching restart, terms-only or privacy-only revision change, refusal, withdrawal, corrupt/missing record, persistence failure, offline and upgrade preserving existing data. Device tests and approved UI/text remain separate from unit tests.
