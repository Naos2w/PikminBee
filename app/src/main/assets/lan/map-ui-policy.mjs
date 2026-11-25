/** Pure helpers shared by the controller and its regression tests. */
export const BRIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/bright";

/** The flag URL is local and can never be a user-supplied asset path. */
export function countryFlagPath(countryCode) {
  const code = typeof countryCode === "string" ? countryCode.trim().toLowerCase() : "";
  return /^[a-z]{2}$/.test(code) ? "/flags/" + code + ".png" : null;
}
/** A destination clock needs no physical GPS reference or browser time-zone guess. */
export function destinationTime(zone, date = new Date()) {
  if (typeof zone !== "string" || !zone || zone.length > 90 || /^[+-]/.test(zone))
    return "當地時間待確認，可補齊舊收藏";
  try {
    const clock = new Intl.DateTimeFormat("en-GB", {
      timeZone: zone, hour:"2-digit", minute:"2-digit", hourCycle:"h23"
    }).format(date);
    return "當地時間 " + clock;
  } catch (_) { return "當地時間待確認，可補齊舊收藏"; }
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
