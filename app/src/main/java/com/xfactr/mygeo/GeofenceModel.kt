package com.xfactr.mygeo

data class GeofenceModel(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val transitionTypes: Int
)