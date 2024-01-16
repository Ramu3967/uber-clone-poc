package com.example.uberclone.utils

data class Waypoint(
    val hint: String,
    val location: List<Double>,
    val name: String
)

data class Leg(
    val steps: List<Any>,
    val weight: Double,
    val distance: Double,
    val summary: String,
    val duration: Double
)

data class Geometry(
    val coordinates: List<List<Double>>,
    val type: String
)

data class Route(
    val legs: List<Leg>,
    val weightName: String,
    val geometry: Geometry,
    val weight: Double,
    val distance: Double,
    val duration: Double
)

data class OSRMResponse(
    val code: String,
    val waypoints: List<Waypoint>,
    val routes: List<Route>
)
