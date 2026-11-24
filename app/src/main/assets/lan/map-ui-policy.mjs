/** Pure helpers shared by the controller and its regression tests. */
export const BRIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/bright";
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
      map.stop();
      map.resize();
      map.jumpTo({center:[point.lon,point.lat], zoom:Math.max(zoom,map.getZoom()), padding});
      pending = null;
      return true;
    }
  };
}
