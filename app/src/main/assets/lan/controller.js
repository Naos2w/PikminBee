import { BRIGHT_STYLE_URL, samePosition, currentPosition, createCameraFocus, visibleMapPadding, countryFlagPath, destinationTime, routePresentation } from "/map-ui-policy.mjs";
const $ = id => document.getElementById(id);
let token = null;
let sessionDeadlineMs = 0;
let renewingSession = false;
let mode = "TELEPORT";
let settingsOpen = false;
let savedRecords = null;
const initialCenter = {lat: 25.033, lon: 121.5654};
let chosen = null; // A map centre is not a user-selected teleport destination.
let current = null;
let latest = null;
let speedEditing = false;
let connected = false;
let remoteLinkHealthy = true;
let refreshing = false;
let routeMutationPending = false, routeStateVersion = 0;
let map = null, maplibre = null;
let mapStyleReady = false, mapWatchdog = null, mapResizeObserver = null;
let mapLastError = "";
let positionMarker = null, realMarker = null, originMarker = null, selectionMarker = null;
let routeMarkers = [];
let lastStopsKey = "", lastLineKey = "", lastDraftKey = "", lastOrderKey = "";
let focusRealWhenReady = false;
const cameraFocus = createCameraFocus();
let teleportBusy = false, pendingTeleportConfirmation = null;
let brightDarkPalette = null, originalBrightPaint = new Map();
const loops = {ROUTE: false};
const setMessage = value => { $("message").textContent = value; };
const setPairMessage = value => { $("pairmessage").textContent = value; };
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

function updateSessionExpiry() {
  const visible = !!token;
  $("session-panel").classList.toggle("hidden", !visible);
  if (!visible) return;
  const remaining = Math.max(0, Math.ceil((sessionDeadlineMs - Date.now()) / 1000));
  const hours = String(Math.floor(remaining / 3600)).padStart(2, "0");
  const minutes = String(Math.floor(remaining % 3600 / 60)).padStart(2, "0");
  const seconds = String(remaining % 60).padStart(2, "0");
  $("session-expiry").textContent = remaining === 0
    ? "授權已到期"
    : (remaining <= 600 ? "快到期 " : "剩餘 ") + hours + ":" + minutes + ":" + seconds;
  $("session-expiry").title = remaining === 0
    ? "控制授權時間已到，請重新配對"
    : remaining <= 600 ? "控制授權即將到期，請在手機核准續期" : "控制授權剩餘時間";
  $("session-panel").classList.toggle("is-urgent", remaining <= 600);
  $("session-renew").disabled = renewingSession || remaining <= 0;
  $("session-renew").textContent = renewingSession
    ? "等待核准…" : "續期";
  $("session-renew").title = "從手機核准延長 2 小時";
}

async function call(path, method = "GET", body = null, useToken = true) {
  const headers = {};
  const requestToken = useToken ? token : null;
  if (method === "POST") headers["Content-Type"] = "application/json";
  if (useToken && requestToken) headers["X-PikminBee-Token"] = requestToken;
  let response;
  try {
    response = await fetch(path, {
      method, headers, body: body == null ? undefined : JSON.stringify(body),
      credentials: "omit", cache: "no-store", redirect: "error"
    });
  } catch (_) { throw Error("無法連接手機，請確認私人 Wi-Fi 與網頁控制服務"); }
  const data = await response.json().catch(() => ({}));
  if (response.status === 401 && useToken) {
    // An old in-flight poll may finish after a phone-approved token rotation.
    // Never tear down the newer session because an older token was rejected.
    if (requestToken !== token || (renewingSession && path === "/api/state"))
      throw Error("等待續期完成，忽略舊狀態");
    token = null; connected = false;
    sessionDeadlineMs = 0;
    remoteLinkHealthy = false;
    latest = null; // clear any in-memory sensitive physical snapshot on revocation
    current = null;
    cameraFocus.clear();
    pendingTeleportConfirmation = null;
    focusRealWhenReady = false;
    $("app-version").textContent = "版本待確認";
    updateSessionExpiry();
    updateCompactWalkControls();
    document.body.classList.remove("remote-active");
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
    token = approved.token; connected = true; remoteLinkHealthy = true;
    sessionDeadlineMs = Date.now() + Math.max(0, approved.expiresInSeconds || 0) * 1000;
    updateSessionExpiry();
    document.body.classList.add("remote-active");
    applyResponsiveLayout();
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

$("session-renew").addEventListener("click", async () => {
  if (!token || renewingSession || sessionDeadlineMs <= Date.now()) return;
  renewingSession = true;
  updateSessionExpiry();
  try {
    const requestedToken = token;
    const pending = await call("/api/session/renew", "POST", {});
    if (!pending.pendingId) throw Error("無法建立續期要求");
    setMessage("續期要求已送往手機，請在 PikminBee 設定中點「核准續期」");
    for (let attempt = 0; attempt < 42; attempt++) {
      await pause(2000);
      if (token !== requestedToken) throw Error("工作階段已更換，請重新確認");
      const result = await call("/api/session/renew/status", "POST",
        {pendingId:pending.pendingId});
      if (!result.token) continue;
      token = result.token;
      sessionDeadlineMs = Date.now() + Math.max(0, result.expiresInSeconds || 0) * 1000;
      // A renewed token must not inherit the previous precise GPS sharing grant.
      latest = null;
      current = null;
      cameraFocus.clear();
      pendingTeleportConfirmation = null;
      focusRealWhenReady = false;
      connected = true;
      remoteLinkHealthy = true;
      updateCompactWalkControls();
      document.body.classList.add("remote-active");
      $("pairbox").style.display = "none";
      $("controls").classList.add("is-visible");
      $("connection").textContent = "已連結手機";
      renderMap();
      updateSessionExpiry();
      await refresh();
      setMessage("手機已核准續期，控制權限再延長 2 小時；真實 GPS 分享需重新授權");
      return;
    }
    throw Error("手機核准逾時，請重新要求續期");
  } catch (error) {
    setMessage(error.message || "續期失敗，請使用原工作階段或重新配對");
  } finally {
    renewingSession = false;
    updateSessionExpiry();
  }
});

function showCoordinate(point) {
  if (!Number.isFinite(point?.lat) || !Number.isFinite(point?.lon)) return;
  chosen = point;
  $("coordinate").value = point.lat.toFixed(6) + ", " + point.lon.toFixed(6);
  $("selected").textContent = "地圖選取草稿（不會自動瞬移） · " +
    point.lat.toFixed(6) + ", " + point.lon.toFixed(6);
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
  const raw = $("coordinate").value.trim();
  if (!raw) throw Error("請先點地圖選位置或貼上緯度, 經度");
  const numbers = raw.split(/[,，\s]+/).filter(Boolean);
  if (numbers.length !== 2 ||
      !numbers.every(part => /^[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)$/.test(part))) {
    throw Error("請輸入兩個數值，例如 25.033000, 121.565400");
  }
  const lat = Number(numbers[0]), lon = Number(numbers[1]);
  if (!Number.isFinite(lat) || !Number.isFinite(lon) ||
      lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    throw Error("座標超出範圍：緯度 -90～90、經度 -180～180");
  }
  const point = {lat,lon};
  showCoordinate(point);
  renderMap();
  return point;
}

function updateModeView() {
  document.body.dataset.mode = settingsOpen ? "settings" : mode.toLowerCase();
  for (const [id,value] of [
    ["mode-teleport","TELEPORT"],["mode-route","ROUTE"]
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
  $("route-panel").classList.toggle("hidden", settingsOpen || mode !== "ROUTE");
  $("web-settings").classList.toggle("hidden", !settingsOpen);
  $("mode-panel-title").textContent = settingsOpen ? "設定" :
    ({TELEPORT:"瞬移",ROUTE:"路徑"}[mode] || "瞬移");
}
// V2: the actual map canvas fills the viewport; these controls only change
// overlays, not the map's view, GPS provider or current route.
let sheetState = "collapsed";
let sideOpen = false;
let sideUserOverride = false;
let lastDevice = "";
function deviceKind() {
  // Use the design-approved 690×920 portrait tablet layout without
  // forcing ordinary narrower handsets (or short 700px phones) into a drawer.
  const w = window.innerWidth, h = window.innerHeight;
  if ((w <= 600 && h >= w) || (w <= 760 && h <= 800 && h > w) || w <= 480) return "mobile";
  // Short landscape screens use the compact floating side panel, not a sheet.
  if (w <= 1060) return "tablet";
  return "desktop";
}
function setSheetState(next) {
  if (!["collapsed","half","expanded"].includes(next)) return;
  sheetState = next;
  $("control-panel").dataset.sheet = next;
  $("sheet-handle").setAttribute("aria-expanded", String(next !== "collapsed"));
  $("sheet-summary").textContent = (settingsOpen ? "設定" :
    ({TELEPORT:"瞬移",ROUTE:"路徑"}[mode] || "瞬移")) +
    (next === "collapsed" ? " · 向上展開" : " · 向下收合");
  updateCompactWalkControls();
  // A sheet is an overlay. It must not resize or pan the existing map.
  requestAnimationFrame(flushMapFocus);
}
function setSidePanel(open, userAction = false) {
  sideOpen = !!open;
  if (userAction) sideUserOverride = true;
  $("control-panel").dataset.panel = sideOpen ? "open" : "closed";
  $("panel-toggle").setAttribute("aria-expanded", String(sideOpen));
  $("panel-toggle").textContent = sideOpen ? "❯" : "❮";
  $("panel-toggle").title = sideOpen ? "收合控制面板" : "展開控制面板";
  // Do not reposition the map or the tools when opening/closing the panel.
  requestAnimationFrame(flushMapFocus);
}
function applyResponsiveLayout() {
  const kind = deviceKind();
  document.body.dataset.device = kind;
  // Respect a deliberate user override even when a tablet is rotated.
  if (kind !== lastDevice) {
    if (kind === "mobile") setSheetState("collapsed");
    else if (!sideUserOverride) setSidePanel(false);
  }
  lastDevice = kind;
  requestAnimationFrame(() => { map?.resize(); flushMapFocus(); });
}
function selectMode(next, userAction = true) {
  if (!["TELEPORT","ROUTE"].includes(next)) return;
  const wasCurrent = !settingsOpen && mode === next;
  mode = next;
  settingsOpen = false;
  updateModeView();
  if (mode === "ROUTE") $("loop").value = String(loops.ROUTE);
  $("confirm-snap").checked = false;
  updateRouteControls();
  renderMap();
  if (userAction) {
    if (deviceKind() === "mobile") {
      setSheetState(wasCurrent && sheetState !== "collapsed" ? "collapsed" : "half");
    } else setSidePanel(!(wasCurrent && sideOpen), true);
  }
}
for (const [id,value] of [
  ["mode-teleport","TELEPORT"],["mode-route","ROUTE"]
]) $(id).addEventListener("click", () => selectMode(value));
$("mode-settings").addEventListener("click", () => {
  const wasCurrent = settingsOpen;
  settingsOpen = !settingsOpen;
  updateModeView();
  if (deviceKind() === "mobile") {
    setSheetState(wasCurrent && sheetState !== "collapsed" ? "collapsed" : "half");
  } else setSidePanel(!(wasCurrent && sideOpen), true);
});
$("web-settings-close").addEventListener("click", () => {
  settingsOpen = false;
  updateModeView();
  if (deviceKind() === "mobile") setSheetState("collapsed");
});
$("panel-toggle").addEventListener("click", () => setSidePanel(!sideOpen, true));
let ignoreNextHandleClick = false, handlePointerY = null;
$("sheet-handle").addEventListener("pointerdown", event => {
  handlePointerY = event.clientY;
  $("sheet-handle").setPointerCapture?.(event.pointerId);
});
$("sheet-handle").addEventListener("pointerup", event => {
  if (handlePointerY == null) return;
  const dy = event.clientY - handlePointerY;
  handlePointerY = null;
  if (Math.abs(dy) < 32) return;
  ignoreNextHandleClick = true;
  if (dy < 0) setSheetState(sheetState === "collapsed" ? "half" : "expanded");
  else setSheetState(sheetState === "expanded" ? "half" : "collapsed");
});
$("sheet-handle").addEventListener("click", () => {
  if (ignoreNextHandleClick) { ignoreNextHandleClick = false; return; }
  setSheetState(sheetState === "collapsed" ? "half" : "collapsed");
});
window.addEventListener("resize", applyResponsiveLayout, {passive:true});
selectMode("TELEPORT", false);
applyResponsiveLayout();
// Horizontal swipe switches only Teleport and Route, never Settings or the map.
const swipeArea = $("mode-content");
const modeOrder = ["TELEPORT", "ROUTE"];
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
  if (mode === "ROUTE") loops.ROUTE = $("loop").value === "true";
  $("confirm-snap").checked = false;
  updateRouteControls();
  renderMap();
});
$("confirm-snap").addEventListener("change", updateRouteControls);

function flushMapFocus() {
  try {
    // No layout work during polling/overlay toggles unless a focus is pending.
    // Use exact rendered overlay bounds, not guessed sidebar widths.
    return cameraFocus.flush(map, viewport => {
      const elements = document.querySelectorAll(
        ".head,.tabs,#control-panel,#map-locations,#map-locations[open] .location-details," +
        "#show-saved,#map-status,.map-quick-tools,#compact-walk-controls,#message:not(:empty)," +
        ".maplibregl-ctrl-bottom-right,#map-warning:not(.hidden)"
      );
      const overlays = [...elements].filter(el=>getComputedStyle(el).display!=="none")
        .map(el=>el.getBoundingClientRect()).filter(r=>r.width>0 && r.height>0);
      return visibleMapPadding(viewport, overlays);
    });
  } catch (_) { return false; }
}
function focusMapPosition(point, zoom = 15, collapse = true) {
  if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)) return false;
  // Keep the full map canvas. Collapse the mobile sheet on explicit focus,
  // and use the unobstructed region of that canvas for camera centring.
  $("map-locations").open = false;
  if (collapse && deviceKind() === "mobile") setSheetState("collapsed");
  cameraFocus.request(point, zoom);
  return flushMapFocus();
}
function reconcileTeleport(state) {
  const expected = pendingTeleportConfirmation;
  if (!expected) return;
  if (expected.session !== token) {
    pendingTeleportConfirmation = null;
    return;
  }
  if (state?.mockActive && samePosition(state.position,expected.point,8)) {
    pendingTeleportConfirmation = null;
    setMessage("已確認手機瞬移至目標位置");
  } else if (Date.now() >= expected.deadline) {
    pendingTeleportConfirmation = null;
    setMessage("地圖已移至目標，但手機尚未確認定位變更；請檢查手機狀態");
  }
}
async function act(path, body, note = "") {
  const editsRoute = path.startsWith("/api/points/") ||
    ["/api/origin", "/api/saved/load", "/api/route/preview"].includes(path);
  if (editsRoute) {
    if (routeMutationPending) return false;
    routeMutationPending = true;
    routeStateVersion++;
    // Clear the stale preview immediately; do not let a pre-edit poll restore it.
    if (latest) latest = {...latest, previews:{}, planning:true};
    $("confirm-snap").checked = false;
    updateRouteControls();
    renderMap();
  }
  try {
    const reply = await call(path, "POST", body);
    setMessage(reply.message || note || "指令已送出");
    if (!editsRoute) await refresh();
    return true;
  } catch (error) {
    if ((error.message || "").includes("無法連接手機")) {
      remoteLinkHealthy = false;
      updateCompactWalkControls();
    }
    setMessage(error.message || "手機無法完成操作");
    return false;
  } finally {
    if (editsRoute) {
      routeMutationPending = false;
      await refresh();
    }
  }
}
$("teleport").addEventListener("click", async () => {
  if (teleportBusy) return;
  teleportBusy = true;
  $("teleport").disabled = true;
  try {
    if (latest?.phase === "MOVING" || latest?.phase === "PAUSED") {
      if (!confirm("目前路線尚未停止，先停止行走才能瞬移。")) return;
      if (!await act("/api/control",{action:"stop"})) return;
      // Android applies Stop asynchronously. Do not send teleport while the
      // route service still reports MOVING/PAUSED (the server will reject it).
      for (let attempt = 0; attempt < 10 &&
          ["MOVING","PAUSED"].includes(latest?.phase); attempt++) {
        await pause(300);
        await refresh();
      }
      if (["MOVING","PAUSED"].includes(latest?.phase)) {
        setMessage("路線尚未停止，請確認停止後再瞬移");
        return;
      }
    }
    if (!confirm("確定將手機模擬位置瞬移到所選座標？")) return;
    const destination = readCoordinate();
    const session = token;
    // Do not use act() here: it awaits a state refresh after HTTP acceptance.
    // Move the camera synchronously after the accepted command, not after polling.
    await call("/api/teleport","POST",destination);
    if (!token || token !== session) return;
    pendingTeleportConfirmation = {point:destination,session,deadline:Date.now()+15000};
    const acceptedAt = performance.now();
    setMessage("瞬移指令已接受；等待手機確認 GPS 位置");
    if (focusMapPosition(destination,15)) {
      // Local-only performance measurements, not analytics or telemetry.
      performance.clearMeasures("pikminbee-teleport-camera");
      performance.measure("pikminbee-teleport-camera",{start:acceptedAt,end:performance.now()});
      requestAnimationFrame(() => {
        performance.clearMeasures("pikminbee-teleport-camera-frame");
        performance.measure("pikminbee-teleport-camera-frame",{start:acceptedAt,end:performance.now()});
      });
    }
    void refresh();
  } catch (error) {
    if ((error.message || "").includes("無法連接手機")) {
      remoteLinkHealthy = false;
      updateCompactWalkControls();
    }
    setMessage(error.message || "瞬移指令失敗");
  } finally {
    teleportBusy = false;
    updateRouteControls();
  }
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
function showSpeedFields() {
  const choice = $("speed").value;
  $("speed-extra").classList.toggle("hidden", !["FIXED","RANGE"].includes(choice));
  $("speed-fixed-wrap").classList.toggle("hidden", choice !== "FIXED");
  $("speed-range-wrap").classList.toggle("hidden", choice !== "RANGE");
  $("make-speed-default").disabled = ["FIXED","RANGE"].includes(choice);
}
function speedPayload() {
  const selected = $("speed").value;
  if (["SLOW","WALK","FAST"].includes(selected))
    return {speedMode:"PRESET",speedPreset:selected};
  const finite = text => {
    const raw = text.trim();
    const value = Number(raw);
    if (!raw || !Number.isFinite(value) || value < 0.8 || value > 20)
      throw Error("速度必須介於 0.8–20 km/h");
    return value;
  };
  if (selected === "FIXED")
    return {speedMode:"FIXED",speedKmh:finite($("speed-fixed").value)};
  if (selected === "RANGE") {
    const min = finite($("speed-min").value), max = finite($("speed-max").value);
    if (min >= max) throw Error("區間最低速度必須小於最高速度");
    return {speedMode:"RANGE",speedMinKmh:min,speedMaxKmh:max};
  }
  throw Error("請選擇有效速度");
}
$("speed").addEventListener("change", async () => {
  showSpeedFields();
  if (!["SLOW","WALK","FAST"].includes($("speed").value)) {
    speedEditing = true; return;
  }
  speedEditing = false;
  await act("/api/speed",speedPayload());
});
for (const id of ["speed-fixed","speed-min","speed-max"])
  $(id).addEventListener("input", () => {speedEditing = true;});
$("apply-speed").addEventListener("click", async () => {
  try {
    const payload = speedPayload();
    if (await act("/api/speed", payload)) speedEditing = false;
  } catch (error) { setMessage(error.message); }
});
$("make-speed-default").addEventListener("click", () => {
  if (["SLOW","WALK","FAST"].includes($("speed").value))
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
  const prepared = routePresentation(latest, mode, $("loop").value === "true").prepared;
  if (!prepared?.id) { setMessage("請先預覽路線，再按開始"); return; }
  if (prepared.loop !== ($("loop").value === "true")) {
    setMessage("循環設定已改變，請重新規劃"); return;
  }
  if (prepared.maxSnapMetres > 40 && !$("confirm-snap").checked) {
    setMessage("請先確認道路吸附位置"); return;
  }
  if (!confirm("已確認路線預覽。現在開始從手機模擬行走？")) return;
  let selectedSpeed;
  try { selectedSpeed = speedPayload(); }
  catch (error) { setMessage(error.message); return; }
  $("start").disabled = true;
  const accepted = await act("/api/route/start",{
    mode, previewId: prepared.id, ...selectedSpeed,
    loop:$("loop").value === "true", confirmSnap:$("confirm-snap").checked
  });
  if (accepted) { speedEditing = false; setMessage("已將開始行走指令送給手機；請查看最新狀態"); }
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
        setMessage("已切回真實 GPS 模式；若已在手機允許分享，可按「真實位置」取得定位。");
        return;
      }
    } catch (error) { setMessage(error.message || "無法讀取恢復狀態"); return; }
  }
  setMessage("已送出恢復指令，但手機尚未確認關閉模擬定位；請稍後按「真實位置」。");
});

const darkQuery = matchMedia("(prefers-color-scheme: dark)");
const webThemeStoreKey = "pikminbee-web-theme";
const webThemes = ["auto","light","dark"];
const savedWebTheme = localStorage.getItem(webThemeStoreKey) || "auto";
$("web-appearance").value = webThemes.includes(savedWebTheme) ? savedWebTheme : "auto";
document.documentElement.dataset.webTheme = $("web-appearance").value;
function webThemeIsDark() {
  const chosenTheme = $("web-appearance").value;
  return chosenTheme === "dark" || (chosenTheme === "auto" && darkQuery.matches);
}
// The retired individual basemap preferences are no longer needed.
localStorage.removeItem("pikminbee-map-appearance");
function styleUrl() { return BRIGHT_STYLE_URL; }
async function loadBrightPalette() {
  if (brightDarkPalette) return;
  const response = await fetch("/bright-dark-palette.json",{cache:"no-store"});
  if (!response.ok) throw Error("無法載入地圖明暗配色");
  brightDarkPalette = await response.json();
}
function applyBrightPalette() {
  if (!map || !mapStyleReady || !brightDarkPalette) return;
  const dark = webThemeIsDark();
  // Recolour only original Bright paint; preserve layout/filters/zoom/source.
  // Capture the original values once so a light/dark switch does not reload tiles.
  for (const group of brightDarkPalette) {
    for (const id of group.layers) {
      if (!map.getLayer(id)) continue;
      const key = id + "|" + group.property;
      if (!originalBrightPaint.has(key))
        originalBrightPaint.set(key,map.getPaintProperty(id,group.property));
      const value = dark ? group.value : originalBrightPaint.get(key);
      if (value !== undefined || dark) map.setPaintProperty(id,group.property,value);
      else map.setPaintProperty(id,group.property,null);
    }
  }
}
/** Trust only the phone-authorised, time-bounded physical snapshot. The
 * browser can never infer real GPS from its own geolocation or mock provider. */
function trustedRealPosition() {
  const point = latest?.realPosition;
  if (!latest?.realLocationSharingApproved || !point ||
      !Number.isFinite(point.lat) || !Number.isFinite(point.lon) ||
      !Number.isFinite(point.capturedAtMillis) ||
      !Number.isFinite(point.expiresAtMillis) ||
      Date.now() > point.expiresAtMillis) return null;
  return point;
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
$("map-zoom-in").addEventListener("click",()=>map?.zoomIn({duration:120}));
$("map-zoom-out").addEventListener("click",()=>map?.zoomOut({duration:120}));
function chooseWebAppearance() {
  const choice = $("web-appearance").value;
  const selectedTheme = webThemes.includes(choice) ? choice : "auto";
  document.documentElement.dataset.webTheme = selectedTheme;
  localStorage.setItem(webThemeStoreKey, selectedTheme);
  applyBrightPalette();
}
$("web-appearance").addEventListener("change", chooseWebAppearance);
darkQuery.addEventListener?.("change", () => {
  if ($("web-appearance").value === "auto") applyBrightPalette();
});
async function initialiseMap() {
  if (map) return;
  armMapWait();
  try {
    await loadBrightPalette();
    maplibre = await import("/vendor/maplibre-gl.mjs");
    // Use the packaged MapLibre 6.18.0 worker. Auto-detection otherwise
    // requests a missing /vendor/maplibre-gl-worker.mjs?v=6.18.0 file.
    maplibre.setWorkerUrl("/vendor/maplibre-gl-worker.mjs");
    const center = chosen || latest?.position || trustedRealPosition() || initialCenter;
    map = new maplibre.Map({
      container:"map",style:styleUrl(),center:[center.lon,center.lat],
      zoom:13.5,attributionControl:true,
      // The PC has a larger viewport; preserve resources on slower phones.
      maxTileCacheSize: 45
    });
    // Zoom/location actions share a permanent bottom-right overlay.
    // Native attribution remains visible and is never covered by the tools.
    map.on("dragstart", () => cameraFocus.clear());
    map.on("click", e => {
      showCoordinate({lat:e.lngLat.lat,lon:e.lngLat.lng});
      renderMap();
    });
    map.on("style.load", () => {
      mapStyleReady = true;
      originalBrightPaint = new Map();
      applyBrightPalette();
      lastLineKey = ""; lastDraftKey = "";
      renderMap();
      flushMapFocus();
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
      flushMapFocus();
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
      mapResizeObserver = new ResizeObserver(() => {
        if (map) { map.resize(); flushMapFocus(); }
      });
      mapResizeObserver.observe($("map"));
    }
    requestAnimationFrame(() => { if (map) { map.resize(); flushMapFocus(); } });
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
  pin.setAttribute("aria-label", "路點 " + label);
  return pin;
}

// Match the native RoutePinBitmap.current() dot: 24dp footprint, white ring,
// blue real GPS / orange simulated GPS. Labels remain in accessible tooltips.
function currentPositionDot(simulated) {
  const pin = document.createElement("div");
  pin.className = "position-dot " + (simulated ? "virtual" : "real");
  // Preserve MapLibre's absolute positioning (never put markers in normal flow).
  pin.style.position = "absolute";
  pin.setAttribute("role", "img");
  pin.setAttribute("aria-label", simulated
    ? "手機目前模擬 GPS 位置" : "手機最後驗證的真實 GPS 位置（非即時）");
  return pin;
}

function routeOriginPin() {
  const pin = document.createElement("div");
  pin.className = "route-origin-pin";
  pin.setAttribute("role", "img");
  pin.setAttribute("aria-label", "路線起點");
  return pin;
}

function renderMap() {
  if (!map) return;
  const virtual = latest?.mockActive ? latest.position : null;
  if (virtual) {
    const p = virtual;
    if (!positionMarker) positionMarker = new maplibre.Marker({
      element:currentPositionDot(true),anchor:"center"
    }).setLngLat([p.lon,p.lat])
      .setPopup(new maplibre.Popup().setText("手機模擬 GPS")).addTo(map);
    else positionMarker.setLngLat([p.lon,p.lat]);
  } else if (positionMarker) { positionMarker.remove(); positionMarker = null; }
  // Physical and virtual phone locations must never share a marker or source.
  const physical = trustedRealPosition();
  if (physical && !samePosition(physical,virtual)) {
    const when = new Date(physical.capturedAtMillis).toLocaleTimeString("zh-TW",
      {hour:"2-digit",minute:"2-digit"});
    const historical = !!physical.historical || !!latest?.mockActive;
    const label = (historical ? "最後真實 GPS（歷史快照） · " : "最近真實 GPS · ") + when;
    if (!realMarker) realMarker = new maplibre.Marker({
      element:currentPositionDot(false),anchor:"center"
    }).setLngLat([physical.lon,physical.lat])
      .setPopup(new maplibre.Popup().setText(label)).addTo(map);
    else {
      realMarker.setLngLat([physical.lon,physical.lat]);
      realMarker.getPopup()?.setText(label);
    }
    realMarker.getElement().dataset.historical = String(historical);
    realMarker.getElement().setAttribute("aria-label",label);
  } else if (realMarker) { realMarker.remove(); realMarker = null; }
  const origin = latest?.origin;
  // Do not pile an origin icon on top of the actual current-position dot.
  const showOrigin = origin && !samePosition(origin,virtual) &&
    !samePosition(origin,physical);
  if (showOrigin) {
    if (!originMarker) originMarker = new maplibre.Marker({
      element:routeOriginPin(),anchor:"center"
    }).setLngLat([origin.lon,origin.lat])
      .setPopup(new maplibre.Popup().setText("路線起點")).addTo(map);
    else originMarker.setLngLat([origin.lon,origin.lat]);
  } else if (originMarker) { originMarker.remove(); originMarker = null; }
  if (chosen && !samePosition(chosen,virtual) && !samePosition(chosen,physical)) {
    if (!selectionMarker) selectionMarker = new maplibre.Marker({color:"#527887"})
      .setLngLat([chosen.lon,chosen.lat])
      .setPopup(new maplibre.Popup().setText("目前選取位置")).addTo(map);
    else selectionMarker.setLngLat([chosen.lon,chosen.lat]);
  } else if (selectionMarker) {
    selectionMarker.remove();
    selectionMarker = null;
  }

  const stops = mode === "ROUTE" ? (latest?.routeStops || []) : [];
  const key = mode + "|" + JSON.stringify(stops);
  if (key !== lastStopsKey) {
    routeMarkers.forEach(marker => marker.remove());
    routeMarkers = stops.map((item,i) =>
      new maplibre.Marker({element:ordinalPin(i + 1,
        i === 0 ? "first" : i === stops.length - 1 ? "last" : "via"),anchor:"center"})
        .setLngLat([item.lon,item.lat])
        .setPopup(new maplibre.Popup().setText("路點 " + (i+1)))
        .addTo(map));
    lastStopsKey = key;
  }
  // Match Android: only moving/paused geometry is active. A parked road must
  // never hide the new draft, and the summary uses the same valid preview.
  const {road, draft} = routePresentation(latest, mode, $("loop").value === "true");
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
  const stops = mode === "ROUTE" ? (latest?.routeStops || []) : [];
  const origin = latest?.origin;
  const busy = latest?.planning || latest?.phase === "MOVING" || latest?.phase === "PAUSED";
  const key = mode + "|" + JSON.stringify(stops) + "|" +
    JSON.stringify(origin) + "|" + busy;
  if (key === lastOrderKey) return;
  lastOrderKey = key;
  const list = $("route-order");
  list.replaceChildren();
  if (mode !== "ROUTE") return;
  const label = "路點";
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
    if (index >= 0 && !busy) {
      const actions = document.createElement("div");
      actions.className = "stop-actions";
      for (const [caption,offset] of [["上移",-1],["下移",1]]) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.textContent = caption;
        btn.setAttribute("aria-label", label + " " + (index + 1) + caption);
        btn.disabled = index + offset < 0 || index + offset >= stops.length;
        btn.addEventListener("click", () =>
          act("/api/points/reorder",{mode,index,offset}));
        actions.appendChild(btn);
      }
      const remove = document.createElement("button");
      remove.type = "button";
      remove.textContent = "刪除";
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
        focusMapPosition(point,16);
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
  const navigating = mode === "ROUTE";
  const stops = latest?.routeStops || [];
  const prepared = routePresentation(latest, mode, $("loop").value === "true").prepared;
  const hasOrigin = !!latest?.origin;
  const moving = latest?.phase === "MOVING";
  const paused = latest?.phase === "PAUSED";
  const busy = moving || paused || !!latest?.planning;
  const loop = $("loop").value === "true";
  const ready = !!prepared?.id && prepared.loop === loop;
  const needSnap = ready && prepared.maxSnapMetres > 40;
  const max = 15;
  $("pointcount").textContent = stops.length + " / " + max + " 個路點 · 按清單順序行走";
  renderRouteOrder();
  $("originlabel").textContent = hasOrigin ? "目前起點：" +
    latest.origin.lat.toFixed(6) + ", " + latest.origin.lon.toFixed(6) :
    "尚未指定起點 · 在地圖選擇後按「設為起點」";
  $("add").disabled = !navigating || busy || stops.length >= max;
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
  $("routehint").textContent = moving || paused
    ? "行走中路點暫時為唯讀；需修改請先確認停止路線。"
    : !hasOrigin ? "先在地圖選取起點，再按「設為起點」。"
    : stops.length === 0 ? "加入目的地，規劃只會預覽不會移動 GPS。"
    : latest?.planning ? "正在查詢步行道路，不會移動手機。"
    : ready ? "路線已預覽。確認道路和起點後可開始。"
    : "請先按「規劃並預覽道路」。";
  if (navigating && !latest?.mockPermissionReady && ready) {
    $("routehint").textContent += " 開始前仍需在手機設定精確定位及模擬位置 App。";
  }
  $("pause").disabled = !moving;
  $("resume").disabled = !paused;
  $("stop").disabled = !moving && !paused;
  $("teleport").disabled = teleportBusy || !connected || !remoteLinkHealthy || moving || paused;
}
function updateCompactWalkControls() {
  const phase = latest?.phase || "IDLE";
  const active = phase === "MOVING" || phase === "PAUSED";
  // A running walk must remain controllable even with the mobile sheet half-open,
  // or when a tablet/desktop drawer is closed. Never require scrolling to Stop.
  const show = connected && active;
  $("compact-walk-controls").classList.toggle("hidden", !show);
  const step = Number(latest?.progress?.estimatedSteps || 0);
  $("compact-walk-summary").textContent = !remoteLinkHealthy
    ? "連線中斷 · 請在手機操作暫停或停止"
    : (phase === "PAUSED" ? "已暫停" : "行走中") + " · 本次估算 " + step + " 步";
  $("compact-pause").classList.toggle("hidden", phase !== "MOVING");
  $("compact-resume").classList.toggle("hidden", phase !== "PAUSED");
  $("compact-pause").disabled = !remoteLinkHealthy || phase !== "MOVING";
  $("compact-resume").disabled = !remoteLinkHealthy || phase !== "PAUSED";
  $("compact-stop").disabled = !remoteLinkHealthy;
  document.body.classList.toggle("has-compact-walk", show);
}
for (const [id,action] of [["compact-pause","pause"],
    ["compact-resume","resume"],["compact-stop","stop"]]) {
  $(id).addEventListener("click", async () => {
    if (action === "stop" &&
        !confirm("確定停止行走並保留目前位置？")) return;
    await act("/api/control", {action});
  });
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
  $("status").textContent = latest?.status || "待命";
  $("mobile-state").textContent = names[phase] || "手機待命";
  $("mobile-steps").textContent = "本次估算 " + count + " 步";
  const real = trustedRealPosition();
  const gpsState = latest?.realGpsState || "locked";
  $("virtual-location-status").textContent = latest?.mockActive && latest?.position ?
    "目前虛擬 GPS · " + latest.position.lat.toFixed(6) + ", " +
      latest.position.lon.toFixed(6) + "（模擬定位中）" :
    "虛擬 GPS 未啟用 · 不代表已有真實衛星定位";
  const activePosition = currentPosition(latest,real);
  $("current-position-icon").dataset.mode = !activePosition || !remoteLinkHealthy ? "unknown" :
    activePosition.simulated ? "mock" : "real";
  $("current-location-label").textContent = !remoteLinkHealthy ? "連線中斷" :
    !activePosition ? (latest?.mockActive ? "模擬位置待確認" : "真實位置待確認") :
    activePosition.simulated ? "目前模擬位置" :
      activePosition.historical ? "最後真實位置（歷史）" : "最近真實位置";
  $("current-coordinate").textContent = activePosition && remoteLinkHealthy ?
    activePosition.point.lat.toFixed(6) + ", " + activePosition.point.lon.toFixed(6) : "—";
  const locationStatus = $("real-location-status");
  locationStatus.dataset.state = real ? "ready" :
    gpsState === "unavailable" ? "error" : gpsState;
  if (real) {
    const captured = new Date(real.capturedAtMillis).toLocaleTimeString("zh-TW",
      {hour:"2-digit",minute:"2-digit"});
    locationStatus.textContent = (real.historical ? "最後真實位置（歷史快照） · " :
      "最近真實位置（手機授權） · ") +
      real.lat.toFixed(6) + ", " + real.lon.toFixed(6) +
      " · " + captured + " 取得" +
      (Number.isFinite(real.accuracy) ?
        " · 精度約 " + Math.round(real.accuracy) + " 公尺" : " · 精度未知");
  } else if (!latest?.realLocationSharingApproved) {
    locationStatus.textContent =
      "真實 GPS 未分享 · 請先在手機 Wi-Fi 控制設定中授權此配對瀏覽器";
  } else if (gpsState === "mock") {
    locationStatus.textContent =
      "目前為模擬 GPS，無可信真實快照。請先切回真實模式取得";
  } else if (gpsState === "locating") {
    locationStatus.textContent = "正在取得經手機授權的非模擬真實位置…";
  } else {
    locationStatus.textContent =
      "真實 GPS 尚無有效快照或已逾 30 分鐘；請返回真實模式重新取得";
  }
  // Keep these tools discoverable even before sharing/locating. The handlers
  // explain the required permission, and never alter the device GPS themselves.
  $("find-real").disabled = !connected || !remoteLinkHealthy;
  $("follow").disabled = !connected || !remoteLinkHealthy;
  $("find-real").title = latest?.realLocationSharingApproved ?
    "查看手機授權的真實位置（不停止模擬）" : "請先於手機 Wi-Fi 設定授權真實位置分享";
  const activeWalk = phase === "MOVING" || phase === "PAUSED";
  $("motion-controls").classList.toggle("is-active", activeWalk);
  document.body.classList.toggle("has-active-controls", activeWalk);
  $("pause").classList.toggle("is-current-action", phase === "MOVING");
  $("resume").classList.toggle("is-current-action", phase === "PAUSED");
  updateCompactWalkControls();
  updateExternalMapLink();
  if (latest?.note && /失敗|無法/.test(latest.note)) setMessage(latest.note);
}
async function refresh() {
  if (!token || refreshing || routeMutationPending) return;
  refreshing = true;
  const requestedRouteVersion = routeStateVersion;
  try {
    const activeToken = token;
    const nextState = await call("/api/state");
    if (token !== activeToken || requestedRouteVersion !== routeStateVersion) return;
    latest = nextState;
    remoteLinkHealthy = true;
    sessionDeadlineMs = Date.now() +
      Math.max(0, Number(latest.sessionExpiresInSeconds) || 0) * 1000;
    updateSessionExpiry();
    $("connection").textContent = "已連結手機";
    current = latest.mockActive ? latest.position : null;
    $("app-version").textContent = typeof latest.appVersionDisplay === "string" ?
      latest.appVersionDisplay : "版本待確認";
    $("app-version").title = latest.appVersionCode != null ?
      "手機 App 版本碼：" + latest.appVersionCode : "手機 App 版本";
    if (!speedEditing) {
      const profile = latest.speedMode || "PRESET";
      $("speed").value = profile === "FIXED" || profile === "RANGE"
        ? profile : (["SLOW","WALK","FAST"].includes(latest.speedPreset) ? latest.speedPreset : "WALK");
      if (Number.isFinite(latest.speedKmh)) $("speed-fixed").value = latest.speedKmh.toFixed(1);
      if (Number.isFinite(latest.speedMinKmh)) $("speed-min").value = latest.speedMinKmh.toFixed(1);
      if (Number.isFinite(latest.speedMaxKmh)) $("speed-max").value = latest.speedMaxKmh.toFixed(1);
    }
    showSpeedFields();
    const labels = {SLOW:"慢走 0.8",WALK:"走路 1.4",FAST:"快走 10.8"};
    $("default-speed").textContent = "預設：" +
      (labels[latest.defaultSpeedPreset] || labels.WALK) + " km/h";
    $("make-speed-default").disabled =
      $("speed").value === "FIXED" || $("speed").value === "RANGE" ||
      (latest.speedMode === "PRESET" && latest.speedPreset === latest.defaultSpeedPreset);
    $("speed-applied").textContent = (latest?.phase === "MOVING" || latest?.phase === "PAUSED") &&
        Number.isFinite(latest.speedAppliedKmh) && latest.speedAppliedKmh > 0
        ? "手機目前套用 " + latest.speedAppliedKmh.toFixed(1) + " km/h" : "";
    updateStatus();
    updateRouteControls();
    renderMap();
    reconcileTeleport(latest);
    refreshSavedClocks();
    if (focusRealWhenReady && trustedRealPosition()) {
      const p = trustedRealPosition();
      focusRealWhenReady = false;
      setMessage("已移至手機授權的最近真實位置，沒有變更 GPS");
      focusMapPosition(p,16);
    }
    if (focusRealWhenReady && ["unavailable","mock","locked"].includes(latest?.realGpsState))
      focusRealWhenReady = false;
  } catch (error) {
    if (token) {
      if (renewingSession && error.message === "等待續期完成，忽略舊狀態")
        return;
      remoteLinkHealthy = false;
      cameraFocus.clear();
      focusRealWhenReady = false;
      $("connection").textContent = "手機連線中斷";
      // Never continue to show previously received precise physical coordinates
      // after the authorised session or network connection is lost.
      if (latest) {
        latest.realPosition = null;
        latest.realLocationSharingApproved = false;
        latest.realGpsState = "locked";
      }
      updateStatus();
      renderMap();
      setMessage(error.message || "無法更新手機狀態");
    }
  } finally {
    refreshing = false;
    if (requestedRouteVersion !== routeStateVersion && !routeMutationPending && token)
      void refresh();
  }
}
async function focusTrustedReal() {
  if (!connected || !remoteLinkHealthy || !token) {
    setMessage("手機未連線，無法取得位置");
    return;
  }
  if (!latest?.realLocationSharingApproved) {
    setMessage("請先在手機 Wi-Fi 控制設定核准此瀏覽器的真實 GPS 分享");
    return;
  }
  const cached = trustedRealPosition();
  if (cached) {
    setMessage(cached.historical ?
      "查看最後真實位置（歷史快照），沒有變更手機 GPS" :
      "查看手機最近真實位置，沒有變更 GPS");
    focusMapPosition(cached,16);
    return;
  }
  if (latest?.mockActive) {
    setMessage("模擬定位中，沒有有效的真實位置快照；可手動恢復真實 GPS 後再取得");
    return;
  }
  focusRealWhenReady = true;
  if (!await act("/api/location/real",{})) focusRealWhenReady = false;
}
$("find-real").addEventListener("click", focusTrustedReal);
$("follow").addEventListener("click", async () => {
  if (latest?.mockActive && latest.position && remoteLinkHealthy) {
    setMessage("已回到目前模擬位置，沒有瞬移");
    focusMapPosition(latest.position,15);
  } else {
    await focusTrustedReal();
  }
});

function labelledText(textValue, tag = "small") {
  const element = document.createElement(tag);
  element.textContent = textValue;
  return element;
}
function countryFlag(countryCode) {
  const path = countryFlagPath(countryCode);
  if (!path) return null;
  const image = document.createElement("img");
  image.className = "saved-country-flag";
  image.alt = ""; // The adjacent country name/code is the accessible label.
  image.addEventListener("error",()=>image.remove(),{once:true});
  image.src = path;
  return image;
}
function refreshSavedClocks() {
  if (!$("saved-dialog").open) return;
  document.querySelectorAll(".saved-clock").forEach(clock => {
    clock.textContent=destinationTime(clock.dataset.timeZone||"");
  });
}
function renderSaved() {
  const list = $("saved-list");
  const previousScroll=list.scrollTop;
  const openDetails=new Set([...list.querySelectorAll("details[data-place-id][open]")]
    .map(item=>item.dataset.placeId));
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
    const flag = countryFlag(place.countryCode);
    const countryRow = document.createElement("div");
    countryRow.className = "saved-country-row";
    if (flag) countryRow.append(flag);
    countryRow.append(labelledText([place.countryName||place.countryCode,place.city]
      .filter(Boolean).join(" · ") || "國家／城市待確認，可補齊舊收藏"));
    card.append(countryRow);
    const clock=labelledText(destinationTime(place.timeZoneId));
    clock.className="saved-clock";
    clock.dataset.timeZone=place.timeZoneId||"";
    card.append(clock);
    const details=document.createElement("details");details.className="saved-details";
    details.dataset.placeId=String(place.id);
    details.open=openDetails.has(String(place.id));
    details.append(labelledText("座標及收藏日期","summary"));
    details.append(labelledText("GPS: "+place.position.lat.toFixed(6)+", "+
      place.position.lon.toFixed(6)));
    if(Number.isFinite(place.createdAtMillis)&&place.createdAtMillis>0)
      details.append(labelledText("收藏於 "+
        new Date(place.createdAtMillis).toLocaleDateString("zh-TW")));
    card.append(details);
    const buttons=document.createElement("div");buttons.className="row";
    const view=document.createElement("button");
    view.textContent="選取";
    view.setAttribute("aria-label","在地圖選取「"+place.name+"」位置，不會移動手機 GPS");
    view.addEventListener("click",()=>{
      showCoordinate(place.position);selectMode("TELEPORT");
      $("saved-dialog").close();
      focusMapPosition(place.position,15);
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
      (route.mode==="FLOWERS"?"舊巡花收藏 · 可轉一般路線":"道路")));
    const button=document.createElement("button");
    button.textContent="載入草稿（仍須預覽）";
    button.addEventListener("click",async()=>{
      if(await act("/api/saved/load",{id:route.id})){
        $("saved-dialog").close();
        selectMode("ROUTE");
        setMessage(route.mode==="FLOWERS"
          ? "舊巡花收藏已轉成一般道路草稿；原收藏保留，請重新預覽"
          : "收藏路線已載入，請先預覽步行道路");
      }
    });
    card.append(button);list.append(card);
  }
  if (!routes.length) list.append(labelledText("沒有符合條件的收藏路線"));
  list.scrollTop=previousScroll; // preserve position across refresh and long lists
}
async function refreshSaved() {
  if (!token || !$("saved-dialog").open) return;
  if (!savedRecords) $("saved-list").textContent="正在讀取收藏…";
  try {
    savedRecords=await call("/api/saved");
    renderSaved();
    $("saved-enrich-status").textContent=savedRecords.enrichmentNote||"";
    $("saved-enrich-button").disabled=!!savedRecords.enrichmentBusy;
  } catch(error){$("saved-enrich-status").textContent=error.message||"無法讀取收藏";}
}
let pendingSavedPoint=null, savedLookupId=null, savedLookupGeneration=0;
$("saved-add-button").addEventListener("click",async()=>{
  if ($("saved-add-button").disabled) return;
  pendingSavedPoint=null;savedLookupId=null;
  $("saved-editor").classList.add("hidden");
  const button = $("saved-add-button");
  const previousLabel = button.textContent;
  try {
    // Only this explicit tap may send the user-selected coordinate to Photon.
    // Map panning / ordinary GPS state polling never triggers reverse geocoding.
    const point=readCoordinate();
    const generation=++savedLookupGeneration;
    pendingSavedPoint={lat:point.lat,lon:point.lon};
    const fallback="位置 "+point.lat.toFixed(5)+", "+point.lon.toFixed(5);
    $("saved-new-position").textContent="GPS: "+point.lat.toFixed(6)+", "+point.lon.toFixed(6);
    $("saved-new-name").value=fallback;
    button.disabled = true;
    button.textContent = "正在取得名稱並收藏…";
    $("saved-enrich-status").textContent = "正在查詢地標、國家、城市與時區；完成後自動收藏";
    const request=await call("/api/saved/place/lookup","POST",point);
    if(generation!==savedLookupGeneration||!$("saved-dialog").open) return;
    savedLookupId=request.lookupId;
    for(let i=0;i<16;i++){
      await pause(800);
      if(generation!==savedLookupGeneration||!$("saved-dialog").open) return;
      const result=await call("/api/saved/place/lookup/status","POST",{
        lookupId:savedLookupId
      });
      if(result.status!=="ready")continue;
      const name=String(result.name||fallback).trim().slice(0,80)||fallback;
      await call("/api/saved/place/add","POST",{
        ...point,name,lookupId:savedLookupId
      });
      if(generation!==savedLookupGeneration||!$("saved-dialog").open) return;
      pendingSavedPoint=null;savedLookupId=null;
      await refreshSaved();
      const placeDetails=[result.countryName || result.countryCode,
        result.city].filter(Boolean).join(" · ");
      $("saved-enrich-status").textContent = name===fallback
        ? "已用座標名稱收藏；可用「補齊舊收藏資訊」再查地名"
        : "已自動命名並收藏「"+name+"」"+(placeDetails?" · "+placeDetails:"");
      return;
    }
    // Only a genuinely failed/timed-out lookup needs the manual fallback.
    savedLookupId=null;
    $("saved-editor").classList.remove("hidden");
    $("saved-new-status").textContent="地名查詢尚未完成，可改名或用座標收藏";
    $("saved-enrich-status").textContent="未取得地名；請確認備用名稱後儲存";
  }catch(error){
    if (pendingSavedPoint && token) {
      savedLookupId=null;
      $("saved-editor").classList.remove("hidden");
      $("saved-new-status").textContent=error.message||"地名查詢失敗，可手動輸入";
    } else {
      $("saved-enrich-status").textContent=error.message||
        "請先在地圖選一點或輸入有效的經緯度";
    }
  }finally{
    button.disabled=false;
    button.textContent=previousLabel;
  }
});
$("saved-cancel").addEventListener("click",()=>{
  savedLookupGeneration++;
  pendingSavedPoint=null;savedLookupId=null;
  $("saved-editor").classList.add("hidden");
});
$("saved-save").addEventListener("click",async()=>{
  if(!pendingSavedPoint){
    $("saved-new-status").textContent="請先選取收藏座標";return;
  }
  const name=$("saved-new-name").value.trim();
  if(!name||name.length>80){
    $("saved-new-status").textContent="收藏名稱必須介於 1 到 80 字";return;
  }
  $("saved-save").disabled=true;
  try {
    await call("/api/saved/place/add","POST",{
      ...pendingSavedPoint,name,lookupId:savedLookupId
    });
    savedLookupGeneration++;
    pendingSavedPoint=null;savedLookupId=null;
    $("saved-editor").classList.add("hidden");
    await refreshSaved();
    $("saved-enrich-status").textContent="已儲存收藏，不會改變手機 GPS";
  } catch(error){
    $("saved-new-status").textContent=error.message||"儲存失敗";
  }finally{$("saved-save").disabled=false;}
});
$("saved-enrich-button").addEventListener("click",async()=>{
  $("saved-enrich-button").disabled=true;
  try {
    const result=await call("/api/saved/place/enrich","POST",{});
    $("saved-enrich-status").textContent=result.message||
      "正在補齊缺少的國家、城市與時區";
    // Bound polling. Preserve scroll and expanded cards while metadata updates.
    for(let i=0;i<50&&$("saved-dialog").open;i++){
      await pause(1500);
      savedRecords=await call("/api/saved");
      renderSaved();
      $("saved-enrich-status").textContent=savedRecords.enrichmentNote||"";
      if(!savedRecords.enrichmentBusy)break;
    }
  }catch(error){
    $("saved-enrich-status").textContent=error.message||"資料補齊失敗";
  }finally{$("saved-enrich-button").disabled=false;}
});
$("show-saved").addEventListener("click",()=>{
  savedRecords=null;
  savedLookupGeneration++;
  pendingSavedPoint=null;savedLookupId=null;
  $("saved-editor").classList.add("hidden");
  $("saved-enrich-status").textContent="";
  $("saved-dialog").showModal();
  $("saved-search").value="";
  refreshSaved();
});
$("close-saved").addEventListener("click",()=>$("saved-dialog").close());
$("saved-search").addEventListener("input",renderSaved);
// Update only the clock text once per minute. Rebuilding the list would reset
// its scroll position and close expanded place details while the user reads them.
setInterval(()=>{if(!document.hidden)refreshSavedClocks();},60000);
setInterval(updateSessionExpiry,1000);
setInterval(() => { if (!document.hidden && connected && token) refresh(); }, 2000);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && token) refresh();
});
