package de.ls.trucknav

import de.ls.trucknav.model.TruckPoiKind
import de.ls.trucknav.net.Tankpool24Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Tankpool24Test {

    @Test
    fun parsesGarminCsvLines() {
        val csv = """
            9.993681,53.551084,"101 - Hamburg",,
            8.682127,50.110924,"7 - Frankfurt am Main",,
        """.trimIndent()

        val pois = Tankpool24Service.parseCsv(csv)

        assertEquals(2, pois.size)
        val first = pois[0]
        assertEquals(101L, first.id)
        assertEquals(TruckPoiKind.TANKPOOL24, first.kind)
        assertEquals("Tankpool24 Hamburg", first.name)
        assertEquals(53.551084, first.point.lat, 1e-6)
        assertEquals(9.993681, first.point.lon, 1e-6)
    }

    @Test
    fun keepsPlaceNameWithSpaces() {
        val csv = "8.682127,50.110924,\"7 - Frankfurt am Main\","
        val poi = Tankpool24Service.parseCsv(csv).single()
        assertEquals("Tankpool24 Frankfurt am Main", poi.name)
    }

    @Test
    fun skipsHeaderAndMalformedLines() {
        val csv = """
            Longitude,Latitude,Name,
            ,,,
            not-a-number,50.0,"1 - X",
            8.0,not-a-number,"2 - Y",
            200.0,50.0,"3 - OutOfRangeLon",
            8.0,95.0,"4 - OutOfRangeLat",
            8.0,50.0,,
            8.0,50.0,"1 - Gueltig",
        """.trimIndent()

        val pois = Tankpool24Service.parseCsv(csv)

        assertEquals(1, pois.size)
        assertEquals("Tankpool24 Gueltig", pois.single().name)
    }

    @Test
    fun handlesCrLfAndBlankLines() {
        val csv = "9.0,53.0,\"1 - A\",\r\n\r\n9.1,53.1,\"2 - B\",\r\n"
        assertEquals(2, Tankpool24Service.parseCsv(csv).size)
    }

    @Test
    fun extractsGarminCsvFromZip() {
        val zip = zipOf("Navigationsdaten/garminCSV.csv" to "9.0,53.0,\"1 - A\",")
        val csv = Tankpool24Service.unzipCsv(zip)
        assertTrue(csv!!.contains("1 - A"))
    }

    @Test
    fun missingCsvInZipReturnsNull() {
        val zip = zipOf("Navigationsdaten/readme.txt" to "nope")
        assertNull(Tankpool24Service.unzipCsv(zip))
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.ISO_8859_1))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
