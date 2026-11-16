package de.ls.trucknav

import de.ls.trucknav.util.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun newerPatchIsDetected() {
        assertTrue(Version.isNewer("0.3.1", "0.3.0"))
        assertFalse(Version.isNewer("0.3.0", "0.3.1"))
    }

    @Test
    fun newerMinorAndMajorAreDetected() {
        assertTrue(Version.isNewer("0.4.0", "0.3.9"))
        assertTrue(Version.isNewer("1.0.0", "0.99.99"))
    }

    @Test
    fun leadingVIsIgnored() {
        assertTrue(Version.isNewer("v0.4.0", "0.3.0"))
        assertFalse(Version.isNewer("v0.3.0", "0.3.0"))
    }

    @Test
    fun equalVersionsAreNotNewer() {
        assertFalse(Version.isNewer("0.4.0", "0.4.0"))
        assertEquals(0, Version.compare("0.4.0", "0.4.0"))
    }

    @Test
    fun multiDigitSegmentsCompareNumerically() {
        assertTrue(Version.isNewer("0.10.0", "0.9.0"))
        assertFalse(Version.isNewer("0.9.0", "0.10.0"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertFalse(Version.isNewer("1.2", "1.2.0"))
        assertTrue(Version.isNewer("1.2.1", "1.2"))
    }

    @Test
    fun preReleaseSuffixIsIgnored() {
        // "0.4.0-beta" ist gleichwertig zu "0.4.0" fuer den Vergleich.
        assertFalse(Version.isNewer("0.4.0-beta", "0.4.0"))
        assertFalse(Version.isNewer("0.4.0", "0.4.0-debug"))
    }
}
