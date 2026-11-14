package de.ls.trucknav

import de.ls.trucknav.data.MapStyleMode
import de.ls.trucknav.data.MapStyles
import de.ls.trucknav.model.GeoPoint
import de.ls.trucknav.util.DayNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayNightTest {

    private val berlin = GeoPoint(52.52, 13.405)
    private val tz: TimeZone = TimeZone.getTimeZone("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(tz).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }
        return cal.timeInMillis
    }

    private fun lengthMinutes(sun: DayNight.SunTimes): Int =
        sun.sunsetMinutes - sun.sunriseMinutes

    @Test
    fun summerDayIsLongerThanWinterDay() {
        val summer = DayNight.sunTimes(berlin, at(2024, 6, 21, 12, 0), tz)!!
        val winter = DayNight.sunTimes(berlin, at(2024, 12, 21, 12, 0), tz)!!

        val summerLen = lengthMinutes(summer)
        val winterLen = lengthMinutes(winter)

        assertTrue("Sommer laenger als Winter", summerLen > winterLen)
        // Berlin zur Sommersonnenwende ca. 16 h 50 min Tageslicht.
        assertTrue("Sommerlaenge plausibel: $summerLen", summerLen in (16 * 60)..(17 * 60 + 30))
        // Berlin zur Wintersonnenwende ca. 7 h 40 min.
        assertTrue("Winterlaenge plausibel: $winterLen", winterLen in (7 * 60)..(8 * 60 + 30))
    }

    @Test
    fun equinoxIsAboutTwelveHours() {
        val sun = DayNight.sunTimes(berlin, at(2024, 3, 20, 12, 0), tz)!!
        val len = lengthMinutes(sun)
        assertTrue("Tagundnachtgleiche ca. 12 h: $len", len in (11 * 60 + 30)..(12 * 60 + 30))
    }

    @Test
    fun sunRisesInTheMorningAndSetsInTheEvening() {
        val sun = DayNight.sunTimes(berlin, at(2024, 6, 21, 12, 0), tz)!!
        assertTrue(sun.sunriseMinutes in (3 * 60)..(6 * 60))
        assertTrue(sun.sunsetMinutes in (20 * 60)..(23 * 60))
    }

    @Test
    fun isNightFollowsTheSun() {
        assertFalse("Mittags hell", DayNight.isNight(berlin, at(2024, 6, 21, 13, 0), tz))
        assertTrue("Nachts dunkel", DayNight.isNight(berlin, at(2024, 6, 21, 2, 0), tz))
        assertTrue("Wintermorgen dunkel", DayNight.isNight(berlin, at(2024, 12, 21, 5, 0), tz))
        assertFalse("Wintermittag hell", DayNight.isNight(berlin, at(2024, 12, 21, 13, 0), tz))
    }

    @Test
    fun fallbackWithoutLocationUsesFixedWindow() {
        assertFalse(DayNight.isNight(null, at(2024, 6, 21, 12, 0), tz))
        assertTrue(DayNight.isNight(null, at(2024, 6, 21, 23, 0), tz))
        assertTrue(DayNight.isNight(null, at(2024, 6, 21, 5, 0), tz))
    }

    @Test
    fun polarDayAndNightHaveNoSunTimes() {
        val longyearbyen = GeoPoint(78.22, 15.65)
        assertNull(DayNight.sunTimes(longyearbyen, at(2024, 6, 21, 12, 0), tz))
        assertNull(DayNight.sunTimes(longyearbyen, at(2024, 12, 21, 12, 0), tz))
    }

    @Test
    fun mapStyleUrlFollowsMode() {
        assertEquals(MapStyles.LIGHT, MapStyles.url(MapStyleMode.AUTO, night = false))
        assertEquals(MapStyles.DARK, MapStyles.url(MapStyleMode.AUTO, night = true))
        assertEquals(MapStyles.LIGHT, MapStyles.url(MapStyleMode.LIGHT, night = true))
        assertEquals(MapStyles.DARK, MapStyles.url(MapStyleMode.DARK, night = false))
    }
}
