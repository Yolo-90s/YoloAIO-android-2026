package com.example.yoloaio.features.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight location helper that avoids the Play Services dependency. We
 * read the cached "last known" fix from each enabled provider and return the
 * freshest one — accurate enough for current-conditions weather, no streaming
 * required.
 */
object LocationProvider {

    private const val TAG = "WeatherLocation"

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun lastKnown(context: Context): Location? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            try {
                if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
            } catch (e: SecurityException) {
                Log.w(TAG, "permission lost for $provider: ${e.message}")
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        // Prefer the most recent fix; tie-broken by accuracy.
        candidates.maxByOrNull { it.time }
    }
}
