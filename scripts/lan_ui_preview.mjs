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
  '/coordinate-format.mjs': ['coordinate-format.mjs', 'application/javascript; charset=utf-8'],
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
  routeRevision: 1, routeLoop:false, planningNotice:'', planningError:false,
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
let syntheticOffline=false, syntheticRenewRequested=false, syntheticRenewApproved=false;
let previewRoutes=[];
let previewPlaces=[{
  id:1,name:"東京車站（合成資料）",position:{lat:35.681236,lon:139.767125},
  countryCode:"JP",countryName:"日本",city:"東京",
  timeZoneId:"Asia/Tokyo",createdAtMillis:1795180000000
},{
  id:2,name:"台北（合成資料）",position:{lat:25.033,lon:121.5654},
  countryCode:"TW",countryName:"台灣",city:"台北",
  timeZoneId:null,createdAtMillis:1795180000000
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
    if(syntheticOffline){send(res,{error:'合成網路中斷'},503);return;}
    if(state.sessionExpiresInSeconds<=0){send(res,{error:'合成授權到期'},401);return;}
    if(state.realPosition?.expiresAtMillis < Date.now()){
      state.realPosition=null;
      state.realGpsState=state.realLocationSharingApproved?'unavailable':'locked';
    }
    send(res,state);return;
  }
  // Loopback-only session fixtures. No actual phone session or credentials.
  if(req.method==='POST'&&url==='/api/preview/session'){
    const data=await readBody(req);
    if(typeof data.seconds==='number')state.sessionExpiresInSeconds=data.seconds;
    if(typeof data.offline==='boolean')syntheticOffline=data.offline;
    if(data.approve===true)syntheticRenewApproved=true;
    send(res,{renewRequested:syntheticRenewRequested,renewApproved:syntheticRenewApproved},200);return;
  }
  if(req.method==='POST'&&url==='/api/session/renew'){
    if(state.sessionExpiresInSeconds<=0){send(res,{error:'合成授權到期'},401);return;}
    syntheticRenewRequested=true;syntheticRenewApproved=false;
    send(res,{pendingId:'synthetic-renewal'},202);return;
  }
  if(req.method==='POST'&&url==='/api/session/renew/status'){
    const data=await readBody(req);
    if(state.sessionExpiresInSeconds<=0){send(res,{error:'合成授權到期'},401);return;}
    if(!syntheticRenewRequested||data.pendingId!=='synthetic-renewal'){send(res,{error:'無效要求'},403);return;}
    if(!syntheticRenewApproved){send(res,{status:'pending'},202);return;}
    state.sessionExpiresInSeconds=7200;state.realLocationSharingApproved=false;state.realPosition=null;state.realGpsState='locked';
    send(res,{token:'preview-only-renewed-token',expiresInSeconds:7200},200);return;
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
  // Synthetic permission toggle for browser tests only; never requests device permissions.
  if(req.method==='POST'&&url==='/api/preview/mock-permission'){
    const {approved}=await readBody(req);
    state.mockPermissionReady=approved===true;
    send(res,{message:'Synthetic test permission state only'},202);return;
  }
  // Fixed synthetic route scenarios: never contact the phone or a routing service.
  if(req.method==='POST'&&url==='/api/preview/route-state'){
    const {scenario}=await readBody(req);
    if(!['active','active-next','parked','planning','ready','stale','failed','cleared','arrived'].includes(scenario)){
      send(res,{error:'Unknown test scenario'},400);return;
    }
    const origin={lat:25.033,lon:121.5654};
    const a={lat:25.043,lon:121.5654}, b={lat:25.033,lon:121.5754};
    const preview=(id,point)=>({id,loop:false,geometry:[origin,point],
      distanceMetres:1000,maxSnapMetres:0});
    const moving=['active','active-next'].includes(scenario);
    state={...state,origin,geometry:[origin,a],mode:'ROUTE',
      position:scenario==='active-next'?{lat:25.036,lon:121.5654}:state.position,
      phase:moving?'MOVING':'PARKED',
      activeLegGeometry:moving?(scenario==='active-next'?[{lat:25.036,lon:121.5654},a]:[origin,a]):[],
      completedGeometry:scenario==='active-next'?[origin,{lat:25.036,lon:121.5654}]:[],
      routeArrivalId:scenario==='arrived'?'synthetic-arrival-'+(state.routeRevision+1):null,
      routeRevision:state.routeRevision+1,
      routeStops:scenario==='cleared'?[]:[moving?a:b],
      planning:scenario==='planning', routeLoop:false,
      planningNotice:scenario==='failed'?'道路規劃失敗：合成逾時':'',
      planningError:scenario==='failed',
      previews:scenario==='ready'?{ROUTE:preview('B',b)}:
        scenario==='stale'?{ROUTE:{...preview('B',b),canStart:false}}:
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
    send(res,{places:previewPlaces,routes:previewRoutes,
      enrichmentBusy:false,enrichmentNote:"本機合成資料測試完成"});return;
  }
  if(req.method==='POST'&&['/api/saved/route/add','/api/saved/route/rename','/api/saved/route/delete','/api/saved/load','/api/route/options','/api/route/cancel-preview'].includes(url)){
    const data=await readBody(req);
    if(url==='/api/route/cancel-preview'){
      state.planning=false;state.previews={};state.planningError=false;state.planningNotice='已取消規劃';
    } else if(url==='/api/route/options'){
      if(data.expectedRevision!==state.routeRevision){send(res,{error:'stale draft'},409);return;}
      state.routeLoop=data.loop===true;state.routeRevision++;state.previews={};
    } else if(url==='/api/saved/route/add'){
      if(data.expectedRevision!==state.routeRevision||state.planning||!state.routeStops.length){send(res,{error:'stale draft'},409);return;}
      previewRoutes.push({id:'synthetic-'+Date.now(),name:data.name,mode:'ROUTE',origin:state.origin,loop:state.routeLoop,stops:state.routeStops.slice()});
    } else {
      const route=previewRoutes.find(x=>x.id===data.id);
      if(!route){send(res,{error:'not found'},404);return;}
      if(url.endsWith('/rename'))route.name=data.name;
      else if(url.endsWith('/delete'))previewRoutes=previewRoutes.filter(x=>x.id!==route.id);
      else {
        if(data.expectedRevision!==state.routeRevision){send(res,{error:'stale draft'},409);return;}
        state.origin=route.origin;state.routeLoop=route.loop;state.routeStops=route.stops.slice();state.routeRevision++;state.previews={};
      }
    }
    send(res,{message:'合成路線收藏／規劃狀態已更新'},202);return;
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
  if(req.method==='POST'&&url==='/api/origin'){
    const data=await readBody(req);
    if(state.planning||['MOVING','PAUSED'].includes(state.phase)||data.expectedRevision!==state.routeRevision||
        !Number.isFinite(data.lat)||!Number.isFinite(data.lon)||Math.abs(data.lat)>90||Math.abs(data.lon)>180){
      send(res,{error:'路線版本或合成起點無效'},409);return;
    }
    state.origin={lat:data.lat,lon:data.lon};state.routeRevision++;state.previews={};
    send(res,{message:'本機合成路線起點已更新；沒有操控手機'},202);return;
  }
  if(req.method==='POST'&&['/api/points/add','/api/points/reorder','/api/points/remove','/api/points/replace','/api/points/clear','/api/points/undo','/api/points/promote-origin'].includes(url)){
    let body='';
    for await(const chunk of req)body+=chunk;
    const request=JSON.parse(body||'{}');
    if(state.planning||['MOVING','PAUSED'].includes(state.phase)||
        (request.expectedRevision!==undefined&&request.expectedRevision!==state.routeRevision)){
      send(res,{error:'路點已變更或目前不可編輯'},409);return;
    }
    const items='routeStops';
    const old=state[items].slice();
    const index=request.index;
    const valid=i=>Number.isInteger(i)&&i>=0&&i<old.length;
    if(url.endsWith('promote-origin')){
      if(request.mode!=='ROUTE'||request.index!==0||request.expectedRevision!==state.routeRevision||!old.length){
        send(res,{error:'無效的第一路點或草稿版本'},409);return;
      }
      state.origin=old.shift();
    } else if(url.endsWith('reorder')){
      const from=request.from, to=request.to;
      if(!valid(from)||!valid(to)||request.expectedRevision!==state.routeRevision){
        send(res,{error:'路點順序無效'},409);return;
      }
      old.splice(to,0,old.splice(from,1)[0]);
    } else if(url.endsWith('add')){
      if(request.mode!=='ROUTE'||old.length>=15||!Number.isFinite(request.lat)||
        !Number.isFinite(request.lon)||Math.abs(request.lat)>90||Math.abs(request.lon)>180||
        request.expectedRevision!==state.routeRevision){
        send(res,{error:'合成路點無效'},409);return;
      }
      old.push({lat:request.lat,lon:request.lon});
    } else if(url.endsWith('clear')) old.length=0;
    else if(url.endsWith('undo')) old.pop();
    else {
      if(!valid(index)){send(res,{error:'路點無效'},409);return;}
      if(url.endsWith('replace')) old[index]={lat:request.lat,lon:request.lon};
      else old.splice(index,1);
    }
    state[items]=old;
    state.routeRevision++;
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
