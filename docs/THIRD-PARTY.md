# Third-party code & asset inventory (PikminBee)

## Locally bundled web-map library

PikminBee's private Wi-Fi browser controller serves a **fixed, reviewed local copy** of MapLibre GL JS rather than executing JavaScript downloaded from a CDN each time an approved remote browser connects.

| Field | Value |
| --- | --- |
| Package | `maplibre-gl` |
| Version | **6.18.0** |
| Distribution | npm public release, `https://unpkg.com/maplibre-gl@6.18.0/` |
| Licence | **BSD-3-Clause** |
| License notice | `app/src/main/assets/lan/vendor/maplibre-gl/LICENSE.txt` |
| JS module | `app/src/main/assets/lan/vendor/maplibre-gl/maplibre-gl.mjs` |
| CSS | `app/src/main/assets/lan/vendor/maplibre-gl/maplibre-gl.css` |
| JS SHA-256 | `1e0a68844041aec6621a10d4725c9970c3b19cf7e1605d98b01dbc993e1058b2` |
| CSS SHA-256 | `a90798a1953017790e590f7e1bd35ff103a8a4263972cb47290b78adf86f8c20` |

The original license notice is retained without changes. Unit tests verify the exact vendored JS/CSS hashes; updating MapLibre requires updating the bundled assets, licence, tests, and review.

MapLibre JS is code only. The map still uses **OpenFreeMap** network map resources with **OpenStreetMap** attribution. Map data licensing and third-party map style hosting policies remain separate from the MapLibre GL JS licence.

For provenance: https://www.npmjs.com/package/maplibre-gl and https://maplibre.org/maplibre-gl-js/docs/

## Scope of this dependency

- Only the browser UI `/vendor/maplibre-gl.mjs` and `/vendor/maplibre-gl.css` are served by the Android LAN listener.
- The browser receives executable code only from its **same-origin local server**; the HTTP Content-Security-Policy disallows CDN script execution.
- Public/private Wi-Fi **HTTP transport remains unencrypted**; local bundling does not fix lack of TLS or protect against malicious on-path network users.
- This library is unrelated to the Android MapLibre native SDK. Check the native SDK's licence and transitives separately at release gate.
- Any later library upgrade requires rechecking the published version and its release licence; do not silently fetch `latest` at runtime.
