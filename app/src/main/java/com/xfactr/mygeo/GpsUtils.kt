package com.xfactr.mygeo

import android.Manifest.permission.*
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.xfactr.mygeo.databinding.ActivityMapsBinding

class GpsUtils : AppCompatActivity(), OnMapReadyCallback {

    private val TAG = "GpsUtils"
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var geofencePendingIntent: PendingIntent
    private lateinit var networkChangeReceiver: NetworkChangeReceiver
    private lateinit var mSettingsClient: SettingsClient
    private lateinit var mLocationSettingsRequest: LocationSettingsRequest.Builder
    private var locationManager: LocationManager? = null
    private lateinit var locationRequest: LocationRequest
    private lateinit var mMockLocation: Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private val geofencingViewModel: GeofenceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this@GpsUtils)

        val isCoarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val isFineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        geofencingClient = LocationServices.getGeofencingClient(this)

        //check sdk
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            geofencePendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, GeofenceBroadcastReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            // Below Android S
            geofencePendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, GeofenceBroadcastReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        //check network
        networkChangeReceiver = NetworkChangeReceiver()
        registerReceiver(networkChangeReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        if (isCoarseLocationGranted && isFineLocationGranted) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            // Last Location
            fusedLocationClient.lastLocation
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val location = task.result
                        if (location != null) {
                            val mLocation = "Last Location\n" +
                                    "Latitude : ${location.latitude}\n" +
                                    "Longitude : ${location.longitude}"
                            println(mLocation)
                            val currentLatLng = LatLng(location.latitude, location.longitude)
                            binding.txtLat.text = "Latitude : ${location.latitude}"
                            binding.txtLong.text = "Longitude : ${location.longitude}"
                            onLocationObtained(currentLatLng)
                            //mLatitude = location.latitude.toInt()
                            //mLongitude = location.longitude.toString()
                        } else {
                            // Location is null, request updates
                            requestLocationUpdates()
                        }
                    } else
                        Toast.makeText(this, task.exception?.message.toString(), Toast.LENGTH_SHORT)
                            .show()
                }
            checkLocationSettings()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    ACCESS_FINE_LOCATION,
                    ACCESS_COARSE_LOCATION
                )
            )
        }

        geofencingViewModel.geofenceAdded.observe(this) {
            ToastUtil.showToast(this,"Geofence Added")
            Log.d("TAG - GeoAdded", it.toString())
        }

        geofencingViewModel.geofenceError.observe(this) {
            Log.d("TAG - GeoError", it.toString())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(networkChangeReceiver)
        geofencingViewModel.geofenceAdded.removeObservers(this)
        geofencingViewModel.geofenceError.removeObservers(this)
        removeGeofence()
    }

    private fun removeGeofence() {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("removeGeofence onSuccess", "Geofence removed successfully!")
            }
            .addOnFailureListener { exception ->
                val errorMessage = getErrorStringGeofence(exception)
                Log.e("removeGeofence", "onFailure: $errorMessage")
            }
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 100)
            .setWaitForAccurateLocation(true)
            .setMaxUpdateDelayMillis(100)
            .setIntervalMillis(3000)
            .build()

        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        /* val location = Location("network")
         fusedLocationClient.setMockLocation(location)*/
        setMockLocation(LatLng(17.5058199, 78.5487103))
        fusedLocationClient.setMockMode(true)
        fusedLocationClient.setMockLocation(mMockLocation).addOnCompleteListener {
            if (it.isSuccessful) {
                Log.d("Mock location is set", "Mock location set successfully!")
            } else {
                Log.e("Mock location Failed", "Failed to set mock location", it.exception)
            }
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )

    }

    // added mock location to test
    private fun setMockLocation(latLng: LatLng) {
        mMockLocation = Location("mock").apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
            altitude = 0.0
            time = System.currentTimeMillis()
            accuracy = 1.0f
            bearing = 1.0f
            speed = 2.0f
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val location = locationResult.lastLocation
            if (location != null) {
                val mLocation = "Updated Location\n" +
                        "Latitude : ${location.latitude}\n" +
                        "Longitude : ${location.longitude}"
                println(mLocation)
                val currentLatLng = LatLng(location.latitude, location.longitude)
                binding.txtLat.text = "Latitude : ${location.latitude}"
                binding.txtLong.text = "Longitude : ${location.longitude}"
                onLocationObtained(currentLatLng)
                fusedLocationClient.removeLocationUpdates(this)
            }
        }
    }

    private fun checkLocationSettings() {
        locationRequest = LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 100)
            .setWaitForAccurateLocation(true)
            .setMaxUpdateDelayMillis(100)
            .setIntervalMillis(3000)
            .build()

        mLocationSettingsRequest =
            LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        mLocationSettingsRequest.setAlwaysShow(true)

        mSettingsClient = LocationServices.getSettingsClient(this@GpsUtils)
        val locationSettingsResponseTask =
            mSettingsClient.checkLocationSettings(mLocationSettingsRequest.build())
        //mSettingsClient.checkLocationSettings(mLocationSettingsRequest.build())
        locationSettingsResponseTask.addOnSuccessListener { response ->
            val states = response.locationSettingsStates
            ToastUtil.showToast(this,"addOnSuccessListener")
            if (states!!.isLocationPresent) {
                Log.d(TAG, "can access the location")
                Log.d(TAG, "gps is on location are enabled!")
                //startGeofencing()
            }
        }
        locationSettingsResponseTask.addOnFailureListener { e ->
            val statusCode = (e as ResolvableApiException).statusCode
            if (statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                try {
                    Log.d(TAG, "gps is off location are disabled!")
                    ToastUtil.showToast(this,"addOnFailureListener")
                    e.startResolutionForResult(this, 100)
                } catch (ex: IntentSender.SendIntentException) {
                    Log.d("TAG-", ex.stackTraceToString())
                }
            }
        }
    }

    private fun onLocationObtained(latLng: LatLng) {
        mMap.addMarker(MarkerOptions().position(latLng).title("Current Location"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        addCircle(latLng, GEOFENCE_RADIUS)
        addGeofence(GeofenceModel("GEOFENCE_ID", latLng.latitude, latLng.longitude, GEOFENCE_RADIUS, Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT))
        //startGeofencing(latLng.latitude, latLng.longitude)
    }

    private fun addCircle(latLng: LatLng, geofenceRadius: Float) {
        val circleOptions = CircleOptions()
        circleOptions.center(latLng)
        circleOptions.radius(geofenceRadius.toDouble())
        circleOptions.strokeColor(Color.argb(255, 255, 0, 0))
        circleOptions.fillColor(Color.argb(64, 255, 0, 0))
        circleOptions.strokeWidth(4f)
        mMap.addCircle(circleOptions)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        //mMap.isMyLocationEnabled
        mMap.animateCamera(CameraUpdateFactory.zoomTo(18.0f))
    }

    private fun addGeofence(geofenceModel: GeofenceModel) {
        val geofence = Geofence.Builder()
            .setRequestId(geofenceModel.id)
            .setCircularRegion(geofenceModel.latitude, geofenceModel.longitude, GEOFENCE_RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setLoiteringDelay(4000)
            .setTransitionTypes(geofenceModel.transitionTypes)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        /*val location = Location("network")
        fusedLocationClient.setMockLocation(location)*/
        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "onSuccess: Geofence Added...")
                geofencingViewModel.setGeofenceAdded(true)
            }
            .addOnFailureListener { exception ->
                val errorMessage = getErrorStringGeofence(exception)
                Log.d(TAG, "onFailure: $errorMessage")
                geofencingViewModel.setGeofenceAdded(false)
                geofencingViewModel.setGeofenceError(exception.message)
            }
    }

    private fun getErrorStringGeofence(exe: Exception): String {
        return if (exe is ApiException) {
            when (exe.statusCode) {
                GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> "Geofence not available"
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> "Too many geofences"
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> "Too many pending intents"
                else -> "Unknown geofence error"
            }
        } else {
            exe.message ?: "Unknown error"
        }
    }

    companion object {
        private const val GEOFENCE_RADIUS = 100.0f // in meters
        private const val GEOFENCE_EXPIRATION = 86400000L // 24 hours in milliseconds
        private const val PERMISSION_REQUEST_LOCATION = 1002
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}