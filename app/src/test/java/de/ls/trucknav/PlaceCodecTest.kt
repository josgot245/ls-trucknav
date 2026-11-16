package de.ls.trucknav

import de.ls.trucknav.data.PlaceCodec
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCodecTest {

    private val berlin = Place("Berlin", "Hauptstadt", GeoPoint(52.52, 13.405))
    private val hamburg = Place("Hamburg", "", GeoPoint(53.55, 9.99))

    @Test
    fun roundTripsPlaces() {
        val encoded = PlaceCodec.encode(listOf(berlin, hamburg))
        assertEquals(listOf(berlin, hamburg), PlaceCodec.decode(encoded))
    }

    @Test
    fun emptyListRoundTrips() {
        assertTrue(PlaceCodec.decode(PlaceCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun nullOrBlankYieldsEmpty() {
        assertTrue(PlaceCodec.decode(null).isEmpty())
        assertTrue(PlaceCodec.decode("   ").isEmpty())
    }

    @Test
    fun malformedJsonYieldsEmpty() {
        assertTrue(PlaceCodec.decode("{kein json").isEmpty())
        assertTrue(PlaceCodec.decode("[{\"name\":\"ohne Punkt\"}]").isEmpty())
    }
}
