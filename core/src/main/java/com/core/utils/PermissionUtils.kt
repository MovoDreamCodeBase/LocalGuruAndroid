package com.core.utils


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager

import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.core.R
import com.core.customviews.CustomDialogBuilder
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

import kotlin.collections.all
import kotlin.collections.filterNot
import kotlin.collections.toTypedArray

class PermissionUtils(private val activity: ComponentActivity) {

    private val permissionLauncher: ActivityResultLauncher<Array<String>>
    private val locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var callback: ((Boolean) -> Unit)? = null
    private var locationCallback: ((Boolean) -> Unit)? = null

    init {
        // Must register early (in onCreate)
        permissionLauncher =
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                val allGranted = results.values.all { it }
                if (allGranted) {
                    callback?.invoke(true)
                } else {
                    showDeniedDialog()
                    callback?.invoke(false)
                }
            }

        // 🔹 Register location settings dialog result launcher
        locationSettingsLauncher =
            activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    locationCallback?.invoke(true)
                } else {
                    locationCallback?.invoke(false)
                }
            }
    }

    /** Public function to check and request only missing permissions */
    fun request(permissions: Array<String>, onResult: (Boolean) -> Unit) {
        callback = onResult
        val missingPermissions = permissions.filterNot {
            ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            // All permissions already granted
            onResult(true)
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    /** Check and show Google’s Location Enable dialog */
    fun checkAndEnableLocationSettings(onLocationReady: (Boolean) -> Unit) {
        locationCallback = onLocationReady

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val settingsClient = LocationServices.getSettingsClient(activity)
        val task = settingsClient.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // ✅ Location already ON
            onLocationReady(true)
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (e: IntentSender.SendIntentException) {
                    e.printStackTrace()
                    onLocationReady(false)
                }
            } else {
                onLocationReady(false)
            }
        }
    }

    private fun showDeniedDialog() {
        CustomDialogBuilder(activity)
            .setTitle(activity.getString(R.string.permissions_needed))
            .setMessage(
               activity.getString(R.string.permission_location_alert)
            )
            .setCancelable(false)
            .setPositiveButton("App Settings") {
                openAppSettings()
            }
            .setNegativeButton("Exit") {

                activity.finish()
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }
    fun isLocationPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Returns true if GPS (Location Provider) is enabled. */
    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}