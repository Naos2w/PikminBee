package com.naos.pikminbee.lan

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Contract checks run locally with Gradle; no device, APK or GitHub Actions required. */
class LanHttpPolicyTest {
    private val address = "192.168.1.77:8765"
    private val get = mapOf("host" to address)
    private val post = get + mapOf(
        "origin" to "http://$address",
        "content-type" to "application/json; charset=utf-8",
        "content-length" to "22",
    )

    @Test fun allowsLocalBrowserGetAndAuthenticatedJsonPostHeaders() {
        assertNull(LanHttpPolicy.rejectRequest("GET", address, get))
        assertNull(LanHttpPolicy.rejectRequest("POST", address, post))
    }

    @Test fun rejectsWrongOrMissingHostAndCrossOriginPost() {
        assertEquals(403, LanHttpPolicy.rejectRequest("GET", address, emptyMap()))
        assertEquals(403, LanHttpPolicy.rejectRequest("GET", address, get + ("host" to "evil.com")))
        assertEquals(403, LanHttpPolicy.rejectRequest("POST", address,
            post + ("origin" to "http://evil.com")))
        assertEquals(403, LanHttpPolicy.rejectRequest("POST", address,
            post - "origin"))
    }

    @Test fun refusesTransferEncodingAndInvalidBodyLengths() {
        assertEquals(400, LanHttpPolicy.rejectRequest("POST", address,
            post + ("transfer-encoding" to "chunked")))
        assertEquals(413, LanHttpPolicy.rejectRequest("POST", address,
            post + ("content-length" to "999999")))
        assertEquals(413, LanHttpPolicy.rejectRequest("POST", address,
            post + ("content-length" to "0")))
        assertEquals(413, LanHttpPolicy.rejectRequest("POST", address,
            post + ("content-length" to "-1")))
    }

    @Test fun acceptsJsonOnlyForWrites() {
        assertEquals(415, LanHttpPolicy.rejectRequest("POST", address,
            post + ("content-type" to "text/plain")))
        assertEquals(415, LanHttpPolicy.rejectRequest("POST", address,
            post - "content-type"))
    }

    @Test fun cspAllowsLocalExecutableCodeButNoCdn() {
        val policy = LanHttpPolicy.CONTENT_SECURITY_POLICY
        assertTrue(policy.contains("script-src 'self'"))
        assertTrue(policy.contains("object-src 'none'"))
        assertTrue(policy.contains("frame-ancestors 'none'"))
        assertFalse(policy.contains("unpkg.com"))
        assertFalse(policy.contains("script-src 'unsafe-inline'"))
        // MapLibre 6.18.x requires same-origin/Blob images and a worker URL.
        assertTrue(policy.contains("img-src 'self' data: blob:"))
        assertTrue(policy.contains("worker-src 'self' blob:"))
        assertTrue(policy.contains("connect-src 'self' https://tiles.openfreemap.org"))
    }

    @Test fun browserBundlesTheExactReviewedMaplibreAssetsLocally() {
        val directory = sequenceOf(
            File("src/main/assets/lan"),
            File("app/src/main/assets/lan"),
        ).firstOrNull { it.isDirectory }
            ?: throw AssertionError("Missing PikminBee LAN asset directory")

        val html = File(directory, "index.html").readText(Charsets.UTF_8)
        val client = File(directory, "controller.js").readText(Charsets.UTF_8)
        assertTrue(html.contains("href=\"/vendor/maplibre-gl.css\""))
        assertTrue(client.contains("import(\"/vendor/maplibre-gl.mjs\")"))
        assertTrue(client.contains("maplibre.setWorkerUrl(\"/vendor/maplibre-gl-worker.mjs\")"))
        assertFalse(html.contains("unpkg.com"))
        assertFalse(client.contains("unpkg.com"))

        val vendor = File(directory, "vendor/maplibre-gl")
        assertTrue(File(vendor, "LICENSE.txt").readText().contains(
            "Copyright (c) 2023, MapLibre contributors"))
        val js = File(vendor, "maplibre-gl.mjs")
        val css = File(vendor, "maplibre-gl.css")
        val worker = File(vendor, "maplibre-gl-worker.mjs")
        assertTrue(js.length() > 1_000_000L)
        assertTrue(css.length() > 40_000L)
        assertTrue("Exact-version MapLibre worker must be packaged", worker.length() > 400_000L)
        assertEquals(
            "1e0a68844041aec6621a10d4725c9970c3b19cf7e1605d98b01dbc993e1058b2",
            sha256(js),
        )
        assertEquals(
            "a90798a1953017790e590f7e1bd35ff103a8a4263972cb47290b78adf86f8c20",
            sha256(css),
        )
        assertEquals(
            "c8998a2616612ebc7afc3a6f83e31ad1c5f6187550a018b4fd0561779c33015b",
            sha256(worker),
        )
    }

    @Test fun browserRequiresPreviewAndExplicitStartAndPairedAuth() {
        val directory = sequenceOf(
            File("src/main/assets/lan"),
            File("app/src/main/assets/lan"),
        ).firstOrNull { it.isDirectory } ?: error("Missing LAN assets")
        val html = File(directory, "index.html").readText(Charsets.UTF_8)
        val js = File(directory, "controller.js").readText(Charsets.UTF_8)
        assertTrue(html.contains("id=\"plan\""))
        assertTrue(html.contains("id=\"start\""))
        assertTrue(html.contains("id=\"set-origin\""))
        assertTrue(html.contains("id=\"mode-saved\""))
        assertTrue(html.contains("id=\"confirm-snap\""))
        assertTrue(html.contains("id=\"map-status\""))
        assertTrue(html.contains("id=\"map-warning\""))
        assertTrue(html.contains("id=\"retry-map\""))
        assertTrue(html.contains("id=\"external-map\""))
        assertTrue(html.contains("id=\"mobile-state\""))
        assertTrue(html.contains("id=\"mobile-steps\""))
        assertTrue(js.contains("map.on(\"error\""))
        assertTrue(js.contains("map.on(\"webglcontextlost\""))
        assertTrue(js.contains("map.resize()"))
        assertTrue(html.contains("clamp(240px,33svh,330px)"))
        assertTrue(js.contains("/api/route/preview"))
        assertTrue(js.contains("previewId: prepared.id"))
        assertTrue(js.contains("/api/route/start"))
        assertTrue(js.contains("X-PikminBee-Token"))
        assertTrue(js.contains("/api/saved"))
        assertTrue(js.contains("confirmSnap"))
        assertFalse(js.contains("setInterval(() => act("))
    }

    @Test fun browserJsUsesOnlyDomControlIdsPresentInHtml() {
        val directory = sequenceOf(
            File("src/main/assets/lan"),
            File("app/src/main/assets/lan"),
        ).firstOrNull { it.isDirectory } ?: error("Missing LAN assets")
        val html = File(directory, "index.html").readText(Charsets.UTF_8)
        val client = File(directory, "controller.js").readText(Charsets.UTF_8)
        val ids = Regex("""\$\("([a-z0-9-]+)"\)""").findAll(client)
            .map { it.groupValues[1] }.toSet()
        assertTrue("No UI ids extracted", ids.size >= 25)
        ids.forEach { id ->
            assertTrue("Controller references missing HTML id: $id",
                html.contains("id=\"$id\""))
        }
    }

    @Test fun remoteControlUsesConnectedDeviceFgsNotMockGpsForOpening() {
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
            ?: error("Missing manifest")
        assertTrue(manifest.contains("android:name=\".lan.LanControlService\""))
        assertTrue(manifest.contains("android:foregroundServiceType=\"connectedDevice\""))
        assertTrue(manifest.contains("FOREGROUND_SERVICE_CONNECTED_DEVICE"))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
