# Mogulei — 品牌識別與遷移設計規格

決策：2026-11-26，擁有者同意先採用 Mogulei。現階段為品牌規劃及查核，不代表完成商標權利審查。#169 是品牌與產品合規唯一任務帳本；本文件是設計、遷移與驗收規格，發行仍由 #168 與 [COMMERCIAL-READINESS.md](COMMERCIAL-READINESS.md) 管控。

## 1. 品牌提案（待視覺定稿）

| 元素 | 建議 |
| --- | --- |
| 文字品牌 | Mogulei（正式大小寫），取自蘑菇諧音 |
| 英文標語 | Explore Your Own Way. |
| 繁中標語 | 探索屬於自己的路。 |
| 核心價值 | 地圖、個人收藏、GPX 與合法授權測試；精簡、隱私優先 |
| Logo | 自創蘑菇與定位圖釘幾何輪廓；避免仿用第三方角色、遊戲蘑菇、圖示、配色或素材 |
| 色彩提案 | 森林綠 #24493E、陶土橘 #C17B55、奶油白 #F2EBDD、深墨綠 #202824；以實際明暗對比及地圖可讀性調整 |
| UI | 保留 map-first、輕量浮動面板、現有操作流程；品牌調整不重新設計所有功能 |
| 文案 | 僅描述真實保留且合規的功能；不得使用第三方品牌引流、暗示授權或宣傳作弊、反偵測及健康資料偽造 |

交付物：原創向量圖形與字標（含單色/反白），Android adaptive icon 前景/背景/monochrome、通知單色圖示、Web/favicon、品牌使用說明、Google Play 512×512 icon 與 1024×500 feature graphic、繁中/英文實際產品截圖及描述。Google Play 正式素材到 #178 再依真實可用功能完成；Logo 草案由擁有者審閱後定稿。依官方 Android adaptive icon 規格使用 108dp 圖層及中央安全區，實測圓形/方圓形裁切與小尺寸；使用授權明確字型、圖形和素材，記錄來源。配色不得掩蓋警告、權限、選取及狀態，暗/亮色文字對比依可及性要求測試。首版不另做重型動畫或廣告版位。

## 2. 權利清查與可逆決策

2026-11-26 初步公開網頁/商店索引搜尋 Mogulei 未取得明確完全同名項目；不等同官方資料庫檢索結果，不宣告可註冊或可購買網域。近似名稱 MOGU 有第三方公開資料索引：
- 台灣註冊編號 02038193，案號 107070408，第 9 類手機應用程式，記載專用期限 2030-01-31：https://www.findcompany.com.tw/trademark/02038193_107070408
- 台灣註冊編號 02036831，案號 107070413，第 42 類軟體相關服務，記載專用期限 2030-01-15：https://www.findcompany.com.tw/trademark/02036831_107070413
以上是待用官方系統核實的近似線索，不作侵權結論。

建議台灣首發（市場未正式鎖定）；若之後開放日本、其他市場，增加各地官方資料庫清查。以 MOGULEI、MOGU LEI、MOGU、MOGULEY、MOGULAY 與對應近似音/文字、圖形檢索；第 9 類可下載 App 為首要，第 42 類按實際提供的軟體/線上服務評估；其他類別依實際商品服務決定，不只憑類別號排除衝突。每項保留地區、日期、查詢式、案號、權利人、指定商品服務、狀態及判斷依據。圖案完成後再做近似圖形檢索。商店與網域（mogulei.com、mogulei.app、mogulei.tw 為待查候選）亦核對，不自行註冊/付款；網域或帳號未確認可用不得宣稱已取得。

官方依據：
- TIPO 官方文字/圖形近似檢索：https://cloud.tipo.gov.tw/S282/S282WV1/
- TIPO 自行檢索方法：https://www.tipo.gov.tw/tw/trademarks/568-7691.html
- TIPO 第9類：https://www.tipo.gov.tw/tw/trademarks/979-19054.html
- TIPO 第42類：https://www.tipo.gov.tw/tw/trademarks/979-19021.html
- WIPO 全球資料庫（仍需各地官方登記核對）：https://www.wipo.int/en/web/global-brand-database/index
- Android applicationId：https://developer.android.com/build/configure-app-module
- Android adaptive icons：https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
- Google Play preview assets：https://support.google.com/googleplay/android-developer/answer/9866151

待官方文字與圖形檢索、品牌可用性與擁有者審閱後才正式鎖定。若有未解除重大近似風險，改候選名或尋求商標專業意見；保留現有 DEV/UAT 身份，不急於改名。品牌查核與實際產品功能合規獨立，禁止用品牌變更隱瞞違規用途。

## 3. 程式與資料遷移

2026-11-26 來源基準 develop e306eceafd24ce0936f5ac0403574aa980a84d5f。現有 app/build.gradle namespace 與 DEV ID 為 com.naos.pikminbee；UAT 為 com.naos.pikminbee.uat，Gradle appDisplayName placeholder；AndroidManifest.xml 使用 drawable/ic_pikminbee，allowBackup=false。PR #167 提案的 com.naos.pikminbee.prod 仍為未上架的建置準備，正式 ID 候選 com.naos.mogulei，#177 最終確認。Android 官方指出上架後變更 applicationId 會成為不同 App。

| 階段 | 變更範圍 | 保護及驗收 |
| --- | --- | --- |
| 0.28.1 #169／#171／#170 | 品牌權利審查、實際合規功能盤點及清理、同意/隱私規格；待核准的原創 Logo /字標及可逆顯示名稱提案 | 未過 G1 不做公開商用發行；不更改 applicationId、簽章、版本碼或資料格式；不擴充非合規用途。 |
| 0.29.1 #174／#150／#172 | 盤點安全的收藏、GPX 路線及設定資料、實際匯出還原/驗收；如 G1 與設計已核准，再做品牌外觀整合 | 明確同意的本機檔案匯出與匯入預覽；資料型別/大小/版本驗證、重複處理、錯誤不覆寫，回復或取消；不匯出/還原授權 token、健康紀錄、私有金鑰或執行中服務狀態。既有非合規資料只保留安全的歷史處理，不啟用其功能。 |
| 0.30.1 #175／#176 | 官網、favicon、說明頁、條款/隱私、品牌文案；正式名稱需先清查 | 合規才整合測試廣告；網域/付費由擁有者決策。 |
| 0.31.1 #177／#178／#179 | 鎖定正式 ID、Play App Signing/AAB、官方素材及封閉測試 | 新 ID 為獨立 App；舊 UAT 和新正式包並存，實際驗證手動匯入及資料完整性，重新取得必要權限與明確同意。禁止自動轉移私有資料、直接卸載舊 App 或未核准刪除資料。 |
| 1.0.0 #168 | 全部上架前閘門通過、擁有者批准才正式發布 | 不提前購買、上架、發佈或啟用正式廣告；AdMob 另有核准。 |

現行檔案盤點：app/build.gradle、settings.gradle、gradle.properties、AndroidManifest.xml、res/drawable/ic_pikminbee.xml、res/values 與 values-night styles、ui/BeeLogoPolicy.kt、ui/FloatingControls.kt、ui/BeeSettingsScreen.kt、data/AppPreferences.kt、data/InstalledAppVersion.kt、gpx/GpxCodec.kt、assets/lan/index.html、controller.js、.github/workflows/android.yml、scripts/uat_release_filename.py、scripts/bump_version.py 及其測試。進一步區分 UI 字串/素材、內部 Kotlin namespace、持久化鍵值、GPX 相容資料、通知、外部 API 限制、版本/發行檔名、舊歷史連結，不全域字串取代。

品牌標示採集中字串/資源；內部程式命名與正式 package ID 遷移另經測試，不因視覺更名強制全庫 namespace 重構。舊 DEV/UAT ID、簽章及資料鍵維持，實際覆蓋更新須驗證現有簽章/版本；正式 App 不假設繼承舊權限、私有資料或 Health Connect。保留 Git 歷史及 Issues/Projects；Repo 改名不是正式上架必要條件，若擁有者核准再專門驗證 remote URL、CI、PR、工具 Project 綁定、README 與外部連結，禁止未核准重寫歷史。

驗收依 #169、#174、#177、#178、#179：品牌素材審閱及權利查核、畫面/字串掃描及測試、明暗對比、實機小圖示、既有 UAT 升級與安全新包匯入、資料筆數與內容一致、錯誤輸入及取消、實際 AAB/版本/簽章、商店資料一致。沒有證據不勾選 Gate 或關閉 Issue。此規格不是已完成 Logo、商標申請或已執行 App 遷移。
