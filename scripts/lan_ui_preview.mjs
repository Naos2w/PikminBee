/**
 * Loopback-only browser smoke preview for PikminBee's real packaged LAN UI.
 * No Android connection, no real pairing and no mock-GPS movement.
 *
 * Run: node scripts/lan_ui_preview.mjs
 * Open: http://127.0.0.1:18765/
 */
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';

const root = resolve('app/src/main/assets/lan');
const port = Number(process.env.PORT || 18765);
const csp = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
  "img-src 'self' data: blob: https://tiles.openfreemap.org; " +
  "connect-src 'self' https://tiles.openfreemap.org; " +
  "font-src data: https://tiles.openfreemap.org; worker-src 'self' blob:; " +
  "object-src 'none'; frame-ancestors 'none'";
const assets = {
  '/': ['index.html', 'text/html; charset=utf-8'],
  '/controller.js': ['controller.js', 'application/javascript; charset=utf-8'],
  '/map-ui-policy.mjs': ['map-ui-policy.mjs', 'application/javascript; charset=utf-8'],
  '/bright-dark-palette.json': ['bright-dark-palette.json', 'application/json; charset=utf-8'],
  '/vendor/maplibre-gl.mjs': ['vendor/maplibre-gl/maplibre-gl.mjs', 'application/javascript; charset=utf-8'],
  '/vendor/maplibre-gl-worker.mjs': ['vendor/maplibre-gl/maplibre-gl-worker.mjs', 'application/javascript; charset=utf-8'],
  '/vendor/maplibre-gl.css': ['vendor/maplibre-gl/maplibre-gl.css', 'text/css; charset=utf-8'],
};
let state = {
  position: {lat:25.040815,lon:121.463424},
  origin: {lat:25.040815,lon:121.463424},
  mockActive: true,
  sessionExpiresInSeconds:7200,
  appVersionDisplay:'本機預覽',
  realGpsState:'locked',
  realLocationSharingApproved:false,
  realPosition:null,
  realTimeZoneId:null,
  realTimeZoneExpiresAtMillis:null,
  // Synthetic numbered draft for visual QA only; this never reaches a phone.
  routeStops: [
    {lat:25.0441,lon:121.4661},{lat:25.0478,lon:121.4702},
    {lat:25.0494,lon:121.4650}
  ], flowerStops: [], geometry: [],
  previews: {}, phase:'PARKED', mode:'ROUTE', planning:false,
  mockPermissionReady:false, status:'手機待命 · 本機預覽',
  progress: {activeLeg:'起點 → 1',estimatedSteps:0,fraction:0,
    travelledMetres:0,totalMetres:0,waypointCount:0},
  speedPreset:'WALK',defaultSpeedPreset:'WALK'
};
let previewPlaces=[{
  id:1,name:"東京車站（合成資料）",position:{lat:35.681236,lon:139.767125},
  countryCode:"JP",countryName:"日本",city:"東京",
  timeZoneId:"Asia/Tokyo",createdAtMillis:1795180000000
},{
  id:2,name:"台北（合成資料）",position:{lat:25.033,lon:121.5654},
  countryCode:"TW",countryName:"台灣",city:"台北",
  timeZoneId:"Asia/Taipei",createdAtMillis:1795180000000
},{
  id:3,name:"舊收藏（合成資料）",position:{lat:40.7,lon:-74.0},
  timeZoneId:null,createdAtMillis:1795180000000
}];
const readBody=async req=>{
  let body='';for await(const chunk of req)body+=chunk;
  try{return JSON.parse(body||'{}');}catch(_){return {};}
};
const send=(res,body,status=200,type='application/json; charset=utf-8')=>{
  res.writeHead(status,{
    'Content-Type':type,'Cache-Control':'no-store','Referrer-Policy':'no-referrer',
    'Content-Security-Policy':csp,'X-Content-Type-Options':'nosniff'
  });
  res.end(Buffer.isBuffer(body)||typeof body==='string'?body:JSON.stringify(body));
};
createServer(async (req,res)=>{
  const url=(req.url||'').split('?')[0];
  const item=/^\/flags\/[a-z]{2}\.png$/.test(url) ? [url.slice(1), 'image/png'] : assets[url];
  if(req.method==='GET'&&item){
    try{send(res,await readFile(join(root,item[0])),200,item[1]);}
    catch(error){send(res,{error:'Preview asset missing'},404);}
    return;
  }
  if(req.method==='POST'&&url==='/api/pair'){
    send(res,{pendingId:'preview-only-pair'},202);return;
  }
  if(req.method==='GET'&&url==='/api/pair/status'){
    send(res,{token:'preview-only-token',expiresInSeconds:7200},200);return;
  }
  if(req.method==='GET'&&url==='/api/state'){
    if(state.realPosition?.expiresAtMillis < Date.now()){
      state.realPosition=null;
      state.realGpsState=state.realLocationSharingApproved?'unavailable':'locked';
    }
    send(res,state);return;
  }
  if(req.method==='POST'&&url==='/api/preview/grant-real'){
    state.realLocationSharingApproved=true;
    state.realGpsState=state.realPosition?'historical':
      (state.mockActive?'mock':'idle');
    send(res,{message:'合成授權：僅用於本機測試，沒有接觸手機'},202);return;
  }
  if(req.method==='POST'&&url==='/api/preview/revoke-real'){
    state.realLocationSharingApproved=false;
    state.realPosition=null;
    state.realGpsState='locked';
    send(res,{message:'合成真實位置已撤銷'},202);return;
  }
  if(req.method==='POST'&&url==='/api/preview/expire-real-position'){
    if(state.realPosition)state.realPosition.expiresAtMillis=Date.now()-1000;
    send(res,{message:'合成快照已過期'},202);return;
  }
  // Fixed synthetic route scenarios: never contact the phone or a routing service.
  if(req.method==='POST'&&url==='/api/preview/route-state'){
    const {scenario}=await readBody(req);
    if(!['active','parked','planning','ready','failed','cleared'].includes(scenario)){
      send(res,{error:'Unknown test scenario'},400);return;
    }
    const origin={lat:25.033,lon:121.5654};
    const a={lat:25.043,lon:121.5654}, b={lat:25.033,lon:121.5754};
    const preview=(id,point)=>({id,loop:false,geometry:[origin,point],
      distanceMetres:1000,maxSnapMetres:0});
    state={...state,origin,geometry:[origin,a],mode:'ROUTE',
      phase:scenario==='active'?'MOVING':'PARKED',
      routeStops:scenario==='cleared'?[]:[scenario==='active'?a:b],
      planning:scenario==='planning',
      previews:scenario==='ready'?{ROUTE:preview('B',b)}:
        scenario==='planning'?{ROUTE:preview('A',a)}:{}};
    send(res,{message:'Synthetic route state only'},202);return;
  }
  if(req.method==='POST'&&url==='/api/preview/active-walk'){
    state.phase='MOVING';state.mockActive=true;
    state.position={lat:25.04,lon:121.46};
    state.progress={...state.progress,estimatedSteps:128,activeLeg:'1 → 2'};
    send(res,{message:'合成行走中，未操作任何真實 GPS'},202);return;
  }
  if(req.method==='GET'&&url==='/api/saved'){
    send(res,{places:previewPlaces,routes:[],
      enrichmentBusy:false,enrichmentNote:"本機合成資料測試完成"});return;
  }
  if(req.method==='POST'&&url==='/api/preview/add-many-favourites'){
    previewPlaces=previewPlaces.slice(0,2);
    for(let i=0;i<45;i++){
      previewPlaces.push({id:1000+i,name:"測試收藏 "+(i+1),
        position:{lat:35.681236+i*0.0001,lon:139.767125},
        countryCode:"JP",countryName:"日本",city:"東京",
        timeZoneId:"Asia/Tokyo",createdAtMillis:1795180000000+i});
    }
    send(res,{message:"合成收藏已生成，沒有修改手機資料"},202);return;
  }
  if(req.method==='POST'&&url==='/api/preview/expire-real-zone'){
    state.realTimeZoneExpiresAtMillis=Date.now()-60_000;
    send(res,{message:"合成實體時區已過期，沒有修改手機資料"},202);return;
  }
  if(req.method==='POST'&&url==='/api/saved/place/lookup'){
    send(res,{lookupId:'synthetic-geocode-lookup'},202);return;
  }
  if(req.method==='POST'&&url==='/api/saved/place/lookup/status'){
    send(res,{status:'ready',name:'合成地標（僅測試）',countryCode:'JP',
      countryName:'日本',city:'東京',timeZoneId:'Asia/Tokyo'});return;
  }
  if(req.method==='POST'&&url==='/api/saved/place/add'){
    const data=await readBody(req);
    previewPlaces.push({id:Date.now(),name:String(data.name||'合成收藏'),
      position:{lat:Number(data.lat),lon:Number(data.lon)},countryCode:'JP',
      countryName:'日本',city:'東京',timeZoneId:'Asia/Tokyo',
      createdAtMillis:Date.now()});
    send(res,{message:'已新增本機合成收藏（沒有修改手機資料）'},202);return;
  }
  if(req.method==='POST'&&url==='/api/saved/place/enrich'){
    send(res,{message:'本機合成收藏資訊已補齊'},202);return;
  }
  if(req.method==='POST'&&
     (url==='/api/saved/place/rename'||url==='/api/saved/place/delete')){
    const data=await readBody(req);
    if(url.endsWith('rename')) previewPlaces=previewPlaces.map(x=>x.id===data.id?
      {...x,name:String(data.name)}:x);
    else previewPlaces=previewPlaces.filter(x=>x.id!==data.id);
    send(res,{message:'本機收藏操作模擬（沒有修改手機資料）'},202);return;
  }
  if(req.method==='POST'&&url==='/api/flowers/nearby'){
    send(res,{flowers:[{id:"test-flower-1",name:"測試花點（非遊戲資料）",
      lat:25.045,lon:121.466,distancemetres:120,distanceMetres:120,
      evidence:"PERSONALLY_CHECKED"}],source:"USER_CATALOGUE_NOT_GAME_API"});return;
  }
  if(req.method==='POST'&&url==='/api/flowers/optimise'){
    send(res,{message:'本機僅供 UI 預覽：道路順序未實際最佳化'},202);return;
  }
  if(req.method==='POST'&&url==='/api/flowers/add'){
    state.flowerStops=[{lat:25.045,lon:121.466}];
    state.previews={};
    send(res,{message:"合成花點已加入（沒有操控手機）"},202);return;
  }
  if(req.method==='POST'&&url==='/api/control'){
    const request=JSON.parse((await (async()=>{let body='';for await(const chunk of req)body+=chunk;return body})())||'{}');
    if(request.action==='restore-real'){
      state.mockActive=false;
      state.position=null;
      state.phase='IDLE';
      state.realGpsState=state.realPosition?'historical':
        (state.realLocationSharingApproved?'idle':'locked');
    }
    if(request.action==='pause'&&state.phase==='MOVING')state.phase='PAUSED';
    if(request.action==='resume'&&state.phase==='PAUSED')state.phase='MOVING';
    if(request.action==='stop'&&['MOVING','PAUSED'].includes(state.phase))
      state.phase='PARKED';
    send(res,{message:'本機模擬狀態已更新；沒有操控手機'},202);return;
  }
  if(req.method==='POST'&&url==='/api/location/real'){
    if(!state.realLocationSharingApproved){
      send(res,{error:'本機合成預覽：先呼叫 grant-real 模擬手機同意'},403);return;
    }
    if(state.mockActive){
      send(res,{error:'模擬定位中不可取得新的真實 GPS'},409);return;
    }
    const capturedAtMillis=Date.now();
    state.realPosition={lat:25.0408,lon:121.4634,accuracy:12,
      capturedAtMillis,expiresAtMillis:capturedAtMillis+30*60_000,
      historical:false};
    state.realGpsState='ready';
    state.realTimeZoneId='Asia/Taipei';
    state.realTimeZoneExpiresAtMillis=capturedAtMillis+30*60_000;
    send(res,{message:'本機預覽的合成真實 GPS（並非手機定位）'},202);return;
  }
  if(req.method==='POST'&&url==='/api/teleport'){
    state.mockActive=true;
    state.realGpsState=state.realPosition?'historical':'mock';
    if(state.realPosition)state.realPosition.historical=true;
    state.position={lat:25.0330,lon:121.5654};
    // Synthetic historical physical coordinate stays only until its original TTL.
    send(res,{message:'合成虛擬位置已啟用（未操控手機）'},202);return;
  }
  if(req.method==='POST'&&(url==='/api/points/reorder'||url==='/api/points/remove')){
    let body='';
    for await(const chunk of req)body+=chunk;
    const request=JSON.parse(body||'{}');
    const items=request.mode==='FLOWERS'?'flowerStops':'routeStops';
    const old=state[items].slice();
    const index=request.index;
    if(url.endsWith('reorder')){
      const next=index+request.offset;
      if(![-1,1].includes(request.offset)||index<0||next<0||
          index>=old.length||next>=old.length){
        send(res,{error:'路點順序無效'},409);return;
      }
      [old[index],old[next]]=[old[next],old[index]];
    } else {
      if(index<0||index>=old.length){send(res,{error:'路點無效'},409);return;}
      old.splice(index,1);
    }
    state[items]=old;
    state.previews={};
    send(res,{message:'本機測試草稿順序已更新；沒有操控手機'},202);return;
  }
  if(req.method==='POST'&&url.startsWith('/api/')){
    send(res,{message:'僅供測試的網頁預覽：沒有向手機送出指令'},202);return;
  }
  send(res,{error:'not found'},404);
}).listen(port,'127.0.0.1',()=>{
  process.stdout.write('PikminBee local-only UI preview at http://127.0.0.1:'+port+'/\\n');
});
