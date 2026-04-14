package com.ifpr.androidapptemplate.ui.meusprojetos

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.baseclasses.Proposta
import com.ifpr.androidapptemplate.ui.detalhes.DetalhesServicoActivity

class MeusProjetosFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var itemContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_meus_projetos, container, false)

        tabLayout = view.findViewById(R.id.tabLayoutProjetos)
        itemContainer = view.findViewById(R.id.itemContainer)
        emptyState = view.findViewById(R.id.emptyState)
        emptyStateText = view.findViewById(R.id.emptyStateText)

        val btnBack = view.findViewById<View>(R.id.btnBack)
        btnBack?.setOnClickListener {
            findNavController().popBackStack()
        }

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        tabLayout.addTab(tabLayout.newTab().setText("Publicados"))
        tabLayout.addTab(tabLayout.newTab().setText("Candidaturas"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    carregarPublicados()
                } else {
                    carregarCandidaturas()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        carregarPublicados() // Default

        return view
    }

    private fun carregarPublicados() {
        itemContainer.removeAllViews()
        val uid = auth.currentUser?.uid ?: return
        
        database.getReference("itens").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                        emptyState.visibility = View.VISIBLE
                        emptyStateText.text = "Você ainda não publicou nada"
                        return
                    }

                    emptyState.visibility = View.GONE
                    for (itemSnapshot in snapshot.children) {
                        val item = itemSnapshot.getValue(Item::class.java) ?: continue
                        if (item.id.isNullOrEmpty()) item.id = itemSnapshot.key
                        adicionarItemNaView(item, "Publicado")
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun carregarCandidaturas() {
        itemContainer.removeAllViews()
        val uid = auth.currentUser?.uid ?: return
        
        database.getReference("propostas")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val projetos = mutableMapOf<String, String>()
                    for (projetoSnapshot in snapshot.children) {
                        for (propostaSnapshot in projetoSnapshot.children) {
                            val prop = propostaSnapshot.getValue(Proposta::class.java)
                            if (prop != null && prop.uidCandidato == uid) {
                                projetos[projetoSnapshot.key!!] = prop.status ?: "pendente"
                            }
                        }
                    }

                    if (projetos.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        emptyStateText.text = "Minhas candidaturas aparecerão aqui"
                    } else {
                        emptyState.visibility = View.GONE
                        buscarItensPorIds(projetos)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun buscarItensPorIds(projetos: Map<String, String>) {
        database.getReference("itens").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                for (userSnapshot in snapshot.children) {
                    for (itemSnapshot in userSnapshot.children) {
                        if (projetos.containsKey(itemSnapshot.key)) {
                            val item = itemSnapshot.getValue(Item::class.java) ?: continue
                            if (item.id.isNullOrEmpty()) item.id = itemSnapshot.key
                            val propStatus = projetos[item.id!!] ?: "pendente"
                            
                            val label = if (propStatus == "aceito") {
                                "VOCÊ FOI ACEITO!"
                            } else {
                                "CANDIDATURA (PENDENTE)"
                            }
                            
                            adicionarItemNaView(item, label)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun adicionarItemNaView(item: Item, labelTag: String) {
        val itemView = LayoutInflater.from(context).inflate(R.layout.item_template, itemContainer, false)

        itemView.findViewById<TextView>(R.id.item_badge_categoria).text = item.categoria ?: "Outro"
        itemView.findViewById<TextView>(R.id.item_titulo).text = item.titulo ?: "Sem título"
        itemView.findViewById<TextView>(R.id.item_descricao).text = item.descricao ?: ""
        itemView.findViewById<TextView>(R.id.item_endereco).text = "📍 ${item.endereco ?: "Não informado"}"
        
        val itemStatusText = itemView.findViewById<TextView>(R.id.item_status_badge)
        itemStatusText.visibility = View.VISIBLE
        itemStatusText.text = labelTag.uppercase()

        val imageView = itemView.findViewById<ImageView>(R.id.item_image)
        val imageCard = itemView.findViewById<CardView>(R.id.item_image_card)

        if (!item.imageUrl.isNullOrEmpty()) {
            imageCard.visibility = View.VISIBLE
            Glide.with(this).load(item.imageUrl).into(imageView)
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
