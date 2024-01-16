//package com.example.uberclone.di
//
//import android.app.PendingIntent
//import android.content.Context
//import androidx.core.app.NotificationCompat
//import com.example.uberclone.R
//import com.example.uberclone.utils.TaxiConstants.CHANNEL_ID
//import com.example.uberclone.utils.TaxiConstants.NOTIFICATION_TITLE
//import com.google.android.gms.location.LocationServices
//import dagger.Module
//import dagger.Provides
//import dagger.hilt.InstallIn
//import dagger.hilt.android.components.ServiceComponent
//import dagger.hilt.android.qualifiers.ApplicationContext
//import dagger.hilt.android.scopes.ServiceScoped
//
//@Module
//@InstallIn(ServiceComponent::class)
//object ModuleService {
//    @Provides
//    @ServiceScoped
//    fun provideFusedLocationClient(
//        @ApplicationContext context: Context
//    ) = LocationServices.getFusedLocationProviderClient(context)
//
//
//    @Provides
//    @ServiceScoped
//    fun baseNotificationBuilder(
//        @ApplicationContext context:Context,
//        pendingIntent: PendingIntent
//    )= NotificationCompat.Builder(context, CHANNEL_ID)
//        .setAutoCancel(false)
//        .setOngoing(true) // can't be swiped away
//        .setSmallIcon(R.drawable.uber_icon)
//        .setContentTitle(NOTIFICATION_TITLE)
//        .setContentText("00:00:00")
//        .setContentIntent(pendingIntent)
//
//}