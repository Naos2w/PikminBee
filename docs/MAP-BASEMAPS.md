# PikminBee — 免 Google API Key 地圖策略

## 決策

**地圖必須在沒有 Google API Key 的情況下可操作**。OpenFreeMap 是預設且唯一必需的地圖提供者；Google Maps Android SDK 只是使用者／開發者明確提供合法受限 Key 後的選配。

- 未提供 Google Maps API Key：不顯示 Google Maps 選項；舊偏好若留有 Google Maps，啟動時自動使用 OpenFreeMap，不顯示空白／錯誤地圖。
- 已提供 Key：保留官方 Google Maps SDK 選項，不在 APK 或文件中硬編碼私密資訊。
- OpenFreeMap 使用原本的 MapLibre Android SDK，選擇「跟隨主題／Liberty／Bright／Positron／Dark」公開圖層。跟隨主題預設深色為 Dark、淺色為 Liberty；明確選擇的底圖不會因 App 主題改變而被覆蓋。
- 底圖選擇只影響**顯示**，不得啟動或修改瞬移、路線、行走或 GPS 模擬定位。保存目前偏好，避免每次啟動重選。
- 無 Google API Key **不等於**不用網路：公開向量圖磚仍需網路連線，官方／公開服務不保證 100% uptime，也沒有離線快取承諾。

## 為何不直接移植 LocWarp「Google 圖磚（測試）」

LocWarp 宣稱該實驗圖層不要求使用者提供 API Key，但 **Google Maps Platform 官方 FAQ** 明確禁止透過 Google Maps Platform 之外的接口直接讀取 Google 圖磚／衛星影像，未獲授權的方式可能被封鎖。因此 PikminBee **不使用 Google 非官方圖磚 URL、不繞過金鑰，亦不從 LocWarp 複製該圖層**。

- LocWarp：https://github.com/keezxc1223/locwarp
- Google FAQ：https://developers.google.com/maps/faq
- 官方 Map Tiles API：https://developers.google.com/maps/documentation/tile/get-api-key

## 公開圖資及使用條款

- OpenFreeMap：https://openfreemap.org/quick_start/ ，免註冊／免 API Key，使用 MapLibre 時需保留來源標示。公開圖層 URL 只使用官方規格 \`https://tiles.openfreemap.org/styles/{liberty|bright|positron|dark}\`。
- OpenFreeMap 條款：https://openfreemap.org/tos/ ，請遵守公共服務合理使用與下載限制。
- 台灣國土測繪中心提供免申請的 WMS／WMTS 底圖，**未在本 PR 接入**；日後若評估加入，須核對其介接格式、標示來源、適用地域與使用條款：https://maps.nlsc.gov.tw/S09SOA/ 。

## 仍需合併 UAT APK 驗證

- [ ] 沒有 Key 的新安裝：正常顯示 OpenFreeMap，沒有無法使用的 Google Maps 按鈕。
- [ ] 模擬舊版偏好保留 Google Maps：在無 Key 版本回退 OpenFreeMap，不顯示空白地圖。
- [ ] 確認 Liberty／Bright／Positron／Dark、跟隨主題切換正常，標記／已規劃道路／路線視野維持。
- [ ] POCO HyperOS：實際網路環境、地圖縮放、深淺模式、旋轉螢幕、圖磚來源標示、服務斷線的行為。
- [ ] 有合法受限 Google Maps Android SDK Key 的測試版本，再另外驗證可選的 Google Maps；無 Key 不阻塞 UAT。
