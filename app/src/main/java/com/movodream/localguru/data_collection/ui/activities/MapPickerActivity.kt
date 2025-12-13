package com.movodream.localguru.data_collection.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.core.preferences.MyPreference
import com.core.preferences.PrefKey
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.movodream.localguru.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedClient: FusedLocationProviderClient
    private var selectedLatLng: LatLng? = null
    private lateinit var addressTxt: TextView
    private var pinView: View? = null
    private var geocoder: Geocoder? = null
    private var locationInfo = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        addressTxt = findViewById(R.id.addressTxt)
        pinView = findViewById(R.id.imgPin)
        geocoder = Geocoder(this, Locale.getDefault())
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<Button>(R.id.btnSelectLocation).setOnClickListener {
            selectedLatLng?.let { pos ->
                setResult(
                    RESULT_OK,
                    Intent().apply {
                        putExtra("lat", pos.latitude)
                        putExtra("lng", pos.longitude)
                        putExtra("address", addressTxt.text.toString())
                        putExtra("locationInfo", locationInfo)
                    }
                )
                finish()
            }
        }
    }

    override fun onMapReady(gMap: GoogleMap) {
        map = gMap
        map.uiSettings.apply {
            isMyLocationButtonEnabled = true
            isCompassEnabled = true
            isZoomControlsEnabled = false
        }

        val initialLat = MyPreference.getValueString(PrefKey.LATITUDE,"0.0")!!.toDouble()
        val initialLng = MyPreference.getValueString(PrefKey.LONGITUDE,"0.0")!!.toDouble()
        if (initialLat != 0.0 && initialLng != 0.0) {
            val pos = LatLng(initialLat, initialLng)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
            selectedLatLng = pos
            fetchAddress(pos)
        }

        enableLocation()

        map.setOnCameraMoveListener {
            // while moving => show loading like Uber
            addressTxt.text = "Fetching location..."
            animatePin(UP = true)
        }

        map.setOnCameraIdleListener {
            selectedLatLng = map.cameraPosition.target
            fetchAddress(selectedLatLng!!)
            animatePin(UP = false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation() {
        if (hasPermission()) {
            map.isMyLocationEnabled = true
            fusedClient.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build(), null
            ).addOnSuccessListener { loc ->
                loc?.let {
                    val pos = LatLng(it.latitude, it.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
                }
            }
        } else requestPermissions()
    }

//    private fun fetchAddress(latLng: LatLng) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val result = geocoder?.getFromLocation(latLng.latitude, latLng.longitude, 1)
//                withContext(Dispatchers.Main) {
//                    addressTxt.text = result?.firstOrNull()?.getAddressLine(0)
//                        ?: "${latLng.latitude}, ${latLng.longitude}"
//                }
//            } catch (_: Exception) {}
//        }
//    }
private fun fetchAddress(latLng: LatLng) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val geocoder = Geocoder(this@MapPickerActivity, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 5)

            val address = addresses?.firstOrNull()

            val fullAddress = address?.let {
                // Build full address string safely
                buildString {
                    for (i in 0..it.maxAddressLineIndex) {
                        val line = it.getAddressLine(i)
                        if (!line.isNullOrBlank()) append(line).append("\n")
                    }
                }.trim()
            } ?: "${latLng.latitude}, ${latLng.longitude}"

            val city = when {
                !address?.locality.isNullOrBlank() -> address!!.locality
                !address?.subLocality.isNullOrBlank() -> address!!.subLocality
                !address?.subAdminArea.isNullOrBlank() -> address!!.subAdminArea
                !address?.featureName.isNullOrBlank() -> address!!.featureName
                else -> ""
            }
            val state = address?.adminArea ?: ""
            val country = address?.countryName ?: ""
            locationInfo = listOf(city, state, country).joinToString("|")

            withContext(Dispatchers.Main) {
                addressTxt.text = fullAddress
              Log.d("Address ::",addressTxt.text.toString())
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                addressTxt.text = "${latLng.latitude}, ${latLng.longitude}"

            }
        }
    }
}


    /** Uber-style pin bounce */
    private fun animatePin(UP: Boolean) {
        pinView?.animate()?.translationY(if (UP) -20f else 0f)
            ?.setDuration(150)?.start()
    }
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ), 101
        )
    }
    private fun hasPermission() =
        ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}


