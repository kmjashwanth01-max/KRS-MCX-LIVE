package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class UserLocationState(
    val isPermissionGranted: Boolean = false,
    val city: String = "Detecting...",
    val state: String = "Tamil Nadu",
    val country: String = "India",
    val latitude: Double = 11.0168,
    val longitude: Double = 76.9558,
    val temperatureC: Double = 30.0,
    val windSpeedKmh: Double = 11.5,
    val weatherCondition: String = "Clear & Sunny",
    val isLiveDetected: Boolean = false,
    val statusLabel: String = "Detecting Location & Weather..."
)

object LocationServiceManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val _locationState = MutableStateFlow(UserLocationState())
    val locationState: StateFlow<UserLocationState> = _locationState.asStateFlow()

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun refreshLocation(context: Context) {
        val granted = hasLocationPermission(context)
        _locationState.value = _locationState.value.copy(isPermissionGranted = granted)

        if (granted) {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (locationManager != null) {
                    val providers = listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER
                    )

                    var bestLocation: Location? = null
                    for (provider in providers) {
                        try {
                            if (locationManager.isProviderEnabled(provider)) {
                                val loc = locationManager.getLastKnownLocation(provider)
                                if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                                    bestLocation = loc
                                }
                            }
                        } catch (_: Throwable) {}
                    }

                    if (bestLocation != null) {
                        resolveCoordinatesAndWeather(context, bestLocation.latitude, bestLocation.longitude)
                    } else {
                        // Fallback to IP geolocation if indoor GPS hasn't locked yet
                        detectLocationViaNetworkAndFetchWeather(context)
                    }

                    val activeProvider = when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> null
                    }

                    if (activeProvider != null) {
                        locationManager.requestSingleUpdate(
                            activeProvider,
                            object : LocationListener {
                                override fun onLocationChanged(location: Location) {
                                    resolveCoordinatesAndWeather(context, location.latitude, location.longitude)
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                                override fun onProviderEnabled(provider: String) {}
                                override fun onProviderDisabled(provider: String) {}
                            },
                            Looper.getMainLooper()
                        )
                    }
                }
            } catch (_: Throwable) {
                detectLocationViaNetworkAndFetchWeather(context)
            }
        } else {
            // Even before GPS permission is granted, fetch approximate network city & real weather
            detectLocationViaNetworkAndFetchWeather(context)
        }
    }

    private fun resolveCoordinatesAndWeather(context: Context, lat: Double, lon: Double) {
        scope.launch {
            var resolvedCity = _locationState.value.city
            var resolvedState = _locationState.value.state
            var resolvedCountry = _locationState.value.country

            try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.ENGLISH).getFromLocation(lat, lon, 1)
                val first = addresses?.firstOrNull()
                if (first != null) {
                    resolvedCity = first.locality ?: first.subAdminArea ?: first.featureName ?: resolvedCity
                    resolvedState = first.adminArea ?: resolvedState
                    resolvedCountry = first.countryName ?: resolvedCountry
                }
            } catch (_: Throwable) {}

            // If Geocoder returned "Detecting...", try reverse geocoding via Nominatim/BigDataCloud
            if (resolvedCity == "Detecting..." || resolvedCity.isBlank()) {
                try {
                    val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string().orEmpty())
                            val c = json.optString("city").ifBlank { json.optString("locality") }
                            val s = json.optString("principalSubdivision")
                            if (c.isNotBlank()) resolvedCity = c
                            if (s.isNotBlank()) resolvedState = s
                        }
                    }
                } catch (_: Throwable) {}
            }

            if (resolvedCity == "Detecting..." || resolvedCity.isBlank()) {
                resolvedCity = "Coimbatore"
            }

            val (tempC, windKmh, condition) = fetchLiveOpenMeteoWeather(lat, lon)

            _locationState.value = UserLocationState(
                isPermissionGranted = hasLocationPermission(context),
                city = resolvedCity,
                state = resolvedState,
                country = resolvedCountry,
                latitude = lat,
                longitude = lon,
                temperatureC = tempC,
                windSpeedKmh = windKmh,
                weatherCondition = condition,
                isLiveDetected = true,
                statusLabel = "$resolvedCity • ${String.format("%.1f", tempC)}°C $condition"
            )
        }
    }

    private fun detectLocationViaNetworkAndFetchWeather(context: Context) {
        scope.launch {
            var lat = 11.0168
            var lon = 76.9558
            var city = "Coimbatore"
            var state = "Tamil Nadu"
            var country = "India"

            try {
                val req = Request.Builder().url("https://ipapi.co/json/").header("User-Agent", "JashxAI/2.0").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val c = json.optString("city")
                        val r = json.optString("region")
                        val cn = json.optString("country_name")
                        val la = json.optDouble("latitude", Double.NaN)
                        val lo = json.optDouble("longitude", Double.NaN)
                        if (c.isNotBlank()) city = c
                        if (r.isNotBlank()) state = r
                        if (cn.isNotBlank()) country = cn
                        if (!la.isNaN() && !lo.isNaN()) {
                            lat = la
                            lon = lo
                        }
                    }
                }
            } catch (_: Throwable) {}

            val (tempC, windKmh, condition) = fetchLiveOpenMeteoWeather(lat, lon)

            _locationState.value = UserLocationState(
                isPermissionGranted = hasLocationPermission(context),
                city = city,
                state = state,
                country = country,
                latitude = lat,
                longitude = lon,
                temperatureC = tempC,
                windSpeedKmh = windKmh,
                weatherCondition = condition,
                isLiveDetected = true,
                statusLabel = "$city • ${String.format("%.1f", tempC)}°C $condition"
            )
        }
    }

    fun fetchLiveOpenMeteoWeather(lat: Double, lon: Double): Triple<Double, Double, String> {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val current = json.optJSONObject("current_weather")
                        if (current != null) {
                            val temp = current.optDouble("temperature", 30.0)
                            val wind = current.optDouble("windspeed", 10.0)
                            val code = current.optInt("weathercode", 0)
                            val desc = mapWmoWeatherCode(code)
                            return Triple(temp, wind, desc)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return Triple(30.0, 11.0, "Clear & Sunny")
    }

    private fun mapWmoWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear Sky ☀️"
            1, 2 -> "Partly Cloudy ⛅"
            3 -> "Overcast ☁️"
            45, 48 -> "Misty / Foggy 🌫️"
            51, 53, 55 -> "Light Drizzle 🌦️"
            61, 63, 65 -> "Rain Showers 🌧️"
            71, 73, 75 -> "Snowfall ❄️"
            80, 81, 82 -> "Scattered Showers 🌦️"
            95, 96, 99 -> "Thunderstorm ⛈️"
            else -> "Pleasant & Clear 🌤️"
        }
    }
}
