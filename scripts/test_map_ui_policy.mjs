import {test} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import { BRIGHT_STYLE_URL, samePosition, currentPosition, createCameraFocus } from "../app/src/main/assets/lan/map-ui-policy.mjs";

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
