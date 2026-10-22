package com.fixedsource.mobile.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationHelper {

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Best-effort last known position. Field capture remains usable without a fix;
     * the watermark then states that coordinates were unavailable.
     */
    @SuppressLint("MissingPermission")
    fun bestLastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .maxByOrNull { it.time }
    }

    fun coordinateLabel(location: Location?): String {
        if (location == null) return "坐标未获取"
        return String.format(
            Locale.US,
            "%.5f, %.5f",
            location.latitude,
            location.longitude
        )
    }
}
