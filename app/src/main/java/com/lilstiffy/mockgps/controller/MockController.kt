package com.lilstiffy.mockgps.controller

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coordinates interactions with the Android mock location APIs.
 * Delegated to by [com.lilstiffy.mockgps.service.MockLocationService].
 */
class MockController(
    private val context: Context
) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val scope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null
    private var providerRegistered = false

    /**
     * Starts pushing mock GPS updates using the supplied [latLngProvider].
     * Returns 	rue if updates could be scheduled.
     */
    fun start(latLngProvider: () -> LatLng): Boolean {
        if (updateJob?.isActive == true) {
            return true
        }

        if (!providerRegistered && !ensureTestProvider()) {
            return false
        }

        updateJob = scope.launch {
            locationManager.setTestProviderEnabled(PROVIDER_NAME, true)

            while (isActive) {
                val latLng = latLngProvider()
                val location = Location(PROVIDER_NAME).apply {
                    latitude = latLng.latitude
                    longitude = latLng.longitude
                    altitude = DEFAULT_ALTITUDE
                    time = System.currentTimeMillis()
                    accuracy = DEFAULT_ACCURACY
                    elapsedRealtimeNanos = System.nanoTime()
                }

                locationManager.setTestProviderLocation(PROVIDER_NAME, location)
                delay(LOCATION_PUSH_DELAY_MS)
            }
        }

        return true
    }

    /**
     * Stops the active mock location loop if one is running.
     */
    fun stop() {
        updateJob?.cancel()
        updateJob = null

        if (providerRegistered) {
            locationManager.setTestProviderEnabled(PROVIDER_NAME, false)
        }
    }

    private fun ensureTestProvider(): Boolean {
        return try {
            locationManager.addTestProvider(
                PROVIDER_NAME,
                /* requiresNetwork = */ true,
                /* requiresSatellite = */ false,
                /* requiresCell = */ false,
                /* hasMonetaryCost = */ false,
                /* supportsAltitude = */ true,
                /* supportsSpeed = */ true,
                /* supportsBearing = */ true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
            providerRegistered = true
            true
        } catch (securityException: SecurityException) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "Mock location failed, make sure MockGPS is the selected mock location app.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        } catch (illegalArgumentException: IllegalArgumentException) {
            // The provider already exists – treat as success.
            providerRegistered = true
            true
        }
    }

    private companion object {
        const val PROVIDER_NAME = LocationManager.GPS_PROVIDER
        const val LOCATION_PUSH_DELAY_MS = 200L
        const val DEFAULT_ALTITUDE = 12.5
        const val DEFAULT_ACCURACY = 2f
    }
}
