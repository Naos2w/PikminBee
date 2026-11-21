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
  '/vendor/maplibre-gl.mjs': ['vendor/maplibre-gl/maplibre-gl.mjs', 'application/javascript; charset=utf-8'],
  '/vendor/maplibre-gl-worker.mjs': ['vendor/maplibre-gl/maplibre-gl-worker.mjs', 'application/javascript; charset=utf-8'],
  '/vendor/maplibre-gl.css': ['vendor/maplibre-gl/maplibre-gl.css', 'text/css; charset=utf-8'],
};
let state = {
  position: {lat:25.040815,lon:121.463424},
  origin: {lat:25.040815,lon:121.463424},
  routeStops: [], flowerStops: [], geometry: [],
  previews: {}, phase:'PARKED', mode:'ROUTE', planning:false,
  mockPermissionReady:false, status:'手機待命 · 本機預覽',
  progress: {activeLeg:'起點 → 1',estimatedSteps:0,fraction:0,
    travelledMetres:0,totalMetres:0,waypointCount:0},
  speedPreset:'WALK',defaultSpeedPreset:'WALK'
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
  const item=assets[url];
  if(req.method==='GET'&&item){
    try{send(res,await readFile(join(root,item[0])),200,item[1]);}
    catch(error){send(res,{error:'Preview asset missing'},404);}
    return;
  }
  if(req.method==='POST'&&url==='/api/pair'){
    send(res,{pendingId:'preview-only-pair'},202);return;
  }
  if(req.method==='GET'&&url==='/api/pair/status'){
    send(res,{token:'preview-only-token'},200);return;
  }
  if(req.method==='GET'&&url==='/api/state'){
    send(res,state);return;
  }
  if(req.method==='GET'&&url==='/api/saved'){
    send(res,{places:[],routes:[]});return;
  }
  if(req.method==='POST'&&url.startsWith('/api/')){
    send(res,{message:'僅供測試的網頁預覽：沒有向手機送出指令'},202);return;
  }
  send(res,{error:'not found'},404);
}).listen(port,'127.0.0.1',()=>{
  process.stdout.write('PikminBee local-only UI preview at http://127.0.0.1:'+port+'/\\n');
});
