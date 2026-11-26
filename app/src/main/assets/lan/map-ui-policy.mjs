/** Pure helpers shared by the controller and its regression tests. */
export const BRIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/bright";

/** The flag URL is local and can never be a user-supplied asset path. */
export function countryFlagPath(countryCode) {
  const code = typeof countryCode === "string" ? countryCode.trim().toLowerCase() : "";
  return /^[a-z]{2}$/.test(code) ? "/flags/" + code + ".png" : null;
}
/** Destination date/time is independent of physical GPS and browser local time.
 * Conservative TW/JP fallback: IANA tzdb zone1970.tab (TW and JP entries).
 * Keep in parity with Android SavedPlaceTimePolicy.effectiveZone. */
export function destinationTime(zone, date = new Date(), countryCode = null) {
  const country = typeof countryCode === "string" ? countryCode.trim().toUpperCase() : "";
  const fallback = country === "TW" ? "Asia/Taipei" : country === "JP" ? "Asia/Tokyo" : null;
  for (const id of [zone, fallback]) {
    if (typeof id !== "string" || !id || id.length > 90 || /^[+-]/.test(id)) continue;
    try {
      const parts = Object.fromEntries(new Intl.DateTimeFormat("en-GB", {
        timeZone:id, year:"numeric", month:"2-digit", day:"2-digit",
        hour:"2-digit", minute:"2-digit", hourCycle:"h23"
      }).formatToParts(date).map(p=>[p.type,p.value]));
      const day = parts.month + "/" + parts.day;
      const year = day === "12/31" || day === "01/01" ? parts.year + "/" : "";
      return year + day + " " + parts.hour + ":" + parts.minute;
    } catch (_) { /* Try the bounded country fallback, or leave unknown. */ }
  }
  return "—/— —:—";
}
export function samePosition(a,b, toleranceMetres = 3) {
  if (!a || !b || ![a.lat,a.lon,b.lat,b.lon].every(Number.isFinite)) return false;
  const dy = (a.lat-b.lat)*111320;
  const dl = ((a.lon-b.lon+540)%360)-180;
  const dx = dl*111320*Math.cos((a.lat+b.lat)*Math.PI/360);
  return Math.hypot(dx,dy) <= toleranceMetres;
}
export function currentPosition(state, physical) {
  return state?.mockActive && state.position ? {point:state.position, simulated:true, historical:false}
    : physical ? {point:physical,simulated:false,historical:!!physical.historical} : null;
}
/** Use the confirmed phone test pace; older snapshots may omit the label. */
export function appliedSpeedLabel(state) {
  if (!["MOVING","PAUSED"].includes(state?.phase)) return "";
  if (typeof state.speedAppliedDisplay === "string" && state.speedAppliedDisplay)
    return state.speedAppliedDisplay;
  const speed = state.speedAppliedKmh;
  return Number.isFinite(speed) && speed > 0
    ? (state.phase === "PAUSED" ? "恢復速度 " : "已套用 ") + Number(speed.toFixed(1)) + " km/h"
    : "速度待確認";
}

/** Consume only new, authoritative arrival events from the paired phone.
 * The initial state is a baseline (no replay after pairing/refresh). Retain the
 * last observed ID even when a poll has no arrival, so dismissals stay final. */
export function createRouteArrivalNotice() {
  let initialized = false, seen = null;
  return {
    reset() { initialized = false; seen = null; },
    observe(state) {
      const id = state?.routeArrivalId;
      const arrival = state?.phase === "PARKED" && state?.mode === "ROUTE" &&
        state?.mockActive === true && typeof id === "string" && id.length > 0 ? id : null;
      if (!initialized) {
        initialized = true;
        seen = arrival;
        return "hide";
      }
      if (!arrival) return "hide";
      if (arrival === seen) return "none";
      seen = arrival;
      return "show";
    }
  };
}

/** The running route and the current draft are different states. A parked
 * route is history, not an active road; never let it mask a new preview. */
export function routePresentation(state, mode, loop) {
  const active = state?.mode === "ROUTE" &&
    (state.phase === "MOVING" || state.phase === "PAUSED");
  const candidate = state?.previews?.ROUTE;
  const prepared = mode === "ROUTE" && !active && !state?.planning &&
    candidate?.id && candidate.loop === loop && candidate.geometry?.length >= 2
    ? candidate : null;
  const road = mode !== "ROUTE" ? [] : active ? (state.geometry || []) :
    (prepared?.geometry || []);
  const stops = state?.routeStops || [];
  const draft = mode === "ROUTE" && !active && !prepared && state?.origin && stops.length
    ? [state.origin, ...stops] : [];
  return {active, prepared, road, draft};
}

/** Find the largest clear rectangle in the map canvas. All bounds are
 * viewport CSS pixels; overlays are clipped to the canvas before evaluation.
 * Sweep the possible horizontal spans and merge their blocked Y intervals.
 * This handles corner cards without wasting the full width/height as padding.
 * The returned camera padding is only applied on an explicit focus action. */
export function visibleMapPadding(viewport, overlays, margin = 8) {
  const w = viewport.width, h = viewport.height;
  const zero = {top:0,right:0,bottom:0,left:0};
  if (!(w > 0 && h > 0)) return zero;
  const clipped = (overlays || []).filter(r=>r.width>0 && r.height>0)
    .map(r=>({
      l:Math.max(0,r.left-viewport.left-margin),
      r:Math.min(w,r.right-viewport.left+margin),
      t:Math.max(0,r.top-viewport.top-margin),
      b:Math.min(h,r.bottom-viewport.top+margin)
    })).filter(r=>r.r>r.l && r.b>r.t);
  const xs = [...new Set([0,w,...clipped.flatMap(r=>[r.l,r.r])])].sort((a,b)=>a-b);
  let best=null, score=-1, bestDistance=Infinity;
  const consider=(l,r,t,b)=>{
    if(r-l<1 || b-t<1)return;
    const area=(r-l)*(b-t);
    const distance=Math.hypot((l+r-w)/2,(t+b-h)/2);
    if(area>score || (area===score && distance<bestDistance)){
      score=area;bestDistance=distance;best={l,r,t,b};
    }
  };
  for(let i=0;i<xs.length-1;i++)for(let j=i+1;j<xs.length;j++){
    const l=xs[i],r=xs[j];
    const blocked=clipped.filter(o=>o.l<r && o.r>l).sort((a,b)=>a.t-b.t);
    let y=0;
    for(const o of blocked){
      if(o.t>y)consider(l,r,y,o.t);
      y=Math.max(y,o.b);
    }
    if(y<h)consider(l,r,y,h);
  }
  return best ? {top:best.t,right:w-best.r,bottom:h-best.b,left:best.l} : zero;
}

/** Last accepted explicit focus wins. Do not wait for tile downloads or state polling. */
export function createCameraFocus() {
  let pending = null;
  return {
    clear() { pending = null; },
    request(point, zoom = 15) { pending = {point,zoom}; },
    flush(map, padding = {top:0,right:0,bottom:0,left:0}) {
      if (!pending || !map) return false;
      const rect = map.getContainer().getBoundingClientRect();
      if (rect.width < 1 || rect.height < 1) return false;
      const {point,zoom} = pending;
      const safePadding = typeof padding === "function" ? padding(rect) : padding;
      map.stop();
      map.resize();
      map.jumpTo({center:[point.lon,point.lat], zoom:Math.max(zoom,map.getZoom()), padding:safePadding});
      pending = null;
      return true;
    }
  };
}
