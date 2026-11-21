const $ = id => document.getElementById(id);
let token = null;
let mode = "TELEPORT";
let settingsOpen = false;
let savedRecords = null;
const initialCenter = {lat: 25.033, lon: 121.5654};
let chosen = null; // A map centre is not a user-selected teleport destination.
let current = null;
let latest = null;
let connected = false;
let refreshing = false;
let map = null, maplibre = null;
let mapStyleReady = false, mapWatchdog = null, mapResizeObserver = null;
let mapLastError = "";
let positionMarker = null, realMarker = null, originMarker = null, selectionMarker = null;
let routeMarkers = [];
let lastStopsKey = "", lastLineKey = "", lastDraftKey = "", lastOrderKey = "";
let focusRealWhenReady = false;
const loops = {ROUTE: false, FLOWERS: true};
const setMessage = value => { $("message").textContent = value; };
const setPairMessage = value => { $("pairmessage").textContent = value; };
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

async function call(path, method = "GET", body = null, useToken = true) {
  const headers = {};
  if (method === "POST") headers["Content-Type"] = "application/json";
  if (useToken && token) headers["X-PikminBee-Token"] = token;
  let response;
  try {
    response = await fetch(path, {
      method, headers, body: body == null ? undefined : JSON.stringify(body),
      credentials: "omit", cache: "no-store", redirect: "error"
    });
  } catch (_) { throw Error("無法連接手機，請確認私人 Wi-Fi 與網頁控制服務"); }
  const data = await response.json().catch(() => ({}));
  if (response.status === 401 && useToken) {
    token = null; connected = false;
    $("controls").classList.remove("is-visible");
    $("pairbox").style.display = "block";
    $("connection").textContent = "配對已失效";
    setPairMessage("會話已失效，請重新從手機取得配對碼。");
    throw Error("配對已失效");
  }
  if (!response.ok) throw Error(data.error || "手機拒絕這項操作");
  return data;
}

$("connect").addEventListener("click", async () => {
  $("connect").disabled = true;
  try {
    const pin = $("pin").value.trim();
    if (!/^\d{6}$/.test(pin)) throw Error("請輸入手機上的六位數配對碼");
    const platform = navigator.userAgentData?.platform || navigator.platform || "Browser";
    const paired = await call("/api/pair", "POST", {
      pin, deviceName: platform.slice(0, 32)
    }, false);
    if (!paired.pendingId) throw Error("無法建立手機配對要求");
    setPairMessage("手機已收到要求，請在 PikminBee 點選「核准」…");
    let approved;
    for (let i = 0; i < 42; i++) {
      await pause(2000);
      const response = await fetch("/api/pair/status", {
        headers: {"X-PikminBee-Pending": paired.pendingId},
        cache:"no-store", credentials:"omit", redirect:"error"
      });
      const answer = await response.json();
      if (response.status === 401 || response.status === 403) {
        throw Error(answer.error || "手機拒絕配對或配對已過期");
      }
      if (!response.ok) throw Error(answer.error || "無法讀取配對狀態");
      if (answer.token) { approved = answer; break; }
    }
    if (!approved?.token) throw Error("等待手機核准逾時");
    token = approved.token; connected = true;
    $("pin").value = "";
    $("pairbox").style.display = "none";
    $("controls").classList.add("is-visible");
    $("connection").textContent = "已連結手機";
    await refresh();
    await initialiseMap();
  } catch (error) {
    setPairMessage(error.message || "無法建立連線");
  } finally { $("connect").disabled = false; }
});
$("pin").addEventListener("keydown", event => {
  if (event.key === "Enter") $("connect").click();
});

function showCoordinate(point) {
  if (!Number.isFinite(point?.lat) || !Number.isFinite(point?.lon)) return;
  chosen = point;
  $("lat").value = point.lat.toFixed(6);
  $("lon").value = point.lon.toFixed(6);
  $("selected").textContent = "選取位置 · " + point.lat.toFixed(6) +
    ", " + point.lon.toFixed(6);
  updateExternalMapLink(point);
}
function updateExternalMapLink(point = chosen || latest?.position || initialCenter) {
  const lat = Math.max(-85, Math.min(85, Number(point?.lat) || initialCenter.lat));
  const lon = Math.max(-180, Math.min(180, Number(point?.lon) || initialCenter.lon));
  $("external-map").href = "https://www.openstreetmap.org/#map=15/" +
    lat.toFixed(5) + "/" + lon.toFixed(5);
}
updateExternalMapLink();

function readCoordinate() {
  const latText = $("lat").value.trim(), lonText = $("lon").value.trim();
  if (!latText || !lonText) throw Error("請先點地圖選位置或輸入經緯度");
  const lat = Number(latText), lon = Number(lonText);
  if (!Number.isFinite(lat) || !Number.isFinite(lon) ||
      lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    throw Error("無效的緯度或經度");
  }
  const point = {lat,lon};
  showCoordinate(point);
  renderMap();
  return point;
}

function updateModeView() {
  for (const [id,value] of [
    ["mode-teleport","TELEPORT"],["mode-route","ROUTE"],["mode-flowers","FLOWERS"]
  ]) {
    const tab = $(id);
    const active = !settingsOpen && mode === value;
    tab.classList.toggle("mode-active", active);
    tab.setAttribute("aria-pressed", active ? "true" : "false");
  }
  $("mode-settings").classList.toggle("mode-active", settingsOpen);
  $("mode-settings").setAttribute("aria-pressed", settingsOpen ? "true" : "false");
  $("location-panel").classList.toggle("hidden", settingsOpen);
  $("teleport-panel").classList.toggle("hidden", settingsOpen || mode !== "TELEPORT");
  $("route-panel").classList.toggle("hidden", settingsOpen ||
    (mode !== "ROUTE" && mode !== "FLOWERS"));
  $("web-settings").classList.toggle("hidden", !settingsOpen);
  $("flower-guide").classList.toggle("hidden", settingsOpen || mode !== "FLOWERS");
  $("flower-catalogue").classList.toggle("hidden", settingsOpen || mode !== "FLOWERS");
}
function selectMode(next) {
  if (!["TELEPORT","ROUTE","FLOWERS"].includes(next)) return;
  mode = next;
  settingsOpen = false;
  updateModeView();
  if (mode === "ROUTE" || mode === "FLOWERS") $("loop").value = String(loops[mode]);
  $("confirm-snap").checked = false;
  updateRouteControls();
  renderMap();
  // Only the panel scrolls into view on small screens, never the interactive map gestures.
  if (connected && matchMedia("(max-width: 760px)").matches)
    $("control-panel").scrollIntoView({behavior:"smooth",block:"start"});
}
for (const [id,value] of [
  ["mode-teleport","TELEPORT"],["mode-route","ROUTE"],["mode-flowers","FLOWERS"]
]) $(id).addEventListener("click", () => selectMode(value));
$("mode-settings").addEventListener("click", () => {
  settingsOpen = !settingsOpen;
  updateModeView();
});
$("web-settings-close").addEventListener("click", () => {
  settingsOpen = false;
  updateModeView();
});
selectMode("TELEPORT");
// Horizontal swipe switches only the three functional modes, never Settings or the map.
const swipeArea = $("mode-content");
const modeOrder = ["TELEPORT", "ROUTE", "FLOWERS"];
let swipeStart = null;
swipeArea.addEventListener("touchstart", event => {
  swipeStart = null;
  if (event.touches.length !== 1 ||
      event.target.closest("button, input, select, textarea, a, summary, label")) return;
  swipeStart = {x:event.touches[0].clientX,y:event.touches[0].clientY};
}, {passive:true});
swipeArea.addEventListener("touchend", event => {
  const start = swipeStart;
  swipeStart = null;
  if (!start || event.changedTouches.length !== 1) return;
  const dx = event.changedTouches[0].clientX - start.x;
  const dy = event.changedTouches[0].clientY - start.y;
  if (Math.abs(dx) < 72 || Math.abs(dx) <= Math.abs(dy) * 1.4) return;
  const index = modeOrder.indexOf(mode);
  const next = index + (dx < 0 ? 1 : -1);
  if (next >= 0 && next < modeOrder.length) selectMode(modeOrder[next]);
}, {passive:true});
$("loop").addEventListener("change", () => {
  if (mode === "ROUTE" || mode === "FLOWERS") loops[mode] = $("loop").value === "true";
  $("confirm-snap").checked = false;
  updateRouteControls();
});
$("confirm-snap").addEventListener("change", updateRouteControls);

async function searchFlowers() {
  if (!token || mode !== "FLOWERS") return;
  const origin = chosen || latest?.origin || latest?.realPosition || latest?.position;
  const list = $("flower-list");
  if (!origin) {
    $("flower-status").textContent="請先在地圖選擇搜尋中心或指定起點";
    return;
  }
  $("flower-search").disabled=true;
  $("flower-status").textContent="正在查詢手機已記錄的花點（非官方即時資料）…";
  list.replaceChildren();
  try {
    const response=await call("/api/flowers/nearby","POST",{
      lat:origin.lat, lon:origin.lon,
      radiusMetres:Number($("flower-radius").value),
      onlyChecked:$("flower-filter").value==="checked"
    });
    const matches=response.flowers||[];
    $("flower-status").textContent="附近已記錄 "+matches.length+
      " 個花點 · 距離為直線距離，非道路最短路徑";
    if(!matches.length){
      const none=document.createElement("small");none.className="muted";
      none.textContent="此範圍沒有符合條件的花點；可先在 Android 記錄或匯入";
      list.append(none);
    }
    for(const flower of matches){
      const row=document.createElement("article");row.className="flower-item";
      const info=document.createElement("div");info.className="stop-detail";
      const name=document.createElement("strong");name.textContent=flower.name;
      const detail=document.createElement("small");
      const unverified=flower.evidence==="UNVERIFIED_IMPORT";
      detail.textContent=(unverified?"匯入候選 · 尚未確認":"本人確認")+
        " · 約 "+Math.round(flower.distanceMetres)+" 公尺";
      info.append(name,detail);
      const add=document.createElement("button");add.type="button";
      add.textContent="加入路線";
      add.disabled=!latest?.origin||["MOVING","PAUSED"].includes(latest?.phase);
      add.addEventListener("click",async()=>{
        if(unverified&&!confirm("此花點僅是匯入候選，尚未確認為遊戲大花。確定加入尋花路線？"))
          return;
        if(await act("/api/flowers/add",{id:flower.id,confirmUnverified:unverified})){
          $("flower-status").textContent="已加入花點；請確認順序並重新預覽道路";
          updateRouteControls();
        }
      });
      row.append(info,add);list.append(row);
    }
  }catch(error){
    $("flower-status").textContent=error.message||"無法讀取花點";
  }finally{$("flower-search").disabled=false;}
}
$("flower-search").addEventListener("click",searchFlowers);
$("flower-optimize").addEventListener("click",async()=>{
  if(!confirm("使用 Valhalla 步行道路距離建議尋花順序？路點不會被加入或刪除，仍須重新預覽才能開始。"))return;
  if(await act("/api/flowers/optimise",{loop:$("loop").value==="true"}))
    $("flower-status").textContent="正在計算步行道路順序，請查看完成後的路點順序及狀態";
});

async function act(path, body, note = "") {
  try {
    const reply = await call(path, "POST", body);
    setMessage(reply.message || note || "指令已送出");
    await refresh();
    return true;
  } catch (error) {
    setMessage(error.message || "手機無法完成操作");
    return false;
  }
}
$("teleport").addEventListener("click", async () => {
  try {
    if (latest?.phase === "MOVING" || latest?.phase === "PAUSED") {
      if (!confirm("目前路線尚未停止，先停止行走才能瞬移。")) return;
      if (!await act("/api/control",{action:"stop"})) return;
    }
    if (!confirm("確定將手機模擬位置瞬移到所選座標？")) return;
    await act("/api/teleport", readCoordinate());
  } catch (error) { setMessage(error.message); }
});
$("set-origin").addEventListener("click", async () => {
  try { await act("/api/origin", readCoordinate()); }
  catch (error) { setMessage(error.message); }
});
$("add").addEventListener("click", async () => {
  try { await act("/api/points/add", {mode,...readCoordinate()}); }
  catch (error) { setMessage(error.message); }
});
$("undo").addEventListener("click", () => act("/api/points/undo",{mode}));
$("clear").addEventListener("click", async () => {
  if (confirm("要清除全部路點嗎？此操作不會移動 GPS。"))
    await act("/api/points/clear",{mode});
});
$("speed").addEventListener("change", () => {
  act("/api/speed",{speedPreset:$("speed").value});
});
$("make-speed-default").addEventListener("click", () => {
  act("/api/speed/default",{speedPreset:$("speed").value});
});
$("plan").addEventListener("click", async () => {
  $("confirm-snap").checked = false;
  $("plan").disabled = true;
  try {
    await act("/api/route/preview",{mode,loop:$("loop").value === "true"});
  } finally { updateRouteControls(); }
});
$("start").addEventListener("click", async () => {
  const prepared = latest?.previews?.[mode];
  if (!prepared?.id) { setMessage("請先預覽路線，再按開始"); return; }
  if (prepared.loop !== ($("loop").value === "true")) {
    setMessage("循環設定已改變，請重新規劃"); return;
  }
  if (prepared.maxSnapMetres > 40 && !$("confirm-snap").checked) {
    setMessage("請先確認道路吸附位置"); return;
  }
  if (!confirm("已確認路線預覽。現在開始從手機模擬行走？")) return;
  $("start").disabled = true;
  const accepted = await act("/api/route/start",{
    mode, previewId: prepared.id, speedPreset:$("speed").value,
    loop:$("loop").value === "true", confirmSnap:$("confirm-snap").checked
  });
  if (accepted) setMessage("已將開始行走指令送給手機；請查看最新狀態");
  updateRouteControls();
});
for (const [id,action] of [["pause","pause"],["resume","resume"],["stop","stop"]]) {
  $(id).addEventListener("click", async () => {
    if (action === "stop" && !confirm("確定停止行走，將定位保留在目前位置？")) return;
    await act("/api/control",{action});
  });
}
$("real").addEventListener("click", async () => {
  if (!confirm("確定停止模擬 GPS，讓手機恢復真實定位？網頁控制連線仍會保留。")) return;
  if (!await act("/api/control",{action:"restore-real"})) return;
  // STOP is handled asynchronously by Android; only query after mock provider releases.
  for (let i = 0; i < 8; i++) {
    await pause(500);
    try {
      latest = await call("/api/state");
      if (!latest.mockActive && latest.position == null) {
        updateStatus(); renderMap();
        focusRealWhenReady = true;
        if (!await act("/api/location/real",{})) focusRealWhenReady = false;
        return;
      }
    } catch (error) { setMessage(error.message || "無法讀取恢復狀態"); return; }
  }
  setMessage("已送出恢復指令，但手機尚未確認關閉模擬定位；請稍後按「查看手機真實 GPS」。");
});

const darkQuery = matchMedia("(prefers-color-scheme: dark)");
const styles = ["auto","liberty","bright","positron","dark"];
const storeKey = "pikminbee-map-appearance";
const savedStyle = localStorage.getItem(storeKey) || "auto";
$("theme").value = styles.includes(savedStyle) ? savedStyle : "auto";
function styleUrl() {
  const choice = $("theme").value;
  const name = choice === "auto" ? (darkQuery.matches ? "dark" : "liberty") :
    (styles.includes(choice) ? choice : "liberty");
  return "https://tiles.openfreemap.org/styles/" + name;
}
const MAP_LOAD_TIMEOUT_MS = 15000;
let mapWaitTimer = null;
function setMapStatus(state, description = "") {
  $("map-status").dataset.state = state;
  $("map-status").textContent = state === "ready" ? "地圖已載入" :
    state === "error" ? "地圖載入失敗" : "正在載入地圖";
  $("map-warning").classList.toggle("hidden", state !== "error");
  if (state === "error") {
    $("map-warning-text").textContent = description ||
      "無法載入道路或地圖圖磚。可重試或改用手動輸入經緯度。";
  }
}
function clearMapWait() {
  if (mapWaitTimer !== null) clearTimeout(mapWaitTimer);
  mapWaitTimer = null;
}
function armMapWait() {
  clearMapWait();
  setMapStatus("loading");
  mapLastError = "";
  mapWaitTimer = setTimeout(() => {
    const ready = map && mapStyleReady && !mapLastError &&
      (typeof map.areTilesLoaded !== "function" || map.areTilesLoaded());
    if (!ready) {
      setMapStatus("error",
        "地圖樣式或圖磚沒有完成載入。請確認網路可連至 tiles.openfreemap.org，" +
        "再試一次；如果手機可連上但仍空白，可能是瀏覽器渲染或資源授權問題。");
    }
  }, MAP_LOAD_TIMEOUT_MS);
}
function reloadMap() {
  if (map) {
    try { map.remove(); } catch (_) { /* partially initialised MapLibre */ }
  }
  if (mapResizeObserver) { mapResizeObserver.disconnect(); mapResizeObserver = null; }
  clearMapWait();
  map = null; mapStyleReady = false; mapLastError = "";
  positionMarker = null; realMarker = null; originMarker = null; selectionMarker = null;
  routeMarkers = []; lastLineKey = ""; lastDraftKey = ""; lastStopsKey = "";
  $("map").replaceChildren();
  initialiseMap();
}
$("retry-map").addEventListener("click", reloadMap);
function chooseMapStyle() {
  localStorage.setItem(storeKey,$("theme").value);
  if (map) {
    armMapWait();
    mapStyleReady = false;
    lastLineKey = ""; lastDraftKey = "";
    try { map.setStyle(styleUrl()); map.resize(); }
    catch (error) { setMapStatus("error", "無法切換地圖樣式，請按重新載入地圖。"); }
  }
}
$("theme").addEventListener("change", chooseMapStyle);
darkQuery.addEventListener?.("change", () => {
  if ($("theme").value === "auto" && map) chooseMapStyle();
});
async function initialiseMap() {
  if (map) return;
  armMapWait();
  try {
    maplibre = await import("/vendor/maplibre-gl.mjs");
    // Use the packaged MapLibre 6.18.0 worker. Auto-detection otherwise
    // requests a missing /vendor/maplibre-gl-worker.mjs?v=6.18.0 file.
    maplibre.setWorkerUrl("/vendor/maplibre-gl-worker.mjs");
    const center = chosen || latest?.position || latest?.realPosition || initialCenter;
    map = new maplibre.Map({
      container:"map",style:styleUrl(),center:[center.lon,center.lat],
      zoom:13.5,attributionControl:true,
      // The PC has a larger viewport; preserve resources on slower phones.
      maxTileCacheSize: 45
    });
    map.addControl(new maplibre.NavigationControl({showCompass:false}),"top-right");
    map.on("click", e => {
      showCoordinate({lat:e.lngLat.lat,lon:e.lngLat.lng});
      renderMap();
    });
    map.on("style.load", () => {
      mapStyleReady = true;
      lastLineKey = ""; lastDraftKey = "";
      renderMap();
    });
    map.on("error", event => {
      const message = String(event?.error?.message || event?.message || "");
      if (!/(tile|load|fetch|network|cors|glyph|sprite|worker|403|404|429|503|image)/i.test(message))
        return;
      mapLastError = message;
      setMapStatus("error",
        "地圖圖磚或字型載入失敗。請檢查手機網路與 OpenFreeMap 的連線，" +
        "可按重新載入地圖；無法載入時仍可手動輸入座標。");
    });
    map.on("load", () => {
      // Changing display:none to a grid/flex layout may require recalculation.
      map.resize();
      renderMap();
      if (!mapLastError) { clearMapWait(); setMapStatus("ready"); }
    });
    map.on("idle", () => {
      if (mapStyleReady && !mapLastError) {
        clearMapWait();
        setMapStatus("ready");
      }
    });
    map.on("webglcontextlost", () => {
      setMapStatus("error", "瀏覽器地圖繪圖中斷，請按重新載入地圖。");
    });
    if ("ResizeObserver" in window) {
      mapResizeObserver = new ResizeObserver(() => { if (map) map.resize(); });
      mapResizeObserver.observe($("map"));
    }
    requestAnimationFrame(() => { if (map) map.resize(); });
  } catch (error) {
    clearMapWait();
    setMapStatus("error",
      "無法啟動地圖元件，請檢查瀏覽器是否支援 WebGL，或改用手動輸入座標。");
    setMessage("地圖初始化失敗：" + (error?.message || "未知原因"));
  }
}
function ordinalPin(label, variant = "") {
  const pin = document.createElement("div");
  pin.className = "ordinal-pin" + (variant ? " " + variant : "");
  pin.textContent = String(label);
  const meaning = {start:"路線起點",real:"手機真實 GPS",virtual:"手機模擬 GPS"};
  pin.setAttribute("aria-label", meaning[variant] || "路點 " + label);
  return pin;
}
function renderMap() {
  if (!map) return;
  if (latest?.position) {
    const p = latest.position;
    if (!positionMarker) positionMarker = new maplibre.Marker({
      element:ordinalPin("模","virtual"),anchor:"center"
    }).setLngLat([p.lon,p.lat])
      .setPopup(new maplibre.Popup().setText("手機模擬 GPS")).addTo(map);
    else positionMarker.setLngLat([p.lon,p.lat]);
  } else if (positionMarker) { positionMarker.remove(); positionMarker = null; }
  // Physical and virtual phone locations must never share a marker or source.
  const physical = latest?.mockActive ? null : latest?.realPosition;
  if (physical) {
    if (!realMarker) realMarker = new maplibre.Marker({
      element:ordinalPin("真","real"),anchor:"center"
    }).setLngLat([physical.lon,physical.lat])
      .setPopup(new maplibre.Popup().setText("手機真實 GPS（非模擬）")).addTo(map);
    else realMarker.setLngLat([physical.lon,physical.lat]);
  } else if (realMarker) { realMarker.remove(); realMarker = null; }
  const origin = latest?.origin;
  if (origin) {
    if (!originMarker) originMarker = new maplibre.Marker({element:ordinalPin("起", "start"),anchor:"center"})
      .setLngLat([origin.lon,origin.lat]).setPopup(new maplibre.Popup().setText("路線起點")).addTo(map);
    else originMarker.setLngLat([origin.lon,origin.lat]);
  } else if (originMarker) { originMarker.remove(); originMarker = null; }
  if (chosen) {
    if (!selectionMarker) selectionMarker = new maplibre.Marker({color:"#527887"})
      .setLngLat([chosen.lon,chosen.lat])
      .setPopup(new maplibre.Popup().setText("目前選取位置")).addTo(map);
    else selectionMarker.setLngLat([chosen.lon,chosen.lat]);
  } else if (selectionMarker) {
    selectionMarker.remove();
    selectionMarker = null;
  }

  const stops = mode === "FLOWERS" ? (latest?.flowerStops || []) :
    mode === "ROUTE" ? (latest?.routeStops || []) : [];
  const key = mode + "|" + JSON.stringify(stops);
  if (key !== lastStopsKey) {
    routeMarkers.forEach(marker => marker.remove());
    routeMarkers = stops.map((item,i) =>
      new maplibre.Marker({element:ordinalPin(i + 1, i === stops.length-1 ? "last" : ""),anchor:"center"})
        .setLngLat([item.lon,item.lat])
        .setPopup(new maplibre.Popup().setText((mode === "FLOWERS" ? "花點 " : "路點 ") + (i+1)))
        .addTo(map));
    lastStopsKey = key;
  }
  const active = latest?.phase === "MOVING" || latest?.phase === "PAUSED" ||
    (latest?.phase === "PARKED" && latest?.geometry?.length >= 2);
  const road = active ? (latest?.geometry || []) :
    (latest?.previews?.[mode]?.geometry || []);
  // Dashed draft order is only a sequence guide, never a pedestrian-road preview.
  const previewReady = (latest?.previews?.[mode]?.geometry || []).length >= 2;
  const draft = !active && !previewReady && origin && stops.length ?
    [origin, ...stops] : [];
  const draftKey = mode + "|" + JSON.stringify(draft);
  if (mapStyleReady && draftKey !== lastDraftKey) {
    const guide = {type:"FeatureCollection",features:draft.length >= 2 ? [{
      type:"Feature",properties:{},
      geometry:{type:"LineString",coordinates:draft.map(p=>[p.lon,p.lat])}
    }] : []};
    if (map.getSource("bee-draft")) map.getSource("bee-draft").setData(guide);
    else {
      map.addSource("bee-draft",{type:"geojson",data:guide});
      map.addLayer({id:"bee-draft-order",type:"line",source:"bee-draft",
        paint:{"line-color":"#436e85","line-width":3,
          "line-opacity":.9,"line-dasharray":[2,2]}});
    }
    lastDraftKey = draftKey;
  }
  const lineKey = mode + "|" + JSON.stringify(road);
  if (lineKey === lastLineKey || !mapStyleReady) return;
  const coords = road.map(point => [point.lon,point.lat]);
  const data = {type:"FeatureCollection",features:coords.length>=2?[{
    type:"Feature",properties:{},geometry:{type:"LineString",coordinates:coords}
  }]:[]};
  if (map.getSource("bee-walk")) map.getSource("bee-walk").setData(data);
  else {
    map.addSource("bee-walk",{type:"geojson",data});
    map.addLayer({id:"bee-walk-line",type:"line",source:"bee-walk",
      paint:{"line-color":"#d39339","line-width":5,"line-opacity":.95}});
  }
  lastLineKey = lineKey;
}
function formatDistance(metres) {
  return metres >= 1000 ? (metres/1000).toFixed(2) + " 公里" :
    Math.round(metres) + " 公尺";
}
function renderRouteOrder() {
  const stops = mode === "FLOWERS" ? (latest?.flowerStops || []) :
    mode === "ROUTE" ? (latest?.routeStops || []) : [];
  const origin = latest?.origin;
  const busy = latest?.planning || latest?.phase === "MOVING" || latest?.phase === "PAUSED";
  const key = mode + "|" + JSON.stringify(stops) + "|" +
    JSON.stringify(origin) + "|" + busy;
  if (key === lastOrderKey) return;
  lastOrderKey = key;
  const list = $("route-order");
  list.replaceChildren();
  if (mode !== "ROUTE" && mode !== "FLOWERS") return;
  const label = mode === "FLOWERS" ? "花點" : "路點";
  const coordinates = point => point.lat.toFixed(5) + ", " + point.lon.toFixed(5);
  function addRow(point, index) {
    const li = document.createElement("li");
    li.className = "stop-item";
    const num = document.createElement("span");
    num.className = "stop-number" + (index < 0 ? " is-origin" : "");
    num.textContent = index < 0 ? "起" : String(index + 1);
    const details = document.createElement("div");
    details.className = "stop-detail";
    const strong = document.createElement("strong");
    strong.textContent = index < 0 ? "路線起點" : label + " " + (index + 1);
    const small = document.createElement("small");
    small.textContent = point ? coordinates(point) : "尚未指定起點";
    details.append(strong, small);
    li.append(num, details);
    if (index >= 0) {
      const actions = document.createElement("div");
      actions.className = "stop-actions";
      for (const [caption,offset] of [["上移",-1],["下移",1]]) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.textContent = caption;
        btn.setAttribute("aria-label", label + " " + (index + 1) + caption);
        btn.disabled = !!busy || index + offset < 0 || index + offset >= stops.length;
        btn.addEventListener("click", () =>
          act("/api/points/reorder",{mode,index,offset}));
        actions.appendChild(btn);
      }
      const remove = document.createElement("button");
      remove.type = "button";
      remove.textContent = "刪除";
      remove.disabled = !!busy;
      remove.setAttribute("aria-label", "刪除" + label + " " + (index + 1));
      remove.addEventListener("click", () =>
        act("/api/points/remove",{mode,index}));
      actions.appendChild(remove);
      li.append(actions);
    }
    if (point) {
      const view = document.createElement("button");
      view.type = "button";
      view.textContent = "查看";
      view.setAttribute("aria-label", "在地圖查看" + (index < 0 ? "路線起點" : label + " " + (index + 1)));
      view.addEventListener("click", () => {
        map?.flyTo({center:[point.lon,point.lat],zoom:16});
      });
      li.append(view);
    }
    list.appendChild(li);
  }
  addRow(origin, -1);
  stops.forEach((point,index) => addRow(point,index));
  if (!stops.length) {
    const empty = document.createElement("li");
    empty.className = "muted";
    empty.textContent = "尚未加入路點。先點地圖選位置，再按「加入路點」。";
    list.appendChild(empty);
  }
}

function updateRouteControls() {
  const navigating = mode === "ROUTE" || mode === "FLOWERS";
  const stops = mode === "FLOWERS" ? (latest?.flowerStops || []) :
    (latest?.routeStops || []);
  const prepared = latest?.previews?.[mode];
  const hasOrigin = !!latest?.origin;
  const moving = latest?.phase === "MOVING";
  const paused = latest?.phase === "PAUSED";
  const busy = moving || paused || !!latest?.planning;
  const loop = $("loop").value === "true";
  const ready = !!prepared?.id && prepared.loop === loop;
  const needSnap = ready && prepared.maxSnapMetres > 40;
  const max = mode === "ROUTE" ? 15 : 14;
  $("pointcount").textContent = stops.length + " / " + max + " 個" +
    (mode === "FLOWERS" ? "花點" : "路點") + " · 按清單順序行走";
  renderRouteOrder();
  $("originlabel").textContent = hasOrigin ? "目前起點：" +
    latest.origin.lat.toFixed(6) + ", " + latest.origin.lon.toFixed(6) :
    "尚未指定起點 · 在地圖選擇後按「設為起點」";
  $("add").disabled = !navigating || busy || stops.length >= max ||
    (mode === "FLOWERS" && !hasOrigin);
  $("set-origin").disabled = !navigating || busy;
  $("undo").disabled = !navigating || busy || stops.length === 0;
  $("clear").disabled = !navigating || busy || stops.length === 0;
  $("plan").disabled = !navigating || busy || !hasOrigin || stops.length === 0;
  $("flower-optimize").disabled = mode !== "FLOWERS" || busy || !hasOrigin ||
    stops.length < (loop ? 2 : 3);
  $("start").disabled = !navigating || busy || !ready ||
    (needSnap && !$("confirm-snap").checked) ||
    !latest?.mockPermissionReady;
  $("snapconfirmrow").classList.toggle("hidden",!needSnap);
  $("previewinfo").classList.toggle("hidden",!ready);
  if (ready) {
    $("previewinfo").textContent = "路線預覽 · " +
      formatDistance(prepared.distanceMetres) + " · 最大道路吸附 " +
      Math.round(prepared.maxSnapMetres) + " 公尺。請確認後再開始。";
  }
  $("routehint").textContent = !hasOrigin ? "先在地圖選取起點，再按「設為起點」。" :
    stops.length === 0 ? "加入目的地，規劃只會預覽不會移動 GPS。" :
    latest?.planning ? "正在查詢步行道路，不會移動手機。" :
    ready ? "路線已預覽。確認道路和起點後可開始。" :
    "請先按「規劃並預覽道路」。";
  if (navigating && !latest?.mockPermissionReady && ready) {
    $("routehint").textContent += " 開始前仍需在手機設定精確定位及模擬位置 App。";
  }
  $("pause").disabled = !moving;
  $("resume").disabled = !paused;
  $("stop").disabled = !moving && !paused;
  $("teleport").disabled = moving || paused;
}
function updateStatus() {
  const phase = latest?.phase || "IDLE";
  const names = {MOVING:"行走中", PAUSED:"已暫停", PARKED:"已停止", IDLE:"待命"};
  const progress = latest?.progress || {};
  const count = Number(progress.estimatedSteps || 0);
  $("stepstatus").textContent = (names[phase] || phase) +
    " · 本次估算 " + count + " 步";
  $("legstatus").textContent = (progress.activeLeg || "") +
    (progress.totalMetres > 0 ? " · " +
      formatDistance(progress.travelledMetres || 0) + " / " +
      formatDistance(progress.totalMetres) + " · " +
      Math.round((progress.fraction||0)*100) + "%" : "");
  $("progressbar").style.width = Math.round(Math.min(1, Math.max(0, progress.fraction || 0))*100) + "%";
  $("status").textContent = (latest?.status || "尚未模擬定位") +
    (latest?.position ? " · " + latest.position.lat.toFixed(5) +
      ", " + latest.position.lon.toFixed(5) : "");
  $("mobile-state").textContent = names[phase] || "手機待命";
  $("mobile-steps").textContent = "本次估算 " + count + " 步";
  const real = latest?.realPosition;
  const gpsState = latest?.realGpsState || "idle";
  const locationStatus = $("real-location-status");
  locationStatus.dataset.state = gpsState === "ready" ? "ready" :
    gpsState === "unavailable" ? "error" : gpsState;
  locationStatus.textContent = gpsState === "ready" && real ?
    "手機真實 GPS · " + real.lat.toFixed(6) + ", " + real.lon.toFixed(6) +
    (Number.isFinite(real.accuracy) ? " · 精度約 " + Math.round(real.accuracy) + " 公尺" : "") :
    gpsState === "mock" ? "目前為模擬 GPS；需先恢復真實 GPS 才能讀取真實位置" :
    gpsState === "locating" ? "正在向手機取得真實 GPS（地圖不會自動瞬移）…" :
    gpsState === "unavailable" ? "暫無近期真實 GPS，請開啟手機定位及 App 再試一次" :
    "尚未查詢手機真實 GPS；地圖初始中心不是手機定位";
  $("find-real").disabled = gpsState === "mock";
  $("follow").disabled = !latest?.position;
  const activeWalk = phase === "MOVING" || phase === "PAUSED";
  $("motion-controls").classList.toggle("is-active", activeWalk);
  document.body.classList.toggle("has-active-controls", activeWalk);
  $("pause").classList.toggle("is-current-action", phase === "MOVING");
  $("resume").classList.toggle("is-current-action", phase === "PAUSED");
  updateExternalMapLink();
  if (latest?.note && /失敗|無法/.test(latest.note)) setMessage(latest.note);
}
async function refresh() {
  if (!token || refreshing) return;
  refreshing = true;
  try {
    latest = await call("/api/state");
    current = latest.position;
    if (["SLOW","WALK","FAST"].includes(latest.speedPreset))
      $("speed").value = latest.speedPreset;
    const labels = {SLOW:"慢走 0.8",WALK:"走路 1.4",FAST:"快走 10.8"};
    $("default-speed").textContent = "預設：" +
      (labels[latest.defaultSpeedPreset] || labels.WALK) + " km/h";
    $("make-speed-default").disabled = latest.speedPreset === latest.defaultSpeedPreset;
    updateStatus();
    updateRouteControls();
    renderMap();
    if (focusRealWhenReady && latest?.realGpsState === "ready" && latest?.realPosition) {
      const p = latest.realPosition;
      focusRealWhenReady = false;
      map?.flyTo({center:[p.lon,p.lat],zoom:16});
    }
    if (focusRealWhenReady && latest?.realGpsState === "unavailable")
      focusRealWhenReady = false;
  } catch (error) {
    if (token) $("connection").textContent = "手機連線中斷";
    if (token) setMessage(error.message || "無法更新手機狀態");
  } finally { refreshing = false; }
}
$("find-real").addEventListener("click", async () => {
  focusRealWhenReady = true;
  if (!await act("/api/location/real",{})) focusRealWhenReady = false;
});
$("follow").addEventListener("click", () => {
  if (!current) {
    setMessage("手機尚未啟動模擬定位；可先在地圖選起點，或輸入經緯度。");
    return;
  }
  if (map) map.flyTo({center:[current.lon,current.lat],zoom:15});
  else setMessage("地圖未載入，仍可使用手機提供的座標或外部地圖。");
});

function zoneParts(zone, date = new Date()) {
  if (typeof zone !== "string" || !zone || zone.length > 90) return null;
  try {
    const format = new Intl.DateTimeFormat("en-GB", {
      timeZone:zone, year:"numeric", month:"2-digit", day:"2-digit",
      hour:"2-digit", minute:"2-digit", hourCycle:"h23"
    });
    const values = Object.fromEntries(format.formatToParts(date)
      .filter(part=>part.type!=="literal").map(part=>[part.type,Number(part.value)]));
    if (!["year","month","day","hour","minute"].every(key=>Number.isFinite(values[key])))
      return null;
    return {
      clock:String(values.hour).padStart(2,"0")+":"+String(values.minute).padStart(2,"0"),
      // Same physical instant, calculated in this zone; offset can include DST.
      offsetMinutes:Math.round((Date.UTC(values.year,values.month-1,values.day,
        values.hour,values.minute)-date.getTime())/60000)
    };
  } catch (_) { return null; }
}
function destinationTime(zone, realZone) {
  const date = new Date();
  const destination = zoneParts(zone, date);
  if (!destination) return "當地時間待確認 · 時差待確認";
  const reference = latest?.realGpsState==="ready" && latest?.realPosition &&
    !latest?.mockActive ? zoneParts(realZone,date) : null;
  if (!reference) return "當地 "+destination.clock+" · 時差待確認";
  const delta = destination.offsetMinutes-reference.offsetMinutes;
  if (!delta) return "當地 "+destination.clock+" · 與目前所在地同時區";
  const a=Math.abs(delta),hours=Math.floor(a/60),mins=a%60;
  const duration=(hours?hours+" 小時 ":"")+(mins?mins+" 分鐘":"");
  return "當地 "+destination.clock+" · 比目前所在地"+(delta>0?"快 ":"慢 ")+duration.trim();
}
function labelledText(textValue, tag = "small") {
  const element = document.createElement(tag);
  element.textContent = textValue;
  return element;
}
function renderSaved() {
  const list = $("saved-list");
  list.replaceChildren();
  if (!savedRecords) return;
  const query = $("saved-search").value.trim().toLocaleLowerCase();
  const places = (savedRecords.places || []).filter(item =>
    [item.name,item.countryName,item.city,item.countryCode]
      .some(value=>String(value||"").toLocaleLowerCase().includes(query)));
  const routes = (savedRecords.routes||[]).filter(item=>
    String(item.name||"").toLocaleLowerCase().includes(query));
  list.append(labelledText("收藏地點（"+places.length+"）","strong"));
  for (const place of places) {
    if (!Number.isFinite(place?.position?.lat) ||
        !Number.isFinite(place?.position?.lon)) continue;
    const card=document.createElement("article"); card.className="saved-card";
    card.append(labelledText(place.name||"未命名位置","strong"));
    const code=String(place.countryCode||"").toUpperCase();
    const flag=/^[A-Z]{2}$/.test(code) ? String.fromCodePoint(...[...code]
      .map(ch=>0x1F1E6+ch.charCodeAt(0)-65)) : "";
    card.append(labelledText([flag,place.countryName||place.countryCode,place.city]
      .filter(Boolean).join(" · ")||"國家／城市待確認"));
    card.append(labelledText(destinationTime(place.timeZoneId,latest?.realTimeZoneId)));
    const details=document.createElement("details");details.className="saved-details";
    details.append(labelledText("座標及收藏日期","summary"));
    details.append(labelledText("GPS: "+place.position.lat.toFixed(6)+", "+
      place.position.lon.toFixed(6)));
    if(Number.isFinite(place.createdAtMillis)&&place.createdAtMillis>0)
      details.append(labelledText("收藏於 "+
        new Date(place.createdAtMillis).toLocaleDateString("zh-TW")));
    card.append(details);
    const buttons=document.createElement("div");buttons.className="row";
    const view=document.createElement("button");
    view.textContent="在地圖選取";view.addEventListener("click",()=>{
      showCoordinate(place.position);selectMode("TELEPORT");
      $("saved-dialog").close();
      map?.flyTo({center:[place.position.lon,place.position.lat],zoom:15});
      setMessage("已選取收藏位置；沒有自動瞬移");
    });
    const rename=document.createElement("button");rename.type="button";
    rename.textContent="改名";
    rename.addEventListener("click",async()=>{
      const proposed=prompt("修改收藏地點名稱",place.name||"");
      if(proposed===null)return;
      const name=proposed.trim();
      if(!name||name.length>80){setMessage("地點名稱需為 1 到 80 字");return;}
      if(await act("/api/saved/place/rename",{id:place.id,name}))await refreshSaved();
    });
    const remove=document.createElement("button");remove.type="button";
    remove.textContent="刪除";
    remove.addEventListener("click",async()=>{
      if(!confirm("確定刪除「"+place.name+"」？不會變更 GPS"))return;
      if(await act("/api/saved/place/delete",{id:place.id}))await refreshSaved();
    });
    buttons.append(view,rename,remove);card.append(buttons);list.append(card);
  }
  if (!places.length) list.append(labelledText("沒有符合條件的收藏地點"));
  list.append(labelledText("收藏路線（"+routes.length+"）","strong"));
  for(const route of routes){
    const card=document.createElement("article");card.className="saved-card";
    card.append(labelledText(route.name||"未命名路線","strong"));
    card.append(labelledText((route.stops||[]).length+" 個路點 · "+
      (route.mode==="FLOWERS"?"尋花":"道路")));
    const button=document.createElement("button");
    button.textContent="載入草稿（仍須預覽）";
    button.addEventListener("click",async()=>{
      if(await act("/api/saved/load",{id:route.id})){
        $("saved-dialog").close();
        selectMode(route.mode==="FLOWERS"?"FLOWERS":"ROUTE");
        setMessage("收藏路線已載入，請先預覽步行道路");
      }
    });
    card.append(button);list.append(card);
  }
  if (!routes.length) list.append(labelledText("沒有符合條件的收藏路線"));
}
async function refreshSaved() {
  if (!token || !$("saved-dialog").open) return;
  $("saved-list").textContent="正在讀取收藏…";
  try {
    savedRecords=await call("/api/saved");
    renderSaved();
  } catch(error){$("saved-list").textContent=error.message||"無法讀取收藏";}
}
$("show-saved").addEventListener("click",()=>{
  $("saved-dialog").showModal();
  $("saved-search").value="";
  refreshSaved();
});
$("close-saved").addEventListener("click",()=>$("saved-dialog").close());
$("saved-search").addEventListener("input",renderSaved);
setInterval(()=>{if(!document.hidden&&$("saved-dialog").open)renderSaved();},60000);
setInterval(() => { if (!document.hidden && connected && token) refresh(); }, 2000);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && token) refresh();
});
