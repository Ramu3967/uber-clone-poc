package com.example.uberclone.utils

object UberConstants  {
    // home fragment
    const val RIDER = "Rider"
    const val DRIVER = "Driver"

    // FB details for the active requests node - rider
    const val DB_ACTIVE_REQUESTS = "activeRequests"
    const val DB_LOCATION = "location"
    const val DB_REQUESTED_AT = "requestedAt"

    // FB details for the users node
    const val USER_TYPE = "userType"
    const val EMAIL = "email"
    const val USERS = "users"
}

enum class HOMESCREENDIRECTIONS{
    DIR_HOME, DIR_RIDER, DIR_DRIVER
}