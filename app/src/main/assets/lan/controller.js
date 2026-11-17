const $ = id => document.getElementById(id);
let token = null;
let mode = "ROUTE";
let current = null;
let chosen = {lat:25.033, lon:121.5654};
let map = null;
let maplibre = null;
let positionMarker = null;
let routeMarkers = [];
let latest = null;
let lastStopsKey = "";
let lastLineKey = "";
let refreshing = false;
let connected = false;

const setMessage = text => { $("message").textContent = text; };
const setPairMessage = text => { $("pairmessage").textContent = text; };

async function call(path, method = "GET", body = null, useToken = true) {
  const headers = {};
  if (method === "POST") headers["Content-Type"] = "application/json";
  if (useToken && token) headers["X-PikminBee-Token"] = token;
  const response = await fetch(path, {
    method, headers, body: body == null ? undefined : JSON.stringify(body),
    credentials: "omit", cache: "no-store", redirect: "error"
  });
  const data = await response.json();
  if (response.status === 401 && useToken) {
    token = null;
    connected = false;
    $("controls").classList.remove("is-visible");
    $("pairbox").style.display = "block";
    $("connection").textContent = "配對失效";
    setPairMessage("連線已失效，請重新輸入手機上的配對碼。");
    throw Error("配對失效");
  }
  if (!response.ok) throw Error(data.error || "請求失敗");
  return data;
}

$("connect").addEventListener("click", async () => {
  $("connect").disabled = true;
  try {
    const pin = $("pin").value.trim();
    if (!/^\d{6}$/.test(pin)) throw Error("請輸入手機顯示的 6 位數字");
    const deviceName = (navigator.userAgentData?.platform || navigator.platform || "Browser").slice(0,32);
    const paired = await call("/api/pair", "POST", {pin,deviceName}, false);
    if (!paired.pendingId) throw Error("無法建立配對要求");
    setPairMessage("已收到要求，請回到手機 PikminBee 核准此裝置…");
    let approved = null;
    for (let i = 0; i < 42; i++) {
      await new Promise(resolve => setTimeout(resolve, 2000));
      const response = await fetch("/api/pair/status", {
        headers: {"X-PikminBee-Pending":paired.pendingId},
        cache:"no-store", credentials:"omit", redirect:"error"
      });
      const answer = await response.json();
      if (response.status === 403 || response.status === 401) {
        throw Error(answer.error || "手機已拒絕、要求過期或裝置不符");
      }
      if (!response.ok) throw Error(answer.error || "無法取得配對結果");
      if (answer.token) { approved = answer; break; }
    }
    if (!approved?.token) throw Error("手機核准逾時，請重新配對");
    token = approved.token;
    $("pin").value = "";
    $("pairbox").style.display = "none";
    $("controls").classList.add("is-visible");
    $("connection").textContent = "已連線";
    connected = true;
    await refresh();
    initialiseMap();
  } catch (err) { setPairMessage(err.message || "無法連線"); }
  finally { $("connect").disabled = false; }
});
$("pin").addEventListener("keydown", event => {
  if (event.key === "Enter") $("connect").click();
});

function selectMode(next) {
  mode = next;
  $("mode-route").classList.toggle("mode-active", mode === "ROUTE");
  $("mode-flowers").classList.toggle("mode-active", mode === "FLOWERS");
  const stops = mode === "ROUTE" ? (latest?.routeStops || []) : (latest?.flowerStops || []);
  $("pointcount").textContent = stops.length + " 個路點";
  renderMap();
}
$("mode-route").addEventListener("click", () => selectMode("ROUTE"));
$("mode-flowers").addEventListener("click", () => selectMode("FLOWERS"));

function readCoordinate() {
  const latText = $("lat").value.trim();
  const lonText = $("lon").value.trim();
  if (!latText || !lonText) throw Error("請先輸入經緯度，或點地圖選取位置");
  const lat = Number(latText);
  const lon = Number(lonText);
  if (!Number.isFinite(lat) || !Number.isFinite(lon) ||
      lat < -90 || lat > 90 || lon < -180 || lon > 180) throw Error("經緯度超出有效範圍");
  return {lat,lon};
}
function showCoordinate(point) {
  chosen = point;
  $("lat").value = point.lat.toFixed(6);
  $("lon").value = point.lon.toFixed(6);
  $("selected").textContent = "選取座標 · " + point.lat.toFixed(6) + ", " + point.lon.toFixed(6);
}
showCoordinate(chosen);

async function act(path, body, note) {
  try {
    const result = await call(path, "POST", body);
    setMessage(result.message || note || "指令已送出");
    await refresh();
  } catch (err) { setMessage(err.message || "操作失敗"); }
}
$("teleport").addEventListener("click", () => {
  try { act("/api/teleport", readCoordinate()); }
  catch (e) { setMessage(e.message); }
});
$("add").addEventListener("click", () => {
  try { act("/api/points/add", {mode, ...readCoordinate()}); }
  catch (e) { setMessage(e.message); }
});
$("undo").addEventListener("click", () => act("/api/points/undo", {mode}));
$("clear").addEventListener("click", () => act("/api/points/clear", {mode}));
$("start").addEventListener("click", () => act("/api/route/start", {
  mode, speed:Number($("speed").value), loop:$("loop").value === "true"
}));
for (const [element,action] of [["pause","pause"],["resume","resume"],["stop","stop"]]) {
  $(element).addEventListener("click", () => act("/api/control", {action}));
}
$("real").addEventListener("click", () => {
  if (window.confirm("確定要停止模擬定位並恢復真實 GPS？這也會關閉 Wi-Fi 控制。")) {
    act("/api/control", {action:"restore-real"});
  }
});

const darkQuery = matchMedia("(prefers-color-scheme: dark)");
const storeKey = "pikminbee-map-appearance";
const savedTheme = localStorage.getItem(storeKey) || "system";
$("theme").value = ["system","light","dark"].includes(savedTheme) ? savedTheme : "system";
function isDark() { return $("theme").value === "dark" ||
  ($("theme").value === "system" && darkQuery.matches); }
$("theme").addEventListener("change", () => {
  localStorage.setItem(storeKey,$("theme").value);
  if (map) map.setStyle(isDark() ? "https://tiles.openfreemap.org/styles/dark" :
    "https://tiles.openfreemap.org/styles/liberty");
});
darkQuery.addEventListener?.("change", () => {
  if ($("theme").value === "system" && map) map.setStyle(isDark() ?
    "https://tiles.openfreemap.org/styles/dark" : "https://tiles.openfreemap.org/styles/liberty");
});

async function initialiseMap() {
  if (map) return;
  try {
    maplibre = await import("/vendor/maplibre-gl.mjs");
    map = new maplibre.Map({
      container:"map",
      style: isDark() ? "https://tiles.openfreemap.org/styles/dark" :
        "https://tiles.openfreemap.org/styles/liberty",
      center:[chosen.lon,chosen.lat], zoom:13.5,
      attributionControl:true
    });
    map.addControl(new maplibre.NavigationControl({showCompass:false}),"top-right");
    map.on("click", e => {showCoordinate({lat:e.lngLat.lat,lon:e.lngLat.lng});renderMap();});
    map.on("style.load", () => {lastLineKey = "";renderMap();});
    map.on("load", renderMap);
  } catch (err) {
    $("map").textContent = "地圖資源無法載入，仍可使用經緯度欄位遠端控制。";
    setMessage("地圖無法啟動（可能是瀏覽器不支援或圖磚網路中斷）：" + err.message);
  }
}

function renderMap() {
  if (!map || !map.loaded()) return;
  const point = latest?.position || chosen;
  if (point) {
    if (!positionMarker) positionMarker = new maplibre.Marker({color:"#f3bd5a"})
      .setLngLat([point.lon,point.lat]).addTo(map);
    else positionMarker.setLngLat([point.lon,point.lat]);
  }
  const stops = mode === "ROUTE" ? (latest?.routeStops || []) : (latest?.flowerStops || []);
  const key = mode + "|" + JSON.stringify(stops);
  if (key !== lastStopsKey) {
    routeMarkers.forEach(marker => marker.remove());
    routeMarkers = stops.map((item,i) => new maplibre.Marker({color:"#50b8d5"})
      .setLngLat([item.lon,item.lat])
      .setPopup(new maplibre.Popup().setText("路點 " + (i+1))).addTo(map));
    lastStopsKey = key;
  }
  const track = latest?.geometry || [];
  const lineKey = JSON.stringify(track);
  if (lineKey !== lastLineKey) {
    const coordinates = track.map(item => [item.lon,item.lat]);
    const data = {type:"FeatureCollection",features: coordinates.length >= 2 ? [{
      type:"Feature",properties:{},geometry:{type:"LineString",coordinates}
    }] : []};
    if (map.getSource("bee-walk")) map.getSource("bee-walk").setData(data);
    else {
      map.addSource("bee-walk",{type:"geojson",data});
      map.addLayer({id:"bee-walk-line",type:"line",source:"bee-walk",
        paint:{"line-color":"#f0aa36","line-width":5,"line-opacity":.95}});
    }
    lastLineKey = lineKey;
  }
}

async function refresh() {
  if (!token || refreshing) return;
  refreshing = true;
  try {
    latest = await call("/api/state");
    current = latest.position;
    $("status").textContent = (latest.status || "尚未模擬") +
      " · " + (latest.phase || "IDLE") +
      (latest.position ? " · " + latest.position.lat.toFixed(5) +
        ", " + latest.position.lon.toFixed(5) : "");
    if (latest.note && latest.note.includes("失敗")) setMessage(latest.note);
    const stops = mode === "ROUTE" ? (latest.routeStops || []) : (latest.flowerStops || []);
    $("pointcount").textContent = stops.length + " 個路點";
    renderMap();
  } catch (err) {
    if (token) $("connection").textContent = "連線中斷";
    if (token) setMessage(err.message || "無法取得手機狀態");
  } finally { refreshing = false; }
}
$("follow").addEventListener("click", () => {
  const point = current;
  if (map && point) map.flyTo({center:[point.lon,point.lat],zoom:15});
});
setInterval(() => {
  if (!document.hidden && connected && token) refresh();
}, 2000);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && token) refresh();
});
