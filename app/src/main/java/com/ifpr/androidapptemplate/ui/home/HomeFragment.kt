package com.ifpr.androidapptemplate.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.util.Base64
import android.widget.*
import android.graphics.BitmapFactory
import android.content.Intent
import androidx.cardview.widget.CardView
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.ui.detalhes.DetalhesServicoActivity
import androidx.navigation.fragment.findNavController
import kotlin.math.*

class HomeFragment : Fragment() {

    private lateinit var itemContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var scrollView: ScrollView

    private lateinit var currentAddressTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    // Guarda todos os itens carregados
    private val todosItens = mutableListOf<Item>()

    // Localização atual do trabalhador
    private var minhaLatitude: Double? = null
    private var minhaLongitude: Double? = null

    // Raio de busca atual em km (começa em 10, expande se não achar)
    private var raioAtualKm: Double = 10.0

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val RAIO_INICIAL_KM = 10.0
        private const val RAIO_MAXIMO_KM = 100.0
        private const val INCREMENTO_RAIO_KM = 10.0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        itemContainer = view.findViewById(R.id.itemContainer)
        emptyState = view.findViewById(R.id.emptyState)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        scrollView = view.findViewById(R.id.scrollView)

        inicializaGerenciamentoLocalizacao(view)

        // Carregar itens do Firebase
        carregarItensMarketplace()

        val btnPainelProjetos = view.findViewById<View>(R.id.btnPainelProjetos)
        btnPainelProjetos?.setOnClickListener {
            findNavController().navigate(R.id.nav_meus_projetos)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        raioAtualKm = RAIO_INICIAL_KM
        carregarItensMarketplace()
    }

    private fun inicializaGerenciamentoLocalizacao(view: View) {
        currentAddressTextView = view.findViewById(R.id.currentAddressTextView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
        } else {
            getCurrentLocation()
        }
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Snackbar.make(
                    requireView(),
                    "Permissão de localização negada. Mostrando todos os projetos.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    minhaLatitude = location.latitude
                    minhaLongitude = location.longitude
                    displayAddress(location)
                    // Re-filtrar os itens com a nova localização
                    exibirItensComRaio()
                }
            }
        }

        locationRequest = LocationRequest.create().apply {
            interval = 30000
            fastestInterval = 30000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun displayAddress(location: Location) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "Localização obtida"
                withContext(Dispatchers.Main) {
                    currentAddressTextView.text = "📍 $address"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    currentAddressTextView.text = "📍 ${location.latitude.format(4)}, ${location.longitude.format(4)}"
                }
            }
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    /**
     * Fórmula de Haversine — calcula distância entre dois pontos em km
     */
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Raio da Terra em km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun carregarItensMarketplace() {
        val databaseRef = FirebaseDatabase.getInstance().getReference("itens")

        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                todosItens.clear()

                for (userSnapshot in snapshot.children) {
                    for (itemSnapshot in userSnapshot.children) {
                        val item = itemSnapshot.getValue(Item::class.java) ?: continue

                        // Default to open if missing
                        if (item.status == null) item.status = "aberto"
                        if (item.id.isNullOrEmpty()) item.id = itemSnapshot.key

                        // Exibir somente projetos em aberto
                        if (item.status == "aberto") {
                            todosItens.add(item)
                        }
                    }
                }

                raioAtualKm = RAIO_INICIAL_KM
                exibirItensComRaio()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Filtra e exibe itens por raio. Se não houver resultados, expande o raio automaticamente.
     */
    private fun exibirItensComRaio() {
        if (!isAdded) return

        val lat = minhaLatitude
        val lon = minhaLongitude

        // Se não temos localização ainda, mostra tudo
        if (lat == null || lon == null) {
            exibirItens(todosItens)
            return
        }

        // Filtrar por raio atual
        val itensFiltrados = todosItens.filter { item ->
            val iLat = item.latitude
            val iLon = item.longitude
            if (iLat == null || iLon == null) {
                // Item sem coordenadas (legado): inclui por segurança
                true
            } else {
                haversineKm(lat, lon, iLat, iLon) <= raioAtualKm
            }
        }

        if (itensFiltrados.isEmpty() && raioAtualKm < RAIO_MAXIMO_KM) {
            // Expandir raio e tentar novamente
            val raioAnterior = raioAtualKm
            raioAtualKm += INCREMENTO_RAIO_KM

            Snackbar.make(
                requireView(),
                "Nenhum projeto em ${raioAnterior.toInt()}km. Ampliando para ${raioAtualKm.toInt()}km...",
                Snackbar.LENGTH_SHORT
            ).show()

            exibirItensComRaio() // Recursão com novo raio
        } else {
            exibirItens(itensFiltrados)
        }
    }

    private fun exibirItens(itens: List<Item>) {
        if (!isAdded) return

        itemContainer.removeAllViews()

        if (itens.isEmpty()) {
            scrollView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            emptyStateText.text = "Nenhum projeto encontrado em ${raioAtualKm.toInt()}km"
            return
        }

        scrollView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        for (item in itens) {
            val itemView = LayoutInflater.from(itemContainer.context)
                .inflate(R.layout.item_template, itemContainer, false)

            // Badge de categoria
            val badgeCategoria = itemView.findViewById<TextView>(R.id.item_badge_categoria)
            badgeCategoria.text = item.categoria ?: "Outro"

            // Título
            val tituloView = itemView.findViewById<TextView>(R.id.item_titulo)
            tituloView.text = item.titulo ?: "Sem título"

            // Descrição
            val descricaoView = itemView.findViewById<TextView>(R.id.item_descricao)
            descricaoView.text = item.descricao ?: ""

            // Endereço
            val enderecoView = itemView.findViewById<TextView>(R.id.item_endereco)
            enderecoView.text = "📍 ${item.endereco ?: "Não informado"}"

            // Distância (se tiver localização)
            val lat = minhaLatitude
            val lon = minhaLongitude
            if (lat != null && lon != null && item.latitude != null && item.longitude != null) {
                val dist = haversineKm(lat, lon, item.latitude!!, item.longitude!!)
                val distStr = if (dist < 1.0) "${(dist * 1000).toInt()}m" else "${"%.1f".format(dist)}km"
                enderecoView.text = "📍 ${item.endereco ?: "Não informado"} · $distStr"
            }

            // Autor
            val autorView = itemView.findViewById<TextView>(R.id.item_autor)
            autorView.text = "Por: ${item.nomeUsuario ?: "Anônimo"}"

            // Status Badge
            val item_status_badge = itemView.findViewById<TextView>(R.id.item_status_badge)
            item_status_badge.visibility = View.VISIBLE
            item_status_badge.text = "ABERTO"
            item_status_badge.setTextColor(resources.getColor(R.color.fixpro_oferta_teal, null))
            item_status_badge.setBackgroundResource(R.drawable.bg_badge_oferta)

            // Imagem
            val imageView = itemView.findViewById<ImageView>(R.id.item_image)
            val imageCard = itemView.findViewById<CardView>(R.id.item_image_card)

            if (!item.imageUrl.isNullOrEmpty()) {
                imageCard.visibility = View.VISIBLE
                Glide.with(itemContainer.context).load(item.imageUrl).into(imageView)
            } else if (!item.base64Image.isNullOrEmpty()) {
                imageCard.visibility = View.VISIBLE
                try {
                    val bytes = Base64.decode(item.base64Image, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    imageView.setImageBitmap(bitmap)
                } catch (_: Exception) {
                    imageCard.visibility = View.GONE
                }
            } else {
                imageCard.visibility = View.GONE
            }

            itemView.setOnClickListener {
                val intent = Intent(requireContext(), DetalhesServicoActivity::class.java)
                intent.putExtra("ITEM_ID", item.id)
                intent.putExtra("USER_ID", item.uidUsuario)
                startActivity(intent)
            }

            itemContainer.addView(itemView)
        }
    }
}