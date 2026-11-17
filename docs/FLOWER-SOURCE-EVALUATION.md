# PikminBee — 花點資料來源評估（2026-11-17）

## 結論

**2026-11-17 需求決策：花點探索以畫面辨識為主，不開發 Overpass／OSM 候選 API 整合，也不移植其他助手程式碼。** 影像辨識原型與待測事項請見 [FLOWER-VISION.md](FLOWER-VISION.md)。

**目前找不到可驗證授權、具官方保證的全球即時 Pikmin Bloom「大花」公開 API。**
因此不會將非公開遊戲後端、社群爬蟲或 OSM 地標直接導入成「已確認大花」。

本功能維持：
- 使用者自行記錄（標示「本人確認」，屬個人記錄，並非官方驗證）
- 由使用者明確匯入自己有權使用的 JSON／GeoJSON（永遠先標示「待確認」）
- 在地圖搜尋附近候選、人工選取花點、重新規劃合法來源的行人道路
- 不會存取遊戲帳號、Cookie、登入憑證，也不自動呼叫遊戲的未公開 API

## 來源比較

| 來源 | 查證依據 | 技術可行性 | 授權／資料品質決定 |
| --- | --- | --- | --- |
| Pikmin Bloom 官方／Wayfarer | 官方 FAQ：Wayspots 可用於大花、蘑菇、明信片；並非每個 Wayspot 都同步到遊戲，也沒有固定入庫時間 | 未核實有可直接取得當前大花座標的公開端點 | **不整合**，不能把 Wayspot 視為已確認大花 |
| OpenStreetMap 與 Overpass | OSM 可查附近一般 POI；資料採 ODbL，公開 Overpass 服務有容量與頻率限制 | 可用於**可能存在的景點**，不是 Pikmin Bloom 大花 | 若另外開發，必須獨立 PR、使用者明確啟用、限量與快取，並標「OSM 未驗證候選」，提供 OSM 署名與 ODbL；**此 PR 不直接連線 Overpass** |
| 玩家共筆手動標記 | 僅當使用者本人有權匯入、確認來源與使用條件時可用 | 本機檔案匯入目前支援花點 JSON／GeoJSON | **允許使用者手動匯入**，不自動將第三方資料包含在 APK |
| LIUXVuse/pikmin-bloom-map | 專案說明包含從 Facebook 社團貼文擷取 GPS、Cookie 工具及社群作者欄位 | 可以研究格式，但不適合直接複製或部署其抓取程式與資料 | **不內建／不爬取／不重新散布**，除非另有明確授權、來源同意與資料品質評估 |
| SmailDot/Pikmin-Bloom-GPS | 開源輔助專案表示從 OSM 查地標做候選，並非實際大花資料 | 可用來驗證「OSM 候選」概念，但非即時官方資料 | **僅參考功能需求**；沒有因此取得其原始程式或資料的再利用許可 |

## 官方限制與風險

- 官方 FAQ 明確說明 Wayspots 與遊戲地圖的出現時間和條件不一致，地標不是即時大花資料。
- 官方支援文件將偽造位置、修改或非官方軟體、未授權使用遊戲後端列為可能違規行為。PikminBee 不保證第三方遊戲接受任何模擬定位，也不提供繞過遊戲安全偵測的機制。
- OSM 的 ODbL 署名、重製／衍生資料庫條款與公開服務使用限制，都需要在**未來若新增 OSM 候選來源**時先處理。
- 社群資料的著作權、張貼者同意、時間效力與正確性需要分開確認；公開 GitHub 來源**不等於**可任意重製／重新散布。

## 暫緩與啟用門檻

在找到新的花點資料來源以前，**保留 UAT 所需的本機候選功能**，不自動收集。

使用者目前已明確不採用「候選地標外部 API」方案，以下條件僅保留為未來若重新提出需求時的審查標準，不是開發排程。任何新 Provider 都需另開 PR，滿足：

1. 來源 API 或檔案授權明確，無需帳號登入 Cookie、未授權遊戲 API 或逆向存取；
2. 資料來源和時間明確顯示，所有未核實資料仍標「待確認」；
3. 可控制開關、服務壅塞與查詢上限，不在背景無限制查詢；
4. 有快取、失敗降級、隱私與授權告知；
5. 獨立單元測試、API／來源協定檢查和 UAT 驗收。

已另開原創的**花朵螢幕像素辨識原型**；但從偵測畫面圖形到準確 GPS 大花位置，仍缺實際截圖測試集與影像對地圖的座標校正，所以**不是可自動巡花的完整功能**。

## 查證連結

- 官方 Wayfarer FAQ：https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3421-faq-about-wayfarer/
- 官方 Ban Appeal／違規說明：https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3044-submitting-a-ban-appeal/
- OSM 著作權與 ODbL：https://www.openstreetmap.org/copyright
- Overpass API：https://wiki.openstreetmap.org/wiki/Overpass_API
- 社群專案（僅作可行性比較）：https://github.com/LIUXVuse/pikmin-bloom-map
- 候選地標專案（僅作可行性比較）：https://github.com/SmailDot/Pikmin-Bloom-GPS
