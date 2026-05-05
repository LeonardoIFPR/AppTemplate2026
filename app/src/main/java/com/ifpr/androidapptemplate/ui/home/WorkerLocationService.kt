package com.ifpr.androidapptemplate.ui.home

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ifpr.androidapptemplate.R

/**
 * Foreground Service que mantém a localização do trabalhador atualizada no Firebase
 * enquanto o app está ativo. Isso permite que o sistema de raio funcione corretamente.
 */
class WorkerLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        private const val CHANNEL_ID = "fixpro_location_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        criarCanalDeNotificacao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, criarNotificacao())
        iniciarRastreamento()
        return START_STICKY
    }

    private fun iniciarRastreamento() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

                // Atualizar lat/lng do trabalhador no Firebase
                FirebaseDatabase.getInstance().getReference("usuarios")
                    .child(uid)
                    .updateChildren(
                        mapOf(
                            "latitude" to location.latitude,
                            "longitude" to location.longitude
                        )
                    )
            }
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 30_000        // Atualiza a cada 30s
            fastestInterval = 15_000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun criarNotificacao(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FixPro — Localização ativa")
            .setContentText("Trabalhadores próximos podem encontrar você")
            .setSmallIcon(R.drawable.ic_panel_premium)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun criarCanalDeNotificacao() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Localização do Trabalhador",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém sua localização visível para clientes próximos"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
