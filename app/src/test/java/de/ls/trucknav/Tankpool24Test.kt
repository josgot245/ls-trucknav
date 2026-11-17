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

    private val kml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
        <Document>
          <name>kml_test.kml</name>
          <Placemark>
            <name>100301 - Nordhausen</name>
            <description><![CDATA[&nbsp;<BR>Staack Pooltankstellen GmbH<BR>&nbsp;<BR>Südstraße 10<BR>99734 Nordhausen<BR>- - Thüringen - Deutschland<BR>&nbsp;<BR>GEO-Daten geprüft<BR>Hotline: 00800 - 317 617 00<BR>&nbsp;]]></description>
            <Point><coordinates>10.79314,51.48216,0</coordinates></Point>
          </Placemark>
          <Placemark>
            <name>101001 - Winsen an der Luhe</name>
            <description><![CDATA[<img src='https://example.org/1.jpg' width='300px;'><BR>&nbsp;<BR>tankpool24 international GmbH<BR>Otto-Hahn-Straße 57<BR>21423 Winsen<BR>Harburg - Niedersachsen - Deutschland<BR>&nbsp;<BR>Produkte:<BR>- Diesel<BR>&nbsp;]]></description>
            <Point><coordinates>10.22936,53.34037,0</coordinates></Point>
          </Placemark>
        </Document>
        </kml>
    """.trimIndent()

    @Test
    fun parsesKmlPlacemarks() {
        val pois = Tankpool24Service.parseKml(kml)

        assertEquals(2, pois.size)
        val first = pois[0]
        assertEquals(100301L, first.id)
        assertEquals(TruckPoiKind.TANKPOOL24, first.kind)
        assertEquals("Tankpool24 Nordhausen", first.name)
        assertEquals(51.48216, first.point.lat, 1e-6)
        assertEquals(10.79314, first.point.lon, 1e-6)
        assertEquals("Südstraße 10, 99734 Nordhausen", first.address)
    }

    @Test
    fun addressSkipsImageAndOperatorLines() {
        val second = Tankpool24Service.parseKml(kml)[1]
        assertEquals("Tankpool24 Winsen an der Luhe", second.name)
        assertEquals("Otto-Hahn-Straße 57, 21423 Winsen", second.address)
    }

    @Test
    fun keepsPlaceNameWithSpaces() {
        val single = kml.substringBefore("</Placemark>") + "</Placemark></Document></kml>"
        val poi = Tankpool24Service.parseKml(single).single()
        assertEquals("Tankpool24 Nordhausen", poi.name)
    }

    @Test
    fun skipsMalformedPlacemarks() {
        val broken = """
            <Placemark><name>not-a-number - X</name>
              <Point><coordinates>8.0,50.0,0</coordinates></Point></Placemark>
            <Placemark><name>2 - Y</name>
              <Point><coordinates>200.0,50.0,0</coordinates></Point></Placemark>
            <Placemark><name>3 - Z</name>
              <Point><coordinates>8.0,95.0,0</coordinates></Point></Placemark>
            <Placemark><name>4 - Gueltig</name>
              <Point><coordinates>8.0,50.0,0</coordinates></Point></Placemark>
        """.trimIndent()

        val pois = Tankpool24Service.parseKml(broken)

        assertEquals(1, pois.size)
        assertEquals("Tankpool24 Gueltig", pois.single().name)
    }

    @Test
    fun placemarkWithoutCoordinatesIsSkipped() {
        val noPoint = """
            <Placemark><name>5 - Ohne Punkt</name>
              <description><![CDATA[x]]></description></Placemark>
        """.trimIndent()
        assertTrue(Tankpool24Service.parseKml(noPoint).isEmpty())
    }

    @Test
    fun extractsKmlFromZip() {
        val zip = zipOf("Navigationsdaten/kml_08012026.kml" to kml)
        val text = Tankpool24Service.unzipKml(zip)
        assertTrue(text!!.contains("100301 - Nordhausen"))
        assertTrue(text.contains("Südstraße 10"))
    }

    @Test
    fun missingKmlInZipReturnsNull() {
        val zip = zipOf("Navigationsdaten/garminCSV.csv" to "9.0,53.0")
        assertNull(Tankpool24Service.unzipKml(zip))
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
