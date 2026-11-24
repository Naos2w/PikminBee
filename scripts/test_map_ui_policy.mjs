import {test} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import { BRIGHT_STYLE_URL, samePosition, currentPosition, createCameraFocus, visibleMapPadding, countryFlagPath, destinationTime } from "../app/src/main/assets/lan/map-ui-policy.mjs";

test("country flags are local PNGs with safe two-letter names",()=>{
  for (const code of ["TW","jp","US","GB","NP","XK"]) {
    const path=countryFlagPath(code);
    assert.equal(path,"/flags/"+code.toLowerCase()+".png");
    const png=readFileSync(new URL("../app/src/main/assets/lan"+path,import.meta.url));
    assert.deepEqual([...png.subarray(0,8)],[137,80,78,71,13,10,26,10]);
  }
  for (const bad of [null,"","TWN","../tw","/tw.png","1A","T/","東京"])
    assert.equal(countryFlagPath(bad),null);
});
test("destination clock needs no physical GPS, respects DST and handles unknown zones",()=>{
  const winter=new Date("2026-11-22T12:00:00Z");
  const summer=new Date("2026-07-22T12:00:00Z");
  assert.equal(destinationTime("Asia/Taipei",winter),"當地時間 20:00");
  assert.equal(destinationTime("Asia/Tokyo",winter),"當地時間 21:00");
  assert.equal(destinationTime("America/New_York",winter),"當地時間 07:00");
  assert.equal(destinationTime("America/New_York",summer),"當地時間 08:00");
  assert.equal(destinationTime("Asia/Kathmandu",winter),"當地時間 17:45");
  assert.equal(destinationTime("UTC",new Date("2026-11-22T00:00:00Z")),"當地時間 00:00");
  for (const bad of [null,"","invalid/City","+02:00"])
    assert.equal(destinationTime(bad,winter),"當地時間待確認，可補齊舊收藏");
});

const taipei = {lat:25.033,lon:121.5654};
const tokyo = {lat:35.681236,lon:139.767125};
const mapStub = (width = 600,height = 400) => {
  const calls = [];
  return {calls, getContainer:()=>({getBoundingClientRect:()=>({width,height})}),
    stop:()=>calls.push("stop"), resize:()=>calls.push("resize"), getZoom:()=>16,
    jumpTo:opts=>calls.push(opts)};
};

test("accepted teleport moves camera synchronously, without fly or state/tile wait", () => {
  const camera = createCameraFocus(), map = mapStub();
  camera.request(tokyo);
  assert.equal(camera.flush(map),true);
  assert.deepEqual(map.calls,["stop","resize",{
    center:[tokyo.lon,tokyo.lat],zoom:16,padding:{top:0,right:0,bottom:0,left:0}
  }]);
  assert.equal(camera.flush(map),false); // never recenter on later polls
});
test("unmeasured or unavailable map keeps only the most recent request", () => {
  const camera = createCameraFocus();
  camera.request(taipei); assert.equal(camera.flush(null),false);
  assert.equal(camera.flush(mapStub(0,0)),false);
  camera.request(tokyo);
  const map = mapStub();
  assert.equal(camera.flush(map),true);
  assert.deepEqual(map.calls[2].center,[tokyo.lon,tokyo.lat]);
});
const rect = (left,top,width,height) => ({left,top,width,height,right:left+width,bottom:top+height});
test("desktop floating drawer changes only the explicit camera padding, not the canvas",()=>{
  const canvas = rect(0,0,1280,800);
  const tools = rect(1224,580,44,190);
  const rail = rect(12,90,62,185);
  const panel = rect(914,67,354,490);
  const collapsed = rect(1224,67,44,44);
  const openPadding = visibleMapPadding(canvas,[rail,panel,tools]);
  const closedPadding = visibleMapPadding(canvas,[rail,collapsed,tools]);
  assert.ok(openPadding.right > closedPadding.right);
  const camera = createCameraFocus(), map = mapStub(1280,800);
  let measures=0;
  const getPadding=()=>{measures++;return openPadding};
  assert.equal(camera.flush(map,getPadding),false);
  assert.equal(measures,0);
  camera.request(tokyo);
  assert.equal(camera.flush(map,getPadding),true);
  assert.deepEqual(map.calls[2].padding,openPadding);
  assert.equal(camera.flush(map,()=>{throw Error("drawer toggled without a focus")}),false);
  assert.equal(map.calls.length,3);
});
test("mobile safe target avoids the floating sheet and the stationary right controls",()=>{
  const canvas = rect(0,0,390,844);
  const overlays=[rect(8,8,322,64),rect(8,78,242,50),
    rect(8,420,324,330),rect(338,560,44,190),rect(8,756,374,64)];
  const p=visibleMapPadding(canvas,overlays);
  const x=(p.left+canvas.width-p.right)/2,y=(p.top+canvas.height-p.bottom)/2;
  assert.ok(x>=0&&x<=canvas.width&&y>=0&&y<=canvas.height);
  for (const r of overlays) assert.ok(x<r.left||x>r.right||y<r.top||y>r.bottom);
  assert.ok(y<420, "focus must be above the open sheet");
});
test("safe rectangle clips offscreen overlays and handles nonzero canvas origins",()=>{
  assert.deepEqual(visibleMapPadding(rect(100,50,600,400),[]),{top:0,right:0,bottom:0,left:0});
  const p=visibleMapPadding(rect(100,50,600,400),[rect(80,0,90,500)],0);
  assert.deepEqual(p,{top:0,right:0,bottom:0,left:70});
  assert.deepEqual(visibleMapPadding(rect(0,0,100,100),[rect(-200,-200,30,30)]),{top:0,right:0,bottom:0,left:0});
});
test("session revoke or manual drag clears any pending focus",()=>{
  const camera=createCameraFocus(), map=mapStub();
  camera.request(tokyo); camera.clear();
  assert.equal(camera.flush(map),false);
  assert.equal(map.calls.length,0);
});
test("real and simulated positions stay distinct, including historical GPS",()=>{
  const physical={...taipei,historical:true};
  assert.deepEqual(currentPosition({mockActive:true,position:tokyo},physical),
    {point:tokyo,simulated:true,historical:false});
  assert.deepEqual(currentPosition({mockActive:false,position:tokyo},physical),
    {point:physical,simulated:false,historical:true});
  assert.equal(currentPosition({mockActive:false,position:tokyo},null),null);
  assert.equal(currentPosition(null,null),null);
});
test("overlap uses metres, handles dateline and rejects invalid points",()=>{
  assert.equal(samePosition(taipei,{lat:taipei.lat+.00001,lon:taipei.lon}),true);
  assert.equal(samePosition(taipei,tokyo),false);
  assert.equal(samePosition({lat:0,lon:179.99999},{lat:0,lon:-179.99999}),true);
  assert.equal(samePosition(null,tokyo),false);
  assert.equal(samePosition({lat:NaN,lon:0},tokyo),false);
});
test("shared dark palette adjusts only Bright paint, not routes, tiles or layout",()=>{
  assert.equal(BRIGHT_STYLE_URL,"https://tiles.openfreemap.org/styles/bright");
  const groups=JSON.parse(readFileSync(new URL("../app/src/main/assets/lan/bright-dark-palette.json",import.meta.url)));
  const seen=new Set();
  for(const g of groups){
    assert.ok(["background-color","fill-color","fill-outline-color","line-color","text-color","text-halo-color"].includes(g.property));
    assert.ok(typeof g.value==="string" || Array.isArray(g.value));
    for(const id of g.layers) {
      assert.ok(!id.startsWith("bee-"));
      const key=id+"|"+g.property;
      assert.ok(!seen.has(key),key);
      seen.add(key);
    }
  }
  for(const required of ["background|background-color","water|fill-color","building|fill-color","park|fill-color","highway-minor|line-color","highway-motorway|line-color","highway-minor-casing|line-color","label_city|text-color","label_city|text-halo-color"])
    assert.ok(seen.has(required),required);
});
