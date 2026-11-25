import {test} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {parseCoordinate} from "../app/src/main/assets/lan/coordinate-format.mjs";

test("shared DD, DMS and DDM input vectors",()=>{
  const cases=readFileSync(new URL("../app/src/test/resources/coordinate-formats.tsv",import.meta.url),"utf8")
    .split(/\r?\n/).filter(line=>line.includes("\t"));
  assert.ok(cases.length>=40);
  for(const line of cases){
    const [input,lat,lon]=line.split("\t"), result=parseCoordinate(input);
    if(lat==="INVALID"){assert.equal(result,null,input);continue;}
    assert.ok(result,input);
    assert.ok(Math.abs(result.lat-Number(lat))<1e-9,input+" latitude");
    assert.ok(Math.abs(result.lon-Number(lon))<1e-9,input+" longitude");
  }
});
