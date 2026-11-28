import assert from "node:assert/strict";
import {test} from "node:test";
import {installPollCapture} from "./web_poll_capture.mjs";

function fixture() {
  let time=0;
  class FakeResponse {
    constructor(status, read) { this.status=status; this.read=read; }
    json() { return this.read(); }
  }
  const queue=[];
  const target={performance:{now:()=>time},Response:FakeResponse,fetch:()=>queue.shift()()};
  const originalFetch=target.fetch, originalJson=FakeResponse.prototype.json;
  const capture=installPollCapture(target);
  return {target,capture,queue,FakeResponse,tick:n=>time+=n,originalFetch,originalJson};
}

test("captures HTTP and read+parse duration without retaining response data", async()=>{
  const f=fixture();
  f.queue.push(async()=>{f.tick(3);return new f.FakeResponse(200,async()=>{f.tick(2);return {private:"fixture"};});});
  const response=await f.target.fetch("/api/state");
  assert.deepEqual(await response.json(),{private:"fixture"});
  assert.deepEqual(f.capture.snapshot(),{durationMs:5,attempts:[{startMs:0,status:200,fetchEndMs:3,fetchOutcome:"http",jsonStartMs:3,jsonEndMs:5,jsonOutcome:"ok"}]});
  f.capture.restore();
  assert.equal(f.target.fetch,f.originalFetch);
  assert.equal(f.FakeResponse.prototype.json,f.originalJson);
});

test("retains HTTP failure and JSON rejection separately",async()=>{
  const f=fixture();
  f.queue.push(async()=>new f.FakeResponse(503,async()=>{throw new SyntaxError("fixture");}));
  const response=await f.target.fetch("/api/state");
  await assert.rejects(response.json(),SyntaxError);
  const [row]=f.capture.snapshot().attempts;
  assert.equal(row.status,503);
  assert.equal(row.fetchOutcome,"http");
  assert.equal(row.jsonOutcome,"error");
});

test("distinguishes transport failure, cancellation and pending fetch",async()=>{
  const f=fixture();
  f.queue.push(async()=>{throw new TypeError("offline");},async()=>{throw new DOMException("cancel","AbortError");},()=>new Promise(()=>{}));
  await assert.rejects(f.target.fetch("/api/state"),TypeError);
  await assert.rejects(f.target.fetch("/api/state"),{name:"AbortError"});
  void f.target.fetch("/api/state");
  f.tick(10);
  const rows=f.capture.snapshot().attempts;
  assert.deepEqual(rows.map(r=>r.fetchOutcome),["transport_error","cancelled","pending"]);
  assert.equal(rows[2].fetchEndMs,null);
});

test("distinguishes body read pending and excludes other endpoints",async()=>{
  const f=fixture();
  f.queue.push(async()=>new f.FakeResponse(200,async()=>1),async()=>new f.FakeResponse(200,()=>new Promise(()=>{})));
  assert.equal(await (await f.target.fetch("/api/saved")).json(),1);
  const response=await f.target.fetch("/api/state");
  const before=f.capture.snapshot();
  assert.equal(before.attempts[0].jsonOutcome,"not_started");
  void response.json();
  f.tick(5);
  const after=f.capture.snapshot();
  assert.equal(after.attempts.length,1);
  assert.equal(after.attempts[0].jsonOutcome,"pending");
  assert.equal(after.attempts[0].jsonEndMs,null);
  assert.equal(before.attempts[0].jsonOutcome,"not_started");
});
