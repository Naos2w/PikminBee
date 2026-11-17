# PikminBee — 花點資料庫與附近搜尋（開發版）

## 資料真實性（重要）

此功能**不具備 Pikmin Bloom 官方即時大花資料**，也不會把 OpenStreetMap 地標或所有 Ingress/Wayspot 地點認作遊戲大花。官方 Wayfarer 說明指出：Wayspot 可用於大花、蘑菇、明信片，但**不是每個 Wayspot 都會在遊戲顯示**。

「本人確認」只是使用者自行核對過該位置的**個人紀錄**，不是官方驗證；匯入的資料一律是「匯入候選／未確認」。未提供官方端點，也不會嘗試連線到非公開遊戲 API 或自動擷取遊戲資料。

參考：
- 官方 Wayfarer FAQ：https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/3421-faq-about-niantic-wayfarer/
- 社群標記概念：https://github.com/Tr6nt0/bloom-scout-plugin
- 玩家自製地點 JSON 格式參考：https://github.com/LIUXVuse/pikmin-bloom-map

以上僅供需求和資料格式研究。**PikminBee 未內建、抓取、複製或重新散佈上述第三方資料或程式碼**；使用者自行確認有權匯入自己的資料。

## 操作

1. 在 **尋花** 頁籤點地圖選位置（此時會沿用既有功能新增一個尋花路點）。
2. 展開 **尋找附近花點**，輸入名稱，按「記錄／確認此花點」，建立本人核對的位置紀錄。
3. 可使用 Android 檔案選擇器匯入**自行擁有、明確標記為花點**的 JSON 或 GeoJSON。檔案先顯示筆數與略過數，經使用者確認後才存入。
4. 選 500 m、2 km 或 5 km 範圍，依**直線距離**排序，亦可篩選「僅本人確認」。
5. 逐一加入或批次選取附近花點到「尋花路線」草稿（上限 14 點，為可選循環保留路點空間）。
6. 路線不會自動開始；使用者按原本的「開始行走」後，才會呼叫 Valhalla 規劃**行人道路**。無法規劃時不會改走直線。
7. 可將花點庫匯出為 GeoJSON，以供個人備份。匯出內容僅有名稱、花點經緯度、種類和個人來源標記，**不含使用者帳號、社群作者或貼文**。

## 支援格式

### 玩家自行記錄的 JSON 陣列

```json
[
  {"name":"公園入口","type":"花點","lat":25.033,"lng":121.5654},
  {"name":"公園步道","type":"big_flower","lat":25.0335,"lon":121.5658}
]
```

僅接收 `type/kind/category` 明確為 `花點`、`大花`、`大花點`、`flower`、`flower_spot`、`big_flower` 或 `bigflower` 的資料；`菇點` 或未分類 OSM 地標不會自動視作大花。非花點記錄會顯示略過筆數。

### GeoJSON FeatureCollection

```json
{
  "type":"FeatureCollection",
  "features":[{
    "type":"Feature",
    "properties":{"name":"公園入口","kind":"big_flower"},
    "geometry":{"type":"Point","coordinates":[121.5654,25.033]}
  }]
}
```

僅解析有明確花點類型的 `Point`，GeoJSON 座標順序為 **經度、緯度**。不支援官方遊戲狀態、開花階段、花蜜即時資訊。

## 安全與限制

- 匯入 **1 MB、1,000 筆**內；資料以 Android Storage Access Framework 由使用者挑選。不得自動擷取網頁或社群帳號 Cookie。
- 距離 25 公尺內的候選視為重複，匯入不會覆蓋本人已核對的紀錄。
- 最多本機儲存 1,000 筆，資料在 App 專屬 SharedPreferences，無雲端帳號／同步；清除 App 資料會失去紀錄。
- GeoJSON 備份重新匯入也一律標記**待確認**，不會因外部資料寫 `verified` 欄位就產生官方認證假象。
- 離線可搜尋已儲存花點；顯示底圖與規劃步行道路仍需各自的網路服務。
- 後續若找到合法、維護中且具許可的花點資料服務，可**獨立開 PR** 研究接入，不會直接硬塞到本功能。

## UAT（到時集中產生一次 APK）

- [ ] POCO：手動記錄、附近篩選、標記本人確認、移除花點。
- [ ] POCO：匯入混合花點／菇點 JSON，檢查僅花點保留且清楚標未確認。
- [ ] POCO：匯入及匯出 GeoJSON，檔案可由第三方工具開啟。
- [ ] POCO：500 m、2 km、5 km 距離顯示、去重和最多 14 點批次加入。
- [ ] POCO：Google Maps／OpenFreeMap 的花點標記和深色模式。
- [ ] POCO：加入候選點不會自動啟動 GPS，正式路線仍須人工按開始。
