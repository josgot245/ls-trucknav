package de.ls.trucknav.util

/**
 * Versionsvergleich fuer Versionsnamen wie "0.3.0", "v1.2.10" oder
 * "0.4.0-beta" (Vorab-Kennungen nach '-' werden ignoriert).
 */
object Version {

    /** true, wenn [candidate] neuer ist als [current]. */
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    /** Vergleicht zwei Versionsnamen: >0 wenn a neuer, <0 wenn a aelter, 0 wenn gleich. */
    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        val count = maxOf(pa.size, pb.size)
        for (i in 0 until count) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** Zerlegt einen Versionsnamen in numerische Bestandteile ("v1.2.3-rc" -> [1, 2, 3]). */
    internal fun parts(value: String): List<Int> =
        value.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
}
