package com.xfactr.mygeo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GeofenceViewModel : ViewModel() {

    private val _geofenceAdded = MutableLiveData<Boolean>()
    val geofenceAdded: LiveData<Boolean> = _geofenceAdded

    private val _geofenceError = MutableLiveData<String?>()
    val geofenceError: LiveData<String?> = _geofenceError

    fun setGeofenceAdded(success: Boolean) {
        _geofenceAdded.value = success
    }

    fun setGeofenceError(error: String?) {
        _geofenceError.value = error
    }

    override fun onCleared() {
        super.onCleared()
    }
}