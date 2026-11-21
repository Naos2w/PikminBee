const $ = id => document.getElementById(id);
let token = null;
let mode = "TELEPORT";
const initialCenter = {lat: 25.033, lon: 121.5654};
let chosen = null; // A map centre is not a user-selected teleport destination.
let current = null;
let latest = null;
let connected = false;
let refreshing = false;
let map = null, maplibre = null;
let mapStyleReady = false, mapWatchdog = null, mapResizeObserver = null;
let mapLastError = "";
let positionMarker = null, originMarker = null, selectionMarker = null;
let routeMarkers = [];
let lastStopsKey = "", lastLineKey = "";
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

function selectMode(next) {
  mode = next;
  for (const [id,value] of [
    ["mode-teleport","TELEPORT"],["mode-route","ROUTE"],
    ["mode-flowers","FLOWERS"],["mode-saved","SAVED"]
  ]) {
    const tab = $("" + id);
    tab.classList.toggle("mode-active", mode === value);
    tab.setAttribute("aria-pressed", mode === value ? "true" : "false");
  }
  $("location-panel").classList.toggle("hidden", mode === "SAVED");
  $("teleport-panel").classList.toggle("hidden", mode !== "TELEPORT");
  $("route-panel").classList.toggle("hidden", mode !== "ROUTE" && mode !== "FLOWERS");
  $("saved-panel").classList.toggle("hidden", mode !== "SAVED");
  if (mode === "ROUTE" || mode === "FLOWERS") $("loop").value = String(loops[mode]);
  $("confirm-snap").checked = false;
  if (mode === "SAVED") refreshSaved();
  updateRouteControls();
  renderMap();
  // The map keeps its own pan/pinch gestures; only navigate the control content.
  if (connected && matchMedia("(max-width: 760px)").matches) {
    $("control-panel").scrollIntoView({behavior:"smooth",block:"start"});
  }
}
for (const [id,value] of [
  ["mode-teleport","TELEPORT"],["mode-route","ROUTE"],
  ["mode-flowers","FLOWERS"],["mode-saved","SAVED"]
]) $(id).addEventListener("click", () => selectMode(value));
selectMode("TELEPORT");
// Match Android's four-mode horizontal swipe without stealing map gestures or form input.
const swipeArea = $("mode-content");
const modeOrder = ["TELEPORT", "ROUTE", "FLOWERS", "SAVED"];
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
  if (confirm("確定停止模擬 GPS，讓手機恢復真實定位？網頁控制連線仍會保留。"))
    await act("/api/control",{action:"restore-real"});
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
  positionMarker = null; originMarker = null; selectionMarker = null;
  routeMarkers = []; lastLineKey = ""; lastStopsKey = "";
  $("map").replaceChildren();
  initialiseMap();
}
$("retry-map").addEventListener("click", reloadMap);
function chooseMapStyle() {
  localStorage.setItem(storeKey,$("theme").value);
  if (map) {
    armMapWait();
    mapStyleReady = false;
    lastLineKey = "";
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
    const center = chosen || latest?.position || initialCenter;
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
      lastLineKey = "";
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
function renderMap() {
  if (!map) return;
  if (latest?.position) {
    const p = latest.position;
    if (!positionMarker) positionMarker = new maplibre.Marker({color:"#f2b858"})
      .setLngLat([p.lon,p.lat]).setPopup(new maplibre.Popup().setText("手機模擬位置")).addTo(map);
    else positionMarker.setLngLat([p.lon,p.lat]);
  } else if (positionMarker) { positionMarker.remove(); positionMarker = null; }
  const origin = latest?.origin;
  if (origin) {
    if (!originMarker) originMarker = new maplibre.Marker({color:"#4c8197"})
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
      new maplibre.Marker({color:i === stops.length-1 ? "#b9575b" : "#4e9f8c"})
        .setLngLat([item.lon,item.lat])
        .setPopup(new maplibre.Popup().setText("路點 " + (i+1))).addTo(map));
    lastStopsKey = key;
  }
  const active = latest?.phase === "MOVING" || latest?.phase === "PAUSED" ||
    (latest?.phase === "PARKED" && latest?.geometry?.length >= 2);
  const road = active ? (latest?.geometry || []) :
    (latest?.previews?.[mode]?.geometry || []);
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
  $("pointcount").textContent = stops.length + " / " + max + " 個路點";
  $("originlabel").textContent = hasOrigin ? "目前起點：" +
    latest.origin.lat.toFixed(6) + ", " + latest.origin.lon.toFixed(6) :
    "尚未指定起點 · 在地圖選擇後按「設為起點」";
  $("add").disabled = !navigating || busy || stops.length >= max ||
    (mode === "FLOWERS" && !hasOrigin);
  $("set-origin").disabled = !navigating || busy;
  $("undo").disabled = !navigating || busy || stops.length === 0;
  $("clear").disabled = !navigating || busy || stops.length === 0;
  $("plan").disabled = !navigating || busy || !hasOrigin || stops.length === 0;
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
  } catch (error) {
    if (token) $("connection").textContent = "手機連線中斷";
    if (token) setMessage(error.message || "無法更新手機狀態");
  } finally { refreshing = false; }
}
$("follow").addEventListener("click", () => {
  if (!current) {
    setMessage("手機尚未啟動模擬定位；可先在地圖選起點，或輸入經緯度。");
    return;
  }
  if (map) map.flyTo({center:[current.lon,current.lat],zoom:15});
  else setMessage("地圖未載入，仍可使用手機提供的座標或外部地圖。");
});

async function refreshSaved() {
  if (!token || mode !== "SAVED") return;
  const list = $("saved-list");
  list.textContent = "正在讀取收藏…";
  try {
    const records = await call("/api/saved");
    list.replaceChildren();
    const addTitle = title => {
      const line = document.createElement("strong");
      line.textContent = title;
      list.appendChild(line);
    };
    addTitle("收藏地點");
    const places = records.places || [];
    if (!places.length) {
      const note = document.createElement("span"); note.className="muted";
      note.textContent="尚未收藏地點"; list.appendChild(note);
    }
    for (const place of places) {
      const button = document.createElement("button");
      button.textContent = place.name + " · 選取位置";
      button.addEventListener("click", () => {
        showCoordinate(place.position);
        selectMode("TELEPORT");
        map?.flyTo({center:[place.position.lon,place.position.lat],zoom:15});
      });
      list.appendChild(button);
    }
    addTitle("收藏路線");
    const routes = records.routes || [];
    if (!routes.length) {
      const note = document.createElement("span"); note.className="muted";
      note.textContent="尚未收藏路線"; list.appendChild(note);
    }
    for (const route of routes) {
      const button = document.createElement("button");
      button.textContent = route.name + " · " + route.stops.length + " 點 · 載入草稿";
      button.addEventListener("click", async () => {
        if (await act("/api/saved/load",{id:route.id})) {
          selectMode(route.mode);
          setMessage("收藏路線已載入，請先預覽再開始");
        }
      });
      list.appendChild(button);
    }
  } catch (error) { list.textContent = error.message || "無法讀取收藏"; }
}
setInterval(() => { if (!document.hidden && connected && token) refresh(); }, 2000);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && token) refresh();
});
