package de.ls.trucknav.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Gemeinsamer HTTP-Client mit sinnvollen Timeouts. */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Fuer Overpass: der oeffentliche Server braucht unter Last deutlich
     * laenger als Routing-Anfragen (gemessen bis ~40 s). Die Abfrage laeuft
     * im Hintergrund, daher sind groessere Timeouts unkritisch.
     */
    val slowClient: OkHttpClient = client.newBuilder()
        .readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    const val USER_AGENT = "LS-TruckNav/0.1 (interne Firmen-App)"
}
