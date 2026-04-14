package com.ifpr.androidapptemplate.ui.home

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
import com.bumptech.glide.Glide
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.ui.detalhes.DetalhesServicoActivity
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    private lateinit var itemContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var scrollView: ScrollView

    // Guarda todos os itens carregados
    private val todosItens = mutableListOf<Item>()

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
        carregarItensMarketplace()
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

                exibirItens()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun exibirItens() {
        if (!isAdded) return

        itemContainer.removeAllViews()

        if (todosItens.isEmpty()) {
            scrollView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            emptyStateText.text = "Nenhum projeto encontrado no momento"
            return
        }

        scrollView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        for (item in todosItens) {
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