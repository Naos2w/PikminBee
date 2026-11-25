// Optional local Chromium smoke check. Uses only the loopback synthetic server.
import assert from "node:assert/strict";
import {spawn} from "node:child_process";
import {mkdtemp, readFile, rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";

const chrome = process.env.PIKMINBEE_CHROME || "C:/Program Files/Google/Chrome/Application/chrome.exe";
const port=18766, url="http://127.0.0.1:"+port;
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn,label,timeout=15000){
  const deadline=Date.now()+timeout;
  while(Date.now()<deadline){
    try { const value=await fn(); if(value)return value; } catch {}
    await delay(150);
  }
  throw new Error("Timed out: "+label);
}
const profile=await mkdtemp(join(tmpdir(),"pikminbee-visual-"));
let preview, browser, socket;
try{
  preview=spawn(process.execPath,["scripts/lan_ui_preview.mjs"],{env:{...process.env,PORT:String(port)},stdio:"ignore"});
  await until(async()=> (await fetch(url)).ok,"synthetic preview");
  browser=spawn(chrome,["--headless=new","--no-first-run","--no-default-browser-check","--disable-extensions",
    "--use-gl=angle","--use-angle=swiftshader","--enable-unsafe-swiftshader",
    "--remote-debugging-port=0","--user-data-dir="+profile,"--window-size=1360,860","about:blank"],{stdio:"ignore"});
  const debugPort=(await until(()=>readFile(join(profile,"DevToolsActivePort"),"utf8"),"Chromium CDP port")).split(/\r?\n/)[0];
  const tabs=await (await fetch("http://127.0.0.1:"+debugPort+"/json/list")).json();
  socket=new WebSocket(tabs.find(t=>t.type==="page").webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{socket.addEventListener("open",resolve,{once:true});socket.addEventListener("error",reject,{once:true});});
  const pending=new Map();let id=0;
  function call(method,params={}){
    const n=++id;
    return new Promise((resolve,reject)=>{
      pending.set(n,{resolve,reject});
      socket.send(JSON.stringify({id:n,method,params}));
    });
  }
  socket.addEventListener("message",e=>{
    const m=JSON.parse(e.data);
    if(m.id && pending.has(m.id)){
      const p=pending.get(m.id);pending.delete(m.id);
      m.error?p.reject(Error(m.error.message)):p.resolve(m.result);
    }
    if(m.method==="Runtime.exceptionThrown")
      console.error("Browser exception:",m.params.exceptionDetails?.exception?.description||m.params.exceptionDetails?.text);
    if(m.method==="Fetch.requestPaused"){
      const style={version:8,sources:{},layers:[{id:"background",type:"background",paint:{"background-color":"#172127"}}]};
      call("Fetch.fulfillRequest",{requestId:m.params.requestId,responseCode:200,
        responseHeaders:[{name:"Content-Type",value:"application/json"},{name:"Access-Control-Allow-Origin",value:"*"}],
        body:Buffer.from(JSON.stringify(style)).toString("base64")}).catch(()=>{});
    }
  });
  async function evaluate(expression){
    const r=await call("Runtime.evaluate",{expression,returnByValue:true,awaitPromise:true});
    if(r.exceptionDetails)throw Error(r.exceptionDetails.text);
    return r.result.value;
  }
  await call("Page.enable");
  await call("Runtime.enable");
  await call("Fetch.enable",{patterns:[{urlPattern:"https://tiles.openfreemap.org/styles/bright*"}]});
  await call("Page.navigate",{url});
  await until(()=>evaluate("document.readyState==='complete' && !!document.querySelector('#connect')"),"pair UI");
  // Capture the real map instance through the test browser only; no production hook.
  await evaluate("import('/vendor/maplibre-gl.mjs').then(m=>{const add=m.Map.prototype.addSource;m.Map.prototype.addSource=function(id,source){if(id==='bee-walk'||id==='bee-draft')window.__qaRouteMap=this;return add.call(this,id,source)};return true})");
  await evaluate("document.querySelector('#pin').value='123456';document.querySelector('#connect').click();true");
  try {
    await until(()=>evaluate("document.body.classList.contains('remote-active')"),"mock pairing");
  } catch (error) {
    console.error("Pair UI:",await evaluate("({ready:document.readyState,message:document.querySelector('#pairmessage')?.textContent,buttonDisabled:document.querySelector('#connect')?.disabled})"));
    throw error;
  }
  await evaluate("document.querySelector('#mode-route').click();true");
  await until(()=>evaluate("!!document.querySelector('.ordinal-pin[aria-label=\"路點 2\"]')"),"real MapLibre route marker",20000);
  await evaluate("document.querySelector('.ordinal-pin[aria-label=\"路點 2\"]').click();true");
  await until(()=>evaluate("!!document.querySelector('.maplibregl-popup-content')"),"route popup");
  const popupStyle=()=>evaluate("(()=>{const c=document.querySelector('.maplibregl-popup-content'), p=c.parentElement, t=p.querySelector('.maplibregl-popup-tip'), b=c.querySelector('button');return {text:c.textContent,bg:getComputedStyle(c).backgroundColor,fg:getComputedStyle(c).color,tip:[getComputedStyle(t).borderTopColor,getComputedStyle(t).borderBottomColor,getComputedStyle(t).borderLeftColor,getComputedStyle(t).borderRightColor],close:getComputedStyle(b).color,closeHeight:b.getBoundingClientRect().height}})()");
  async function choose(theme){
    await evaluate("(()=>{const s=document.querySelector('#web-appearance');s.value="+JSON.stringify(theme)+";s.dispatchEvent(new Event('change'));return true})()");
  }
  await choose("dark");const dark=await popupStyle();
  await choose("light");const light=await popupStyle();
  assert.ok(dark.text.includes("路點 2") && light.text.includes("路點 2"));
  assert.equal(dark.bg,"rgb(28, 43, 52)");assert.equal(dark.fg,"rgb(234, 242, 242)");
  assert.equal(light.bg,"rgb(255, 255, 255)");assert.equal(light.fg,"rgb(32, 49, 58)");
  for(const p of [dark,light]){assert.ok(p.tip.includes(p.bg));assert.equal(p.close,p.fg);assert.equal(p.closeHeight,30);}
  await evaluate("document.querySelector('.maplibregl-popup-close-button').click();document.querySelector('#show-saved').click();true");
  await until(()=>evaluate("document.querySelectorAll('.saved-country-flag').length===2 && [...document.querySelectorAll('.saved-country-flag')].every(i=>i.complete&&i.naturalWidth>0)"),"two decoded local flags");
  const saved=await evaluate("({flags:[...document.querySelectorAll('.saved-country-flag')].map(i=>[new URL(i.src).pathname,i.naturalWidth]),clocks:[...document.querySelectorAll('.saved-clock')].map(i=>i.textContent)})");
  assert.deepEqual(saved.flags.map(a=>a[0]).sort(),["/flags/jp.png","/flags/tw.png"]);
  assert.equal(saved.clocks.filter(c=>/^當地時間 \d{2}:\d{2}$/.test(c)).length,2);
  assert.ok(saved.clocks.some(c=>c.includes("當地時間待確認")));
  assert.ok(saved.clocks.every(c=>!c.includes("時差")));
  // Inspect the actual GeoJSON source data rendered by packaged MapLibre.
  const origin=[121.5654,25.033], a=[121.5654,25.043], b=[121.5754,25.033];
  const coords=async()=>evaluate("(async()=>{const m=window.__qaRouteMap;if(!m)return null;const road=m.getSource('bee-walk'),draft=m.getSource('bee-draft');if(!road||!draft)return null;const points=async s=>(await s.getData()).features[0]?.geometry.coordinates||[];return {road:await points(road),draft:await points(draft)}})()");
  const verifyLayers=async(scenario,road,draft)=>{
    const response=await fetch(url+"/api/preview/route-state",{method:"POST",
      headers:{"Content-Type":"application/json"},body:JSON.stringify({scenario})});
    assert.ok(response.ok);
    await until(async()=>JSON.stringify(await coords())===JSON.stringify({road,draft}),
      "route layers "+scenario);
    return {scenario,road,draft};
  };
  const routeLayers=[];
  routeLayers.push(await verifyLayers("active",[origin,a],[]));
  routeLayers.push(await verifyLayers("parked",[],[origin,b]));
  routeLayers.push(await verifyLayers("planning",[],[origin,b]));
  routeLayers.push(await verifyLayers("ready",[origin,b],[]));
  await until(()=>evaluate("document.querySelector('#previewinfo').textContent.includes('1.00 公里') && !document.querySelector('#previewinfo').classList.contains('hidden')"),"new preview summary");
  await evaluate("document.querySelector('#mode-teleport').click();true");
  await until(async()=>JSON.stringify(await coords())===JSON.stringify({road:[],draft:[]}),"other mode clears layers");
  await evaluate("document.querySelector('#mode-route').click();true");
  await until(async()=>JSON.stringify(await coords())===JSON.stringify({road:[origin,b],draft:[]}),"route mode restores B only");
  routeLayers.push(await verifyLayers("failed",[],[origin,b]));
  routeLayers.push(await verifyLayers("cleared",[],[]));
  console.log(JSON.stringify({dark,light,saved,routeLayers,verified:"synthetic browser; no phone or external map tiles"},null,2));
}finally{
  socket?.close();
  browser?.kill();preview?.kill();
  await delay(500);
  await rm(profile,{recursive:true,force:true,maxRetries:2,retryDelay:300}).catch(()=>{});
}
