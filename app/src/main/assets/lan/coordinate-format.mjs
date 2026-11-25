/** Local coordinate input only; keep grammar in parity with domain/Coordinate.kt. */
const unsigned = "(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)";
const ddPair = new RegExp("^([+-]?"+unsigned+")(?:\\s*[,，]\\s*|\\s+)([+-]?"+unsigned+")$");
const angular = "([+-]?[0-9]+)\\s*°\\s*("+unsigned+")\\s*'\\s*(?:("+unsigned+")\\s*\"\\s*)?([NSEW])";
const angularPair = new RegExp("^"+angular+"\\s*(?:[,，]\\s*)?"+angular+"$","i");

function angularValue(match, offset) {
  const degreeText = match[offset], degrees = Number(degreeText);
  const minuteText = match[offset+1], minutes = Number(minuteText);
  const secondText = match[offset+2], seconds = secondText == null ? 0 : Number(secondText);
  const direction = match[offset+3].toUpperCase();
  const latitude = "NS".includes(direction), negative = "SW".includes(direction);
  if ((degreeText.startsWith("-") && !negative) ||
      (degreeText.startsWith("+") && negative)) return null;
  if (secondText != null && minuteText.includes(".")) return null;
  if (![degrees,minutes,seconds].every(Number.isFinite) ||
      minutes < 0 || minutes >= 60 || seconds < 0 || seconds >= 60) return null;
  const limit = latitude ? 90 : 180, whole = Math.abs(degrees);
  if (whole > limit || (whole === limit && (minutes > 0 || seconds > 0))) return null;
  const magnitude = whole + minutes/60 + seconds/3600;
  if (!Number.isFinite(magnitude) || magnitude > limit) return null;
  return {latitude,value:magnitude === 0 ? 0 : negative ? -magnitude : magnitude};
}

export function parseCoordinate(text) {
  if (typeof text !== "string") return null;
  const value = text.replace(/[′’‘＇]/g,"'").replace(/[″“”＂]/g,'"')
    .replace(/−/g,"-").replace(/\s+/g," ").trim();
  const dd = ddPair.exec(value);
  if (dd) {
    const lat = Number(dd[1]) || 0, lon = Number(dd[2]) || 0;
    return Number.isFinite(lat) && Number.isFinite(lon) &&
      lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 ? {lat,lon} : null;
  }
  const match = angularPair.exec(value);
  if (!match) return null;
  const a = angularValue(match,1), b = angularValue(match,5);
  if (!a || !b || a.latitude === b.latitude) return null;
  return a.latitude ? {lat:a.value,lon:b.value} : {lat:b.value,lon:a.value};
}
