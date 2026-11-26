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
  // Actual coordinate input in the packaged UI. Selection is explicit, and no
  // phone or external geocoding endpoint is involved.
  const paste=async text=>{
    await until(()=>evaluate("!document.querySelector('#set-origin').disabled"),"previous route edit settled");
    await evaluate("(()=>{const field=document.querySelector('#coordinate');field.value="+JSON.stringify(text)+";field.dispatchEvent(new Event('input',{bubbles:true}));return field.value})()");
  };
  const dms="25°01′58.8″N 121°33′55.44″E";
  await paste(dms);
  assert.equal(await evaluate("document.querySelector('#coordinate').value"),dms);
  await evaluate("document.querySelector('#set-origin').click();true");
  await until(()=>evaluate("document.querySelector('#coordinate').value==='25.033000, 121.565400'"),"DMS input normalisation");
  await paste("33°30′S 70°40′W");
  await evaluate("document.querySelector('#set-origin').click();true");
  await until(()=>evaluate("document.querySelector('#coordinate').value==='-33.500000, -70.666667'"),"DDM input normalisation");
  await paste("25°60′N 121°0′E");
  await evaluate("document.querySelector('#set-origin').click();true");
  await until(()=>evaluate("document.querySelector('#message').textContent.includes('經緯度格式或範圍無效')"),"invalid angular input rejected");
  await paste("0, 0");
  await evaluate("document.querySelector('#set-origin').click();true");
  await until(()=>evaluate("document.querySelector('#coordinate').value==='0.000000, 0.000000' && document.querySelector('#external-map').href.includes('/0.00000/0.00000')"),"zero coordinate remains valid");
  console.log("Coordinate input: DD/DMS/DDM paste, explicit confirmation and invalid input verified in Chromium.");
  await evaluate("document.querySelector('.ordinal-pin[aria-label=\"路點 2\"]').click();true");
  await until(()=>evaluate("!!document.querySelector('.maplibregl-popup-content')"),"route popup");
  const popupStyle=()=>evaluate("(()=>{const c=document.querySelector('.maplibregl-popup-content'), p=c.parentElement, t=p.querySelector('.maplibregl-popup-tip'), b=c.querySelector('.maplibregl-popup-close-button');return {text:c.textContent,bg:getComputedStyle(c).backgroundColor,fg:getComputedStyle(c).color,tip:[getComputedStyle(t).borderTopColor,getComputedStyle(t).borderBottomColor,getComputedStyle(t).borderLeftColor,getComputedStyle(t).borderRightColor],close:getComputedStyle(b).color,closeHeight:b.getBoundingClientRect().height}})()");
  async function choose(theme){
    await evaluate("(()=>{const s=document.querySelector('#web-appearance');s.value="+JSON.stringify(theme)+";s.dispatchEvent(new Event('change'));return true})()");
  }
  await choose("dark");const dark=await popupStyle();
  await choose("light");const light=await popupStyle();
  assert.ok(dark.text.includes("路點 2") && light.text.includes("路點 2"));
  assert.equal(dark.bg,"rgb(28, 43, 52)");assert.equal(dark.fg,"rgb(234, 242, 242)");
  assert.equal(light.bg,"rgb(255, 255, 255)");assert.equal(light.fg,"rgb(32, 49, 58)");
  for(const p of [dark,light]){assert.ok(p.tip.includes(p.bg));assert.equal(p.close,p.fg);assert.equal(p.closeHeight,30);}
  await evaluate("document.querySelector('.maplibregl-popup-close-button').click();true");
  // Settings is a small Web-only panel: no dead phone-only categories.
  const mapBounds=()=>evaluate("(()=>{const r=document.querySelector('#map').getBoundingClientRect();return [r.x,r.y,r.width,r.height]})()");
  const mapBeforeSettings=await mapBounds();
  await evaluate("document.querySelector('#mode-settings').click();true");
  await until(()=>evaluate("document.body.dataset.mode==='settings' && !document.querySelector('#web-settings').classList.contains('hidden')"),"compact settings open");
  assert.equal(await evaluate("document.querySelectorAll('#web-settings [data-web-theme-option]').length"),3);
  assert.equal(await evaluate("document.querySelectorAll('#web-settings .settings-group').length"),0);
  await evaluate("document.querySelector('[data-web-theme-option=dark]').click();true");
  assert.ok(await evaluate("document.documentElement.dataset.webTheme==='dark' && document.querySelector('[data-web-theme-option=dark]').getAttribute('aria-pressed')==='true' && localStorage.getItem('pikminbee-web-theme')==='dark'"));
  await evaluate("document.querySelector('[data-web-theme-option=light]').click();true");
  assert.deepEqual(await mapBounds(),mapBeforeSettings);
  await evaluate("document.querySelector('#web-settings-close').click();true");
  await until(()=>evaluate("document.body.dataset.mode==='route' && document.querySelector('#web-settings').classList.contains('hidden')"),"return to original route mode");
  assert.deepEqual(await mapBounds(),mapBeforeSettings);
  console.log("Compact Web settings: three theme actions, persistence and unchanged map canvas verified.");
  // Real pointer input against the isolated synthetic server. No phone commands.
  const fixtureState=()=>fetch(url+"/api/state").then(r=>r.json());
  await until(()=>evaluate("document.querySelectorAll('#route-order .stop-grip').length===3"),"three editable waypoints");
  const oldDraft=await fixtureState();
  await evaluate("document.querySelector('#route-order').scrollIntoView({block:'center'});document.querySelector('#route-order').scrollTop=0;true");
  const bounds=await evaluate("(()=>{const handles=[...document.querySelectorAll('#route-order .stop-grip')];return handles.map(h=>{const r=h.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})})()");
  assert.equal(bounds.length,3);
  await call("Input.dispatchMouseEvent",{type:"mouseMoved",x:bounds[0].x,y:bounds[0].y});
  await call("Input.dispatchMouseEvent",{type:"mousePressed",x:bounds[0].x,y:bounds[0].y,button:"left",buttons:1,clickCount:1});
  await until(()=>evaluate("!!document.querySelector('.stop-dragging')"),"pointer capture on drag grip");
  await call("Input.dispatchMouseEvent",{type:"mouseMoved",x:bounds[2].x,y:bounds[2].y,button:"left",buttons:1});
  await until(()=>evaluate("[...document.querySelectorAll('#route-order .stop-item[data-index]')].at(-1).dataset.index==='0'"),"live multi-row reorder");
  assert.equal((await fixtureState()).routeRevision,oldDraft.routeRevision,"no request until pointer release");
  await call("Input.dispatchMouseEvent",{type:"mouseReleased",x:bounds[2].x,y:bounds[2].y,button:"left",buttons:0,clickCount:1});
  const moved=[...oldDraft.routeStops.slice(1),oldDraft.routeStops[0]];
  await until(async()=>JSON.stringify((await fixtureState()).routeStops)===JSON.stringify(moved),"atomic drag commit");
  assert.equal((await fixtureState()).routeRevision,oldDraft.routeRevision+1);
  await until(()=>evaluate("document.querySelectorAll('#route-order .stop-grip').length===3"),"confirmed rows after drag");
  const stale=await fetch(url+"/api/points/reorder",{method:"POST",headers:{"Content-Type":"application/json"},
    body:JSON.stringify({mode:"ROUTE",from:0,to:2,expectedRevision:oldDraft.routeRevision})});
  assert.equal(stale.status,409);
  assert.deepEqual((await fixtureState()).routeStops,moved);
  await evaluate("window.confirm=()=>false;document.querySelector('#clear').click();true");
  assert.deepEqual((await fixtureState()).routeStops,moved,"cancelled clear must not change draft");
  await evaluate("document.querySelector('#route-order .stop-grip').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));true");
  const keyboardMoved=[moved[1],moved[0],moved[2]];
  await until(async()=>JSON.stringify((await fixtureState()).routeStops)===JSON.stringify(keyboardMoved),"keyboard reorder");
  console.log("Route editing: pointer drag across multiple rows, one commit, stale revision rejection, cancelled clear and accessible keyboard reorder verified.");
  await evaluate("document.querySelector('#show-saved').click();true");
  await until(()=>evaluate("document.querySelectorAll('.saved-country-flag').length===2 && [...document.querySelectorAll('.saved-country-flag')].every(i=>i.complete&&i.naturalWidth>0)"),"two decoded local flags");
  const saved=await evaluate("({flags:[...document.querySelectorAll('.saved-country-flag')].map(i=>[new URL(i.src).pathname,i.naturalWidth]),clocks:[...document.querySelectorAll('.saved-clock')].map(i=>i.textContent)})");
  assert.deepEqual(saved.flags.map(a=>a[0]).sort(),["/flags/jp.png","/flags/tw.png"]);
  assert.equal(saved.clocks.filter(c=>/^(?:\d{4}\/)?\d{2}\/\d{2} \d{2}:\d{2}$/.test(c)).length,2);
  assert.ok(saved.clocks.includes("—/— —:—"));
  assert.ok(saved.clocks.every(c=>!c.includes("時差")));
  const cardLayout=()=>evaluate("(()=>{const list=document.querySelector('#saved-list');return {width:list.clientWidth,scrollWidth:list.scrollWidth,cards:[...list.querySelectorAll('.saved-place-card')].map(c=>{const top=c.querySelector('.saved-place-top').getBoundingClientRect(),clock=c.querySelector('.saved-clock-row').getBoundingClientRect(),coord=c.querySelector('.saved-place-coordinate').getBoundingClientRect(),more=c.querySelector('.saved-place-more').getBoundingClientRect();return {text:c.querySelector('.saved-place-select').innerText,clockInside:clock.right<=top.right+1,coordinateBelow:coord.top>=top.bottom-1,menuSize:more.height,hidden:c.querySelector('.saved-place-actions').classList.contains('hidden')}})}})()");
  const desktopCards=await cardLayout();
  assert.equal(desktopCards.cards.length,3);
  assert.ok(desktopCards.cards.every(c=>c.clockInside&&c.coordinateBelow&&c.hidden&&c.menuSize>=40&&!c.text.includes("收藏於")));
  await call("Emulation.setDeviceMetricsOverride",{width:320,height:720,deviceScaleFactor:1,mobile:true});
  await delay(300);
  const mobileCards=await cardLayout();
  assert.equal(mobileCards.cards.length,3);
  assert.ok(mobileCards.scrollWidth<=mobileCards.width+1,"saved list does not overflow at 320px");
  assert.ok(mobileCards.cards.every(c=>c.clockInside&&c.coordinateBelow&&c.hidden));
  await evaluate("document.querySelector('.saved-place-more').click();true");
  assert.ok(await evaluate("document.querySelector('#saved-dialog').open && !document.querySelector('.saved-place-actions').classList.contains('hidden') && document.querySelector('.saved-place-actions').innerText.includes('複製座標')"));
  // Exercise menu actions against synthetic records only. Clipboard and dialogs
  // are stubbed inside this temporary test browser, not on the user's desktop.
  await evaluate("Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async value=>{window.__copied=value}}});[...document.querySelectorAll('.saved-place-actions:not(.hidden) button')].find(b=>b.textContent==='複製座標').click();true");
  await until(()=>evaluate("window.__copied==='35.681236, 139.767125'"),"copy synthetic coordinate");
  await evaluate("window.prompt=()=> '重新命名的合成收藏';[...document.querySelectorAll('.saved-place-actions:not(.hidden) button')].find(b=>b.textContent==='改名').click();true");
  await until(()=>evaluate("[...document.querySelectorAll('.saved-place-name')].some(n=>n.textContent==='重新命名的合成收藏')"),"rename synthetic record");
  await evaluate("window.prompt=()=> '東京車站（合成資料）';[...document.querySelectorAll('.saved-place-actions:not(.hidden) button')].find(b=>b.textContent==='改名').click();true");
  await until(()=>evaluate("[...document.querySelectorAll('.saved-place-name')].some(n=>n.textContent.includes('東京車站'))"),"restore synthetic name");
  await evaluate("window.confirm=()=>true;[...document.querySelectorAll('.saved-place-card')].find(c=>c.innerText.includes('舊收藏（合成資料）')).querySelector('.saved-place-more').click();[...document.querySelectorAll('.saved-place-actions:not(.hidden) button')].find(b=>b.textContent==='刪除').click();true");
  await until(()=>evaluate("document.querySelectorAll('.saved-place-card').length===2"),"delete synthetic record");
  await evaluate("document.querySelector('#saved-tab-routes').click();true");
  assert.ok(await evaluate("document.querySelector('#saved-tab-routes').getAttribute('aria-pressed')==='true' && document.querySelectorAll('.saved-place-card').length===0"));
  await evaluate("document.querySelector('#saved-tab-places').click();document.querySelector('#saved-search').value='台灣';document.querySelector('#saved-search').dispatchEvent(new Event('input'));true");
  assert.equal(await evaluate("document.querySelectorAll('.saved-place-card').length"),1);
  await evaluate("document.querySelector('#saved-search').value='';document.querySelector('#saved-search').dispatchEvent(new Event('input'));true");
  await evaluate("[...document.querySelectorAll('.saved-place-select')].find(b=>b.textContent.includes('東京車站')).click();true");
  await until(()=>evaluate("!document.querySelector('#saved-dialog').open && document.querySelector('#coordinate').value==='35.681236, 139.767125' && document.querySelector('#message').textContent.includes('沒有自動瞬移')"),"favourite selection without teleport");
  await call("Emulation.clearDeviceMetricsOverride");
  await evaluate("document.querySelector('#mode-route').click();true");
  console.log(JSON.stringify({desktopCards,mobileCards,cardInteractions:"menu, tabs, metadata search, explicit map selection"},null,2));
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
  routeLayers.push(await verifyLayers("stale",[origin,b],[]));
  await until(()=>evaluate("document.querySelector('#previewinfo').textContent.includes('起點已變動') && document.querySelector('#start').disabled"),"stale drawing retained, start disabled");
  routeLayers.push(await verifyLayers("planning",[],[origin,b]));
  await until(()=>evaluate("!document.querySelector('#cancel-plan').classList.contains('hidden')"),"cancel planning visible");
  await evaluate("document.querySelector('#cancel-plan').click();true");
  await until(async()=>!(await fixtureState()).planning,"cancel synthetic plan");
  routeLayers.push(await verifyLayers("failed",[],[origin,b]));
  await until(()=>evaluate("document.querySelector('#routehint').textContent.includes('道路規劃失敗')"),"planning failure visible");
  routeLayers.push(await verifyLayers("parked",[],[origin,b]));
  await until(()=>evaluate("!document.querySelector('#save-route').disabled && !document.querySelector('#routehint').textContent.includes('道路規劃失敗')"),"fresh parked draft and save action");
  await evaluate("(()=>{const loop=document.querySelector('#loop');loop.value='true';loop.dispatchEvent(new Event('change'));return true})()");
  await until(async()=> (await fixtureState()).routeLoop===true,"shared loop option");
  await until(()=>evaluate("!document.querySelector('#save-route').disabled"),"save route after options commit");
  const savedDraft=await fixtureState();
  const savedRoutes=async()=> (await (await fetch(url+'/api/saved')).json()).routes;
  await evaluate("window.prompt=()=> '合成步行路線';document.querySelector('#save-route').click();true");
  await until(async()=> (await savedRoutes()).length===1,"save route from route panel");
  const savedRoute=(await savedRoutes())[0];
  assert.deepEqual(savedRoute.origin,savedDraft.origin);
  assert.deepEqual(savedRoute.stops,savedDraft.routeStops);
  assert.equal(savedRoute.loop,true);
  await evaluate("document.querySelector('#show-saved').click();document.querySelector('#saved-tab-routes').click();true");
  await until(()=>evaluate("document.querySelector('.saved-route-card')?.textContent.includes('合成步行路線')"),"saved route card");
  await evaluate("window.prompt=()=> '改名合成路線';[...document.querySelectorAll('.saved-route-card button')].find(b=>b.textContent==='改名').click();true");
  await until(()=>evaluate("document.querySelector('.saved-route-card')?.textContent.includes('改名合成路線')"),"rename route");
  await evaluate("window.confirm=()=>true;[...document.querySelectorAll('.saved-route-card button')].find(b=>b.textContent==='載入').click();true");
  await until(()=>evaluate("!document.querySelector('#saved-dialog').open"),"load route only as draft");
  const loadedDraft=await fixtureState();
  assert.deepEqual(loadedDraft.origin,savedDraft.origin);
  assert.deepEqual(loadedDraft.routeStops,savedDraft.routeStops);
  assert.equal(loadedDraft.routeLoop,true);
  assert.deepEqual(loadedDraft.position,savedDraft.position);
  await evaluate("document.querySelector('#show-saved').click();document.querySelector('#saved-tab-routes').click();true");
  await until(()=>evaluate("!!document.querySelector('.saved-route-card')"),"reopen route library");
  await evaluate("[...document.querySelectorAll('.saved-route-card button')].find(b=>b.textContent==='刪除').click();true");
  await until(async()=> (await savedRoutes()).length===0,"delete route without altering draft");
  assert.deepEqual((await fixtureState()).routeStops,savedDraft.routeStops);
  await evaluate("document.querySelector('#close-saved').click();true");
  console.log('Route favourites: save, origin/loop/order retention, rename, load-only, delete; planning cancellation, visible failure and stale preview verified.');
  routeLayers.push(await verifyLayers("cleared",[],[]));
  // Right-click map actions use the existing UI and only the loopback fixture.
  const contextVisible=()=>evaluate("!document.querySelector('#map-context-menu').classList.contains('hidden')");
  const rightClick=async (x,y)=>{
    await call("Input.dispatchMouseEvent",{type:"mouseMoved",x,y});
    await call("Input.dispatchMouseEvent",{type:"mousePressed",x,y,button:"right",buttons:2,clickCount:1});
    await call("Input.dispatchMouseEvent",{type:"mouseReleased",x,y,button:"right",buttons:0,clickCount:1});
  };
  const mapPoint=await evaluate("(()=>{const r=window.__qaRouteMap.getCanvas().getBoundingClientRect();return {x:Math.round(r.x+r.width*.38),y:Math.round(r.y+r.height*.40)}})()");
  const rightClickMap=()=>rightClick(mapPoint.x,mapPoint.y);
  await until(()=>evaluate("!document.querySelector('#add').disabled && document.querySelector('#pointcount').textContent.startsWith('0 /')"),"empty editable route");
  const beforeMenu=await fixtureState();
  const previousCoordinate=await evaluate("document.querySelector('#coordinate').value");
  await rightClickMap();
  await until(contextVisible,"route context menu");
  assert.equal(await evaluate("document.querySelector('#map-context-label').textContent"),"加入路點");
  assert.equal(await evaluate("document.querySelector('#coordinate').value"),previousCoordinate);
  assert.deepEqual((await fixtureState()).routeStops,beforeMenu.routeStops,"opening the menu never edits the draft");
  await evaluate("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));true");
  assert.equal(await contextVisible(),false);
  await evaluate("window.__qaRouteMap.once('contextmenu',e=>{const p=e.lngLat.wrap();window.__contextEventPoint={lat:Number(p.lat.toFixed(6)),lon:Number(p.lng.toFixed(6))}});true");
  await rightClickMap();
  await until(contextVisible,"reopened route context menu");
  const eventPoint=await evaluate("window.__contextEventPoint");
  assert.ok(eventPoint && Number.isFinite(eventPoint.lat) && Number.isFinite(eventPoint.lon));
  await evaluate("document.querySelector('#map-context-action').click();true");
  await until(async()=> (await fixtureState()).routeStops.length===1,"one synthetic waypoint added");
  const added=await fixtureState();
  assert.equal(added.routeRevision,beforeMenu.routeRevision+1);
  assert.deepEqual(added.routeStops[0],eventPoint);
  assert.equal(added.phase,beforeMenu.phase,"adding a waypoint never starts a route");
  assert.deepEqual(added.position,beforeMenu.position,"adding a waypoint never changes position");
  assert.equal(await contextVisible(),false);
  await evaluate("document.querySelector('#mode-teleport').click();true");
  await rightClickMap();
  assert.equal(await contextVisible(),false,"permission not granted: no test-position action");
  const permission=await fetch(url+"/api/preview/mock-permission",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({approved:true})});
  assert.ok(permission.ok);
  await until(async()=>{await rightClickMap();return await contextVisible()},"authorised synthetic test position menu");
  assert.equal(await evaluate("document.querySelector('#map-context-label').textContent"),"瞬移至此");
  const beforeCancel=await fixtureState();
  await evaluate("window.__contextConfirmCount=0;window.confirm=()=>{window.__contextConfirmCount++;return false};document.querySelector('#map-context-action').click();true");
  assert.equal(await evaluate("window.__contextConfirmCount"),1,"original confirmation is mandatory");
  assert.deepEqual((await fixtureState()).position,beforeCancel.position,"cancelled test leaves position unchanged");
  assert.equal(await contextVisible(),false);
  await rightClickMap();
  await until(contextVisible,"reopened test position menu");
  await evaluate("document.querySelector('#mode-route').click();true");
  assert.equal(await contextVisible(),false,"mode change dismisses stale menu");
  // Check viewport clamping using the real map event at the window edge.
  await evaluate("(()=>{const m=window.__qaRouteMap;m.fire('contextmenu',{lngLat:m.getCenter(),originalEvent:new MouseEvent('contextmenu',{button:2,clientX:innerWidth-1,clientY:innerHeight-1,cancelable:true})});return true})()");
  await until(contextVisible,"edge menu");
  assert.ok(await evaluate("(()=>{const r=document.querySelector('#map-context-menu').getBoundingClientRect();return r.left>=0&&r.top>=0&&r.right<=innerWidth&&r.bottom<=innerHeight})()"));
  await evaluate("document.body.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true}));true");
  assert.equal(await contextVisible(),false,"outside interaction dismisses menu");
  await fetch(url+"/api/preview/route-state",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({scenario:"active"})});
  await until(()=>evaluate("document.querySelector('#add').disabled"),"route editing locked while active");
  await rightClickMap();
  assert.equal(await contextVisible(),false);
  console.log("Map context menu: real right-click, current-mode action, exact synthetic waypoint, cancellation/permission, Escape, outside click, viewport clamp and active-route lock verified.");
  // Completion UI is a server-confirmed, one-time event, not a PARKED guess.
  const arrivalVisible=()=>evaluate("!document.querySelector('#route-arrival-notice').classList.contains('hidden')");
  const scenario=async name=>{
    const r=await fetch(url+"/api/preview/route-state",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({scenario:name})});
    assert.ok(r.ok);
  };
  const followVisible=()=>evaluate("!document.querySelector('#route-follow').classList.contains('hidden')");
  const followEnabled=()=>evaluate("document.querySelector('#route-follow').getAttribute('aria-pressed')==='true'");
  const centredOn=point=>evaluate("(()=>{const c=window.__qaRouteMap.getCenter();return Math.abs(c.lat-"+point.lat+")<0.00001&&Math.abs(c.lng-"+point.lon+")<0.00001})()");
  await until(followVisible,"active route follow control");
  assert.equal(await followEnabled(),false,"following is opt-in");
  const beforeFollow=await fixtureState();
  await evaluate("document.querySelector('#route-follow').click();true");
  await until(followEnabled,"follow selected");
  await until(()=>centredOn(beforeFollow.position),"initial follow camera");
  assert.deepEqual((await fixtureState()).position,beforeFollow.position,"camera action does not mutate phone state");
  await scenario("active-next");
  const nextPosition=(await fixtureState()).position;
  await until(()=>centredOn(nextPosition),"camera follows subsequent confirmed position");
  await evaluate("window.__qaRouteMap.fire('dragstart');true");
  assert.equal(await followEnabled(),false,"manual drag releases following");
  await evaluate("document.querySelector('#route-follow').click();true");
  await until(followEnabled,"follow can be re-enabled explicitly");
  assert.equal(await arrivalVisible(),false,"active route does not alert");
  await scenario("parked");
  await until(async()=>!await followVisible(),"route completion/stop removes follow action");
  assert.equal(await followEnabled(),false,"no following of a parked route");
  console.log("Route camera follow: opt-in, confirmed position updates, no phone mutation, manual drag release, stop clearing verified.");
  await until(()=>evaluate("!document.querySelector('#add').disabled"),"manual parked snapshot");
  assert.equal(await arrivalVisible(),false,"manual stop does not alert");
  await scenario("arrived");
  await until(arrivalVisible,"confirmed arrival notice");
  assert.ok(await evaluate("document.querySelector('#route-arrival-notice').innerText.includes('路徑已完成')"));
  assert.ok(await evaluate("(()=>{const r=document.querySelector('#route-arrival-notice').getBoundingClientRect();return r.left>=0&&r.right<=innerWidth&&r.top>=0&&r.bottom<=innerHeight})()"));
  await evaluate("document.querySelector('#route-arrival-dismiss').click();true");
  assert.equal(await arrivalVisible(),false);
  await delay(2500);
  assert.equal(await arrivalVisible(),false,"polling does not reopen dismissed notice");
  await scenario("active");
  await until(()=>evaluate("document.querySelector('#add').disabled"),"new active route");
  await scenario("arrived");
  await until(arrivalVisible,"second distinct natural arrival");
  await scenario("parked");
  await until(async()=>!await arrivalVisible(),"superseded arrival notice dismissed");
  console.log("Route completion notice: natural arrivals, manual stop, dismissal across polls, subsequent route and stale clearing verified.");
  console.log(JSON.stringify({dark,light,saved,routeLayers,verified:"synthetic browser; no phone or external map tiles"},null,2));
}finally{
  socket?.close();
  browser?.kill();preview?.kill();
  await delay(500);
  await rm(profile,{recursive:true,force:true,maxRetries:2,retryDelay:300}).catch(()=>{});
}
