package com.xfactr.mygeo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcast"
        private const val CHANNEL_ID = "geofence_channel"
        private const val NOTIFICATION_ID = 1001
        private val DEFAULT_SOUND = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent?.hasError() == true) {
            Log.e(TAG, "Geofence error: ${geofencingEvent.errorCode}")
            return
        }

        // check the transition type
        val transitionType = geofencingEvent?.geofenceTransition

        // check the geofence ids triggered
        val triggeredGeofences = geofencingEvent?.triggeringGeofences?.map { it.requestId }
        Log.d(TAG, "Triggered geofences: $triggeredGeofences")

        // log the transition type
        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                // enter event
                Log.d(TAG, "Geofence transition enter: $transitionType")
                ToastUtil.showToast(context, "Geofence enter: $transitionType")
                showNotification(context, transitionType)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                // exit event
                Log.d(TAG, "Geofence transition exit: $transitionType")
                ToastUtil.showToast(context, "Geofence exit: $transitionType")
                showNotification(context, transitionType)
            }
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                // Dwell event
                Log.d(TAG, "Geofence transition dwell: $transitionType")
                ToastUtil.showToast(context, "Geofence dwell: $transitionType")
                showNotification(context, transitionType)
            }
        }
    }

    private fun showNotification(context: Context, transitionType: Int) {
        createNotificationChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Geofence Transition")
            .setContentText("Geofence transition: ${getTransitionString(transitionType)}")
            .setAutoCancel(true)
            .setSound(DEFAULT_SOUND)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private fun createNotificationChannel(context: Context) {
        val name = "Geofence Channel"
        val descriptionText = "Channel for geofence notifications"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun getTransitionString(transitionType: Int): String {
        return when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Enter"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Exit"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "Dwell"
            else -> "Unknown"
        }
    }
}