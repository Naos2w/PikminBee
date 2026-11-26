// Local, isolated Chromium CSS layout regression. No real phone or API calls.
// Load the packaged HTML/styles with its runtime script and external stylesheet removed.
// Only reproduce responsive DOM states; do not execute app commands.
import assert from "node:assert/strict";
import {spawn} from "node:child_process";
import {mkdtemp,readFile,rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
const chrome=process.env.PIKMINBEE_CHROME || "C:/Program Files/Google/Chrome/Application/chrome.exe";
const profile=await mkdtemp(join(tmpdir(),"pikminbee-help-layout-"));
const delay=ms=>new Promise(r=>setTimeout(r,ms));
let browser,socket;
try{
  const source=await readFile("app/src/main/assets/lan/index.html","utf8");
  const html=source.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,"")
    .replace(/<link\b[^>]*rel="stylesheet"[^>]*>/gi,"")
    .replace(/<head>/i,'<head><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data:; script-src \'none\'">');
  browser=spawn(chrome,["--headless=new","--no-first-run","--no-default-browser-check",
    "--disable-extensions","--remote-debugging-port=0","--user-data-dir="+profile,"about:blank"],{stdio:"ignore"});
  let port;
  for(let i=0;i<100&&!port;i++){try{port=(await readFile(join(profile,"DevToolsActivePort"),"utf8")).split(/\r?\n/)[0]}catch{}if(!port)await delay(100)}
  assert.ok(port,"Chromium CDP port");
  const tabs=await (await fetch("http://127.0.0.1:"+port+"/json/list")).json();
  socket=new WebSocket(tabs.find(t=>t.type==="page").webSocketDebuggerUrl);
  await new Promise((resolve,reject)=>{socket.addEventListener("open",resolve,{once:true});socket.addEventListener("error",reject,{once:true})});
  let seq=0;const pending=new Map();
  socket.addEventListener("message",e=>{const msg=JSON.parse(e.data),p=pending.get(msg.id);if(p){pending.delete(msg.id);msg.error?p.reject(Error(msg.error.message)):p.resolve(msg.result)}});
  const call=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}))});
  const evaluate=async expression=>{const result=await call("Runtime.evaluate",{expression,returnByValue:true,awaitPromise:true});if(result.exceptionDetails)throw Error(result.exceptionDetails.text);return result.result.value};
  await call("Page.enable"); await call("Runtime.enable");
  const frameId=(await call("Page.getFrameTree")).frameTree.frame.id;
  await call("Page.setDocumentContent",{frameId,html});
  const cases=[
    [2560,1440,"desktop"],[1920,1080,"desktop"],[1366,768,"desktop"],
    [1440,540,"desktop"],[1024,768,"tablet"],[800,480,"tablet"],
    [690,920,"tablet"],[390,844,"mobile"],[320,720,"mobile"]
  ];
  const states=[];
  const inspect=()=>evaluate(`(()=>{
    const panel=document.querySelector("#control-panel"), title=panel.querySelector(".control-title");
    const capture=id=>{
      const el=document.getElementById(id),r=el.getBoundingClientRect(),visible=r.width>0&&r.height>0&&getComputedStyle(el).visibility!=="hidden";
      return {visible,width:r.width,height:r.height,left:r.left,right:r.right,top:r.top,bottom:r.bottom,
        hit:visible&&el.contains(document.elementFromPoint(r.left+r.width/2,r.top+r.height/2))};
    };
    const pr=panel.getBoundingClientRect(), mr=document.querySelector("#map").getBoundingClientRect();
    return {panel:{left:pr.left,right:pr.right,top:pr.top,bottom:pr.bottom,width:pr.width},title:capture("mode-panel-title"),
      help:capture("mode-help"),toggle:capture("panel-toggle"),sheet:capture("sheet-handle"),map:[mr.x,mr.y,mr.width,mr.height],
      horizontalOverflow:panel.scrollWidth>panel.clientWidth+1};
  })()`);
  const inside=(a,b)=>a.left>=b.left-1&&a.right<=b.right+1&&a.top>=b.top-1&&a.bottom<=b.bottom+1;
  for(const [width,height,kind] of cases){
    await call("Emulation.setDeviceMetricsOverride",{width,height,deviceScaleFactor:1,mobile:kind==="mobile"});
    await evaluate(`(()=>{
      document.body.classList.add("remote-active");
      document.body.dataset.device=${JSON.stringify(kind)};
      document.body.dataset.mode="route";
      document.querySelector("#pairbox").classList.add("hidden");
      document.querySelector("#controls").classList.add("is-visible");
      const p=document.querySelector("#control-panel");p.dataset.panel="closed";p.dataset.sheet="collapsed";
      return true
    })()`);
    const label=width+"x"+height+" "+kind;
    const closed=await inspect();
    assert.equal(closed.help.visible,false,label+": help must be hidden while collapsed");
    if(kind==="mobile"){
      assert.equal(closed.toggle.visible,false,label+": desktop toggle hidden on mobile");
      assert.ok(closed.sheet.hit,label+": sheet handle reachable");
    }else{
      assert.ok(closed.toggle.visible&&closed.toggle.hit&&inside(closed.toggle,closed.panel),label+": expand button remains inside and clickable");
      assert.equal(closed.horizontalOverflow,false,label+": collapsed panel has no horizontal overflow");
    }
    await evaluate(`(()=>{const p=document.querySelector("#control-panel");p.dataset.panel="open";p.dataset.sheet="expanded";return true})()`);
    const open=await inspect();
    assert.ok(open.help.visible&&open.help.hit&&inside(open.help,open.panel),label+": expanded help is visible and reachable");
    assert.ok(open.help.width>=44&&open.help.height>=44,label+": help target at least 44px");
    if(kind!=="mobile"){
      assert.ok(open.toggle.visible&&open.toggle.hit&&inside(open.toggle,open.panel),label+": collapse button remains clickable");
      assert.ok(open.help.right<=open.toggle.left-2,label+": distinct help and collapse targets");
      assert.ok(open.title.right<=open.help.left+1,label+": title does not overlap help");
    }
    assert.equal(open.horizontalOverflow,false,label+": expanded panel has no horizontal overflow");
    assert.deepEqual(open.map,closed.map,label+": map canvas unchanged");
    states.push({viewport:label,collapsed:"pass",expanded:"pass",map:"unchanged"});
  }
  console.log(JSON.stringify(states,null,2));
  console.log("PASS: "+states.length+" viewport cases, collapsed/expanded layout and pointer hit targets.");
}finally{
  try{socket?.close()}catch{}
  browser?.kill();
  await delay(500);
  await rm(profile,{recursive:true,force:true,maxRetries:5,retryDelay:200}).catch(()=>{});
}
