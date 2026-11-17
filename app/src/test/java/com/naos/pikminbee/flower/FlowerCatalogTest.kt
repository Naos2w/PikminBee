package com.naos.pikminbee.flower

import com.naos.pikminbee.domain.Coordinate
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class FlowerCatalogTest {
    private val centre = Coordinate(25.0330, 121.5654)
    private fun at(delta: Double) = Coordinate(25.0330 + delta, 121.5654)

    @Test fun nearbyOrdersByDistanceAndDoesNotInventRecords() {
        val a = FlowerSpot.imported("遠", at(.015))
        val b = FlowerSpot.manual("近", at(.001))
        val c = FlowerSpot.imported("很近", at(.0002))
        val result = FlowerFinder.nearby(centre, listOf(a,b,c), 2000.0)
        assertEquals(listOf(c.id,b.id,a.id), result.map { it.id })
        assertEquals(listOf(b.id), FlowerFinder.nearby(centre, listOf(a,b,c), 2000.0,
            onlyChecked = true).map { it.id })
        assertEquals(emptyList<FlowerSpot>(), FlowerFinder.nearby(centre,listOf(a,b,c),5.0))
    }

    @Test fun mergingKeepsPersonallyCheckedObservationAndIgnoresNearDuplicates() {
        val original = FlowerSpot.manual("親自核對", centre)
        val imported = FlowerSpot.imported("外部匯入", at(.00004))
        val unique = FlowerSpot.imported("新花", at(.003))
        val merge = FlowerFinder.combine(listOf(original), listOf(imported, unique))
        assertEquals(1, merge.added)
        assertEquals(1, merge.duplicates)
        assertEquals(original, merge.flowers[0])
        assertEquals(FlowerEvidence.UNVERIFIED_IMPORT, merge.flowers[1].evidence)
    }

    @Test fun nearestStopsNeverExceedSafeFlowerCircuitLimitOrRepeat() {
        val existing = listOf(at(.002))
        val spots = (1..30).map { i -> FlowerSpot.imported("花 $i",at(i * .0008)) }
        val result = FlowerFinder.appendToDraft(centre, existing, spots,14)
        assertEquals(14,result.size)
        assertEquals(existing.first(),result.first())
        assertTrue(result.distinct().size == result.size)
    }

    @Test fun communityJsonAcceptsFlowersAndSkipsMushroomsWithoutCopyingAuthors() {
        val json = """[
           {"author":"private-account","name":"榕樹","lat":25.033,"lng":121.5654,"type":"花點"},
           {"name":"蘑菇","lat":25.034,"lng":121.5654,"type":"菇點"}
        ]""".trimIndent().toByteArray()
        val result = FlowerJsonCodec.decode(json)
        assertEquals("JSON",result.source)
        assertEquals(1,result.ignored)
        assertEquals(1,result.spots.size)
        assertEquals("榕樹",result.spots.single().name)
        assertEquals(FlowerEvidence.UNVERIFIED_IMPORT,result.spots.single().evidence)
        val exported = FlowerJsonCodec.encodeGeoJson(result.spots).toString(Charsets.UTF_8)
        assertFalse(exported.contains("private-account"))
    }

    @Test fun exportedGeoJsonCanBeImportedButNeverClaimsOfficialVerification() {
        val input = listOf(FlowerSpot.manual("中文 & <花>", centre))
        val encoded = FlowerJsonCodec.encodeGeoJson(input)
        val roundTrip = FlowerJsonCodec.decode(encoded)
        assertEquals("GeoJSON",roundTrip.source)
        assertEquals(input.single().coordinate,roundTrip.spots.single().coordinate)
        assertEquals(input.single().name,roundTrip.spots.single().name)
        assertEquals(FlowerEvidence.UNVERIFIED_IMPORT,roundTrip.spots.single().evidence)
    }

    @Test fun rejectUnmarkedGeographicalPois() {
        val content = """{"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{"name":"公園"},"geometry":{"type":"Point","coordinates":[121.5654,25.033]}}
        ]}""".trimIndent().toByteArray()
        assertThrows(FlowerJsonCodec.ImportException::class.java) { FlowerJsonCodec.decode(content) }
    }

    @Test fun invalidCoordinatesAreRejectedInsteadOfDropped() {
        val json = """[{"name":"bad","type":"big_flower","lat":95,"lng":121}]"""
        assertThrows(FlowerJsonCodec.ImportException::class.java) { FlowerJsonCodec.decode(json.toByteArray()) }
    }

    @Test fun bodyLengthLimitAppliesToStreams() {
        val large = ByteArray(FlowerJsonCodec.MAX_BYTES + 1) { 65 }
        assertThrows(FlowerJsonCodec.ImportException::class.java) {
            FlowerJsonCodec.decode(ByteArrayInputStream(large))
        }
    }

    @Test fun unknownJsonAndEmptyArrayDoNotCreateFakeFlowers() {
        for (json in listOf("[]","{}","not-json",
            """[{"name":"unknown","lat":25,"lng":121}]""")) {
            assertThrows(FlowerJsonCodec.ImportException::class.java) {
                FlowerJsonCodec.decode(json.toByteArray())
            }
        }
    }

    @Test fun addingNearbyNeverSilentlyTruncatesExistingDraft() {
        val draft = (1..15).map { i -> at(i * 0.001) }
        assertThrows(IllegalArgumentException::class.java) {
            FlowerFinder.appendToDraft(centre, draft, listOf(FlowerSpot.imported("new", at(.03))), 14)
        }
        assertEquals(15, draft.size)
    }

    @Test fun storageLimitRejectsOversizedMergeWithoutDamagingOriginal() {
        val original = FlowerSpot.manual("已保存", centre)
        val incoming = FlowerSpot.imported("新花",at(.05))
        assertThrows(IllegalArgumentException::class.java) {
            FlowerFinder.combine(listOf(original),listOf(incoming),maxStored=1)
        }
        assertEquals("已保存", original.name)
    }
}
