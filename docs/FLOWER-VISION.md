# PikminBee — 花朵畫面辨識原型（待 UAT）

## 需求決策

只研究**使用者明確授權的畫面辨識**，不整合 Overpass／OpenStreetMap 候選花點搜尋，也不接取非公開 Pikmin Bloom API。這個 PR 完全重新實作影像處理，**未複製其他未確認授權專案的原始碼**。

## 已實作

1. 在「尋花」頁籤展開 **畫面花朵辨識（實驗功能）**。
2. 可使用 Android 文件選擇器挑選自己的 PNG/JPEG 等圖片並辨識；圖片只做界限內縮圖解碼，最多約 480×850 像素，分析結束後不寫入檔案。
3. 可按 **授權畫面辨識**，由 Android 顯示 MediaProjection 系統同意對話框，選擇允許分享的畫面範圍；每次重新啟動擷取都要重新取得同意。
4. 獨立 `mediaProjection` 類型的前景服務啟動後，每約 3 秒對最新畫面進行**只讀**辨識；系統通知和 App 均可立即停止。
5. 會將畫面轉為短暫存於記憶體的縮圖像素，以原創 HSV 色彩門檻、連通區域、面積／填充率／長寬比等方式辨識**彩色、部分帶暖色中心的淺色花朵候選形狀**。
6. 顯示候選個數、相對畫面位置及影像特徵分數。**分數不是辨識準確率**。不把候選點當成經緯度，也**不會自動加入尋花路徑**。
7. 偵測接近全黑的畫面時，會顯示空白或內容受保護的提醒。**不繞過 FLAG_SECURE／遊戲防擷取機制**。
8. 畫面原始像素、截圖、MediaProjection 同意權杖、遊戲內帳號都不會持久化、上傳或寫入 LAN API。停止時釋放 VirtualDisplay、ImageReader 及影像權限。

## 與巡花助手的區別

巡花助手的畫面掃描架構可以作為研究靈感，但它的原始碼授權未核實，**本功能從頭編寫**。同時不引入它的 Overpass 地標候選搜尋、定位校正、遊戲畫面偵測規避、觸控自動化或 Health Connect 步數注入。

目前的畫面辨識是**原型**：沒有遊戲真實截圖、沒有人工標記測試集，就不能聲稱對 Pikmin Bloom 大花達到可用準確率。單元測試以程式生成的彩色／淺色圓形、草地、微小雜訊、黑畫面及 UI 干擾進行驗證，只能證明演算法的基本行為。

## 下一個技術階段（不能默認已完成）

- **取得明確允許且未受保護的測試截圖**，建立人工標註集，計算 precision／recall，調整白色花朵、各種品種、光影、視角及不同螢幕尺寸。
- **地圖像素 → 世界座標校正** 是獨立功能。需要基於使用者提供的明確地圖對應點和比例尺推算，並顯示估計誤差；在有可測誤差之前，禁止自動將花朵像素寫成 GPS 路點。
- 用手機實際測試 MediaProjection 的應用程式分享、全螢幕分享、旋轉後 resize、停止權杖、無畫面／黑畫面和 Android 14+ 新權限。
- 只在整合 UAT 階段產生一次測試 APK，不因單項 TODO 重複打包。
- Pikmin Bloom 及 Android 的內容擷取限制必須尊重；若遊戲封鎖擷取，停止辨識而不是規避保護。

## Android 平台依據

- MediaProjection：每次擷取會話必須取得使用者同意，每個權杖只能建立一次 VirtualDisplay，啟動前景服務後才能取得 MediaProjection：
  https://developer.android.com/media/grow/media-projection
- Android 前景服務類型 `mediaProjection`：
  https://developer.android.com/develop/background-work/services/fgs/service-types

**目前狀態：Android 原始碼＋本機單元測試；POCO UAT 尚未執行，不能宣稱實機畫面可辨識。**
