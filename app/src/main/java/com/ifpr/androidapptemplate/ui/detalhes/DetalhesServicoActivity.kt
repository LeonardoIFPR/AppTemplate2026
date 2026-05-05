package com.ifpr.androidapptemplate.ui.detalhes

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.baseclasses.Proposta

class DetalhesServicoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    
    private var itemId: String? = null
    private var donoId: String? = null
    
    private lateinit var txtTitulo: TextView
    private lateinit var txtDescricao: TextView
    private lateinit var txtCategoria: TextView
    private lateinit var txtEndereco: TextView
    private lateinit var txtAutor: TextView
    private lateinit var imageView: ImageView
    private lateinit var imageCard: CardView
    private lateinit var btnBack: ImageButton
    private lateinit var candidatosContainer: LinearLayout
    
    private lateinit var candidaturaContainer: LinearLayout
    private lateinit var txtStatusCandidatura: TextView
    private lateinit var edtMensagemProposta: EditText
    private lateinit var btnCandidatar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_servico)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        
        itemId = intent.getStringExtra("ITEM_ID")
        donoId = intent.getStringExtra("USER_ID")

        txtTitulo = findViewById(R.id.txtTitulo)
        txtDescricao = findViewById(R.id.txtDescricao)
        txtCategoria = findViewById(R.id.txtCategoria)
        txtEndereco = findViewById(R.id.txtEndereco)
        txtAutor = findViewById(R.id.txtAutor)
        imageView = findViewById(R.id.imageView)
        imageCard = findViewById(R.id.imageCard)
        btnBack = findViewById(R.id.btnBack)
        candidatosContainer = findViewById(R.id.candidatosContainer)
        
        candidaturaContainer = findViewById(R.id.candidaturaContainer)
        txtStatusCandidatura = findViewById(R.id.txtStatusCandidatura)
        edtMensagemProposta = findViewById(R.id.edtMensagemProposta)
        btnCandidatar = findViewById(R.id.btnCandidatar)

        btnBack.setOnClickListener { finish() }

        if (itemId != null && donoId != null) {
            carregarDetalhes()
            verificarStatusCandidatura()
        } else {
            Toast.makeText(this, "Erro ao carregar detalhes", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        btnCandidatar.setOnClickListener {
            enviarCandidatura()
        }
    }

    private fun carregarDetalhes() {
        database.child("itens").child(donoId!!).child(itemId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val item = snapshot.getValue(Item::class.java)
                    if (item != null) {
                        txtTitulo.text = item.titulo
                        txtDescricao.text = item.descricao
                        txtCategoria.text = item.categoria
                        txtEndereco.text = "📍 ${item.endereco}"
                        txtAutor.text = "Publicado por: ${item.nomeUsuario}"

                        if (!item.imageUrl.isNullOrEmpty()) {
                            imageCard.visibility = View.VISIBLE
                            Glide.with(this@DetalhesServicoActivity).load(item.imageUrl).into(imageView)
                        } else if (!item.base64Image.isNullOrEmpty()) {
                            imageCard.visibility = View.VISIBLE
                            try {
                                val bytes = Base64.decode(item.base64Image, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                imageView.setImageBitmap(bitmap)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun verificarStatusCandidatura() {
        val uid = auth.currentUser?.uid
        if (uid == null) return

        // Não mostra se o projeto for meu, mas mostra os candidatos!
        if (uid == donoId) {
            candidaturaContainer.visibility = View.GONE
            txtStatusCandidatura.visibility = View.VISIBLE
            txtStatusCandidatura.text = "Candidatos para o seu projeto:"
            txtStatusCandidatura.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            txtStatusCandidatura.gravity = android.view.Gravity.START
            txtStatusCandidatura.setPadding(0, 0, 0, 0)
            candidatosContainer.visibility = View.VISIBLE
            carregarCandidatosParaDono()
            return
        }

        database.child("propostas").child(itemId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var propEnviada: Proposta? = null
                    for (p in snapshot.children) {
                        val proposta = p.getValue(Proposta::class.java)
                        if (proposta?.uidCandidato == uid) {
                            if (proposta.id.isNullOrEmpty()) proposta.id = p.key
                            propEnviada = proposta
                            break
                        }
                    }

                    if (propEnviada != null) {
                        candidaturaContainer.visibility = View.GONE
                        txtStatusCandidatura.visibility = View.VISIBLE
                        
                        if (propEnviada.status == "aceito") {
                            txtStatusCandidatura.text = "VOCÊ FOI ACEITO!\nCLIQUE AQUI PARA ABRIR O CHAT"
                            txtStatusCandidatura.setBackgroundResource(R.drawable.ripple_button_primary)
                            txtStatusCandidatura.setTextColor(android.graphics.Color.WHITE)
                            txtStatusCandidatura.setPadding(32, 32, 32, 32)
                            txtStatusCandidatura.setOnClickListener {
                                val intent = android.content.Intent(this@DetalhesServicoActivity, com.ifpr.androidapptemplate.ui.chat.ChatActivity::class.java)
                                intent.putExtra("PROPOSTA_ID", propEnviada.id)
                                intent.putExtra("PARTICIPANT_NAME", txtAutor.text.toString().replace("Publicado por: ", ""))
                                intent.putExtra("PROJECT_NAME", txtTitulo.text.toString())
                                intent.putExtra("ITEM_ID", itemId)
                                intent.putExtra("DONO_ID", donoId)
                                startActivity(intent)
                            }
                        } else {
                            txtStatusCandidatura.text = "✓ Você já enviou uma proposta para este projeto"
                            txtStatusCandidatura.setOnClickListener(null)
                        }
                    } else {
                        candidaturaContainer.visibility = View.VISIBLE
                        txtStatusCandidatura.visibility = View.GONE
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    
    private fun enviarCandidatura() {
        val user = auth.currentUser ?: return
        val mensagem = edtMensagemProposta.text.toString().trim()
        
        if (mensagem.isEmpty()) {
            Toast.makeText(this, "Escreva uma mensagem na sua proposta", Toast.LENGTH_SHORT).show()
            return
        }
        
        val propRef = database.child("propostas").child(itemId!!).push()
        val propId = propRef.key ?: return
        
        val proposta = Proposta(
            id = propId,
            projetoId = itemId,
            uidCandidato = user.uid,
            nomeCandidato = user.displayName ?: "Usuário",
            mensagem = mensagem,
            status = "pendente",
            timestamp = System.currentTimeMillis()
        )
        
        propRef.setValue(proposta).addOnSuccessListener {
            Toast.makeText(this, "Candidatura enviada com sucesso!", Toast.LENGTH_SHORT).show()
            candidaturaContainer.visibility = View.GONE
            txtStatusCandidatura.visibility = View.VISIBLE
        }.addOnFailureListener {
            Toast.makeText(this, "Erro ao enviar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun carregarCandidatosParaDono() {
        if (itemId == null) return
        database.child("propostas").child(itemId!!).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                candidatosContainer.removeAllViews()
                if (!snapshot.exists()) {
                    val tv = TextView(this@DetalhesServicoActivity)
                    tv.text = "Ainda não há candidatos."
                    tv.setTextColor(android.graphics.Color.GRAY)
                    candidatosContainer.addView(tv)
                    return
                }

                for (p in snapshot.children) {
                    val proposta = p.getValue(Proposta::class.java) ?: continue
                    if (proposta.id.isNullOrEmpty()) proposta.id = p.key

                    val itemView = LayoutInflater.from(this@DetalhesServicoActivity).inflate(R.layout.item_candidato, candidatosContainer, false)
                    
                    val tvName = itemView.findViewById<TextView>(R.id.txtCandidatoNome)
                    val tvMsg = itemView.findViewById<TextView>(R.id.txtCandidatoMensagem)
                    val btnAceitar = itemView.findViewById<Button>(R.id.btnCandidatoAcao)
                    
                    tvName.text = "Candidato: ${proposta.nomeCandidato}"
                    tvMsg.text = proposta.mensagem
                    
                    if (proposta.status == "aceito") {
                        btnAceitar.text = "ABRIR CHAT"
                        btnAceitar.setBackgroundResource(R.drawable.ripple_button_primary)
                        // Make it slightly distinct if it's chat open? It already is primary!
                    } else {
                        btnAceitar.text = "ACEITAR CANDIDATO"
                        btnAceitar.setBackgroundResource(R.drawable.ripple_button_secondary)
                        btnAceitar.setTextColor(android.graphics.Color.WHITE)
                    }
                    
                    btnAceitar.setOnClickListener {
                        if (proposta.status == "aceito") {
                            val intent = android.content.Intent(this@DetalhesServicoActivity, com.ifpr.androidapptemplate.ui.chat.ChatActivity::class.java)
                            intent.putExtra("PROPOSTA_ID", proposta.id)
                            intent.putExtra("PARTICIPANT_NAME", proposta.nomeCandidato)
                            intent.putExtra("PROJECT_NAME", txtTitulo.text.toString())
                            intent.putExtra("ITEM_ID", itemId)
                            intent.putExtra("DONO_ID", donoId)
                            startActivity(intent)
                        } else {
                            aceitarCandidato(proposta)
                        }
                    }

                    candidatosContainer.addView(itemView)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun aceitarCandidato(proposta: Proposta) {
        if (itemId == null || proposta.id == null) return
        proposta.status = "aceito"
        database.child("propostas").child(itemId!!).child(proposta.id!!).setValue(proposta)
            .addOnSuccessListener {
                Toast.makeText(this, "Candidato aceito!", Toast.LENGTH_SHORT).show()
                // Update item status
                database.child("itens").child(donoId!!).child(itemId!!).child("status").setValue("em_andamento")
                carregarCandidatosParaDono() // reload UI
            }
    }
}
