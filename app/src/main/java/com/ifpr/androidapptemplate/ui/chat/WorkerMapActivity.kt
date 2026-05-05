package com.ifpr.androidapptemplate.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.ifpr.androidapptemplate.R
import kotlinx.coroutines.*
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URL
import kotlin.math.*

class WorkerMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var btnBack: ImageButton
    private lateinit var txtMapTitle: TextView
    private lateinit var txtMapSubtitle: TextView
    private lateinit var txtMapDistance: TextView
    private lateinit var txtMapClientAddress: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var workerMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var routePolyline: Polyline? = null

    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var projectName: String = ""
    private var clientAddress: String = ""

    companion object {
        private const val LOCATION_PERMISSION_CODE = 201

        /**
         * Tile source CartoDB Dark Matter — mapa escuro estilo Uber, gratuito, sem chave.
         */
        private val CARTO_SERVERS = arrayOf(
            "https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/",
            "https://cartodb-basemaps-b.global.ssl.fastly.net/dark_all/",
            "https://cartodb-basemaps-c.global.ssl.fastly.net/dark_all/"
        )

        private val DARK_TILE_SOURCE = object : OnlineTileSourceBase(
            "CartoDB Dark",
            0, 19, 256, ".png",
            CARTO_SERVERS
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = (pMapTileIndex shr 51).toInt() and 0x1F
                val x    = (pMapTileIndex and 0x3FFFFFF).toInt()
                val y    = ((pMapTileIndex shr 26) and 0x3FFFFFF).toInt()
                val idx  = (pMapTileIndex and 0x7FFFFFFF).toInt() % CARTO_SERVERS.size
                val server = CARTO_SERVERS[idx]
                return "$server$zoom/$x/$y.png"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMDroid requer configuração de user agent
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        setContentView(R.layout.activity_worker_map)

        destLat = intent.getDoubleExtra("DEST_LAT", 0.0)
        destLng = intent.getDoubleExtra("DEST_LNG", 0.0)
        projectName = intent.getStringExtra("PROJECT_NAME") ?: "Projeto"
        clientAddress = intent.getStringExtra("CLIENT_ADDRESS") ?: ""

        mapView = findViewById(R.id.osmMapView)
        btnBack = findViewById(R.id.btnBackMap)
        txtMapTitle = findViewById(R.id.txtMapTitle)
        txtMapSubtitle = findViewById(R.id.txtMapSubtitle)
        txtMapDistance = findViewById(R.id.txtMapDistance)
        txtMapClientAddress = findViewById(R.id.txtMapClientAddress)

        txtMapTitle.text = "Rota até o cliente"
        txtMapSubtitle.text = projectName
        txtMapClientAddress.text = clientAddress.ifEmpty { "Endereço do cliente" }

        btnBack.setOnClickListener { finish() }

        configurarMapa()
        adicionarMarkerDestino()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarLocalizacao()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        }
    }

    private fun configurarMapa() {
        mapView.setTileSource(DARK_TILE_SOURCE)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(14.0)

        // Centralizar no destino inicialmente
        mapView.controller.setCenter(GeoPoint(destLat, destLng))
    }

    private fun adicionarMarkerDestino() {
        val destPoint = GeoPoint(destLat, destLng)
        destinationMarker = Marker(mapView).apply {
            position = destPoint
            title = "📍 $projectName"
            snippet = clientAddress
            icon = vectorToDrawable(R.drawable.ic_marker_fixpro_worker)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(destinationMarker)
        mapView.invalidate()
    }

    private fun iniciarLocalizacao() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                atualizarPosicaoTrabalhador(location.latitude, location.longitude)
            }
        }

        val req = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, mainLooper)
        }
    }

    private fun atualizarPosicaoTrabalhador(lat: Double, lng: Double) {
        val ponto = GeoPoint(lat, lng)

        // Atualizar ou criar marker do trabalhador (posição atual)
        if (workerMarker == null) {
            workerMarker = Marker(mapView).apply {
                title = "Você está aqui"
                icon = criarMarkerCirculo()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(workerMarker)
        }
        workerMarker!!.position = ponto

        // Mostrar distância ao destino
        val dist = haversineKm(lat, lng, destLat, destLng)
        val distStr = if (dist < 1.0) "${(dist * 1000).toInt()}m" else "${"%.1f".format(dist)}km"
        txtMapDistance.text = distStr

        // Buscar e desenhar rota via OSRM
        buscarRota(lat, lng, destLat, destLng)

        mapView.invalidate()
    }

    /**
     * Chama a API pública OSRM para obter a rota entre dois pontos.
     * Nenhuma chave necessária.
     */
    private fun buscarRota(oLat: Double, oLng: Double, dLat: Double, dLng: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "http://router.project-osrm.org/route/v1/driving/$oLng,$oLat;$dLng,$dLat?overview=full&geometries=geojson"
                val response = URL(url).readText()
                val json = JSONObject(response)

                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) return@launch

                val route = routes.getJSONObject(0)
                val distanceMeters = route.getDouble("distance")
                val durationSeconds = route.getDouble("duration")

                val geometry = route.getJSONObject("geometry")
                val coords = geometry.getJSONArray("coordinates")

                val pontos = mutableListOf<GeoPoint>()
                for (i in 0 until coords.length()) {
                    val coord = coords.getJSONArray(i)
                    pontos.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                }

                val distKm = distanceMeters / 1000.0
                val minutos = (durationSeconds / 60).toInt()
                val distLabel = if (distKm < 1.0) "${distanceMeters.toInt()}m" else "${"%.1f".format(distKm)}km"

                withContext(Dispatchers.Main) {
                    // Remover rota anterior
                    routePolyline?.let { mapView.overlays.remove(it) }

                    // Desenhar nova rota em teal FixPro
                    routePolyline = Polyline(mapView).apply {
                        setPoints(pontos)
                        outlinePaint.color = android.graphics.Color.parseColor("#00C9A7")
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.isAntiAlias = true
                    }
                    mapView.overlays.add(0, routePolyline)

                    txtMapDistance.text = distLabel
                    txtMapSubtitle.text = "$projectName · ~${minutos}min"

                    mapView.invalidate()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    txtMapSubtitle.text = "$projectName · rota indisponível"
                }
            }
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Converte vector drawable em BitmapDrawable para uso como marker OSMDroid
     */
    private fun vectorToDrawable(resId: Int): Drawable {
        val drawable = ContextCompat.getDrawable(this, resId)!!
        val bitmap = Bitmap.createBitmap(96, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    /**
     * Cria um círculo teal pequeno para representar a posição atual do trabalhador
     */
    private fun criarMarkerCirculo(): Drawable {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#00C9A7")
            style = android.graphics.Paint.Style.FILL
        }
        val border = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(20f, 20f, 16f, paint)
        canvas.drawCircle(20f, 20f, 16f, border)
        return BitmapDrawable(resources, bitmap)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarLocalizacao()
        } else {
            Toast.makeText(this, "Permissão de localização necessária para a rota", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        mapView.onDetach()
    }
}
