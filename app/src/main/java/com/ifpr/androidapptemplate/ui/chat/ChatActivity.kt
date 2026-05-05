package com.ifpr.androidapptemplate.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.baseclasses.Message

class ChatActivity : AppCompatActivity() {

    private lateinit var btnBackChat: ImageButton
    private lateinit var txtChatParticipantName: TextView
    private lateinit var txtChatProjectName: TextView
    private lateinit var edtMessageInput: EditText
    private lateinit var btnSendMessage: FrameLayout
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var btnVerRota: FrameLayout

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private var propostaId: String? = null
    private var participantName: String? = null
    private var projectName: String? = null
    private var itemId: String? = null
    private var donoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        propostaId = intent.getStringExtra("PROPOSTA_ID")
        participantName = intent.getStringExtra("PARTICIPANT_NAME") ?: "Contato"
        projectName = intent.getStringExtra("PROJECT_NAME") ?: "Negociação"
        itemId = intent.getStringExtra("ITEM_ID")
        donoId = intent.getStringExtra("DONO_ID")

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("chats").child(propostaId ?: "")

        btnBackChat = findViewById(R.id.btnBackChat)
        txtChatParticipantName = findViewById(R.id.txtChatParticipantName)
        txtChatProjectName = findViewById(R.id.txtChatProjectName)
        edtMessageInput = findViewById(R.id.edtMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer)
        chatScrollView = findViewById(R.id.chatScrollView)
        btnVerRota = findViewById(R.id.btnVerRota)

        txtChatParticipantName.text = participantName
        txtChatProjectName.text = projectName

        btnBackChat.setOnClickListener { finish() }
        btnSendMessage.setOnClickListener { sendMessage() }

        // Mostrar botão de rota SOMENTE para o trabalhador (quem não é o dono do item)
        val currentUid = auth.currentUser?.uid
        if (donoId != null && currentUid != null && currentUid != donoId) {
            btnVerRota.visibility = View.VISIBLE
            btnVerRota.setOnClickListener {
                abrirMapaRota()
            }
        }

        if (propostaId != null) {
            listenForMessages()
        }
    }

    /**
     * Busca as coordenadas do item no Firebase e abre o WorkerMapActivity
     */
    private fun abrirMapaRota() {
        if (itemId == null || donoId == null) {
            Toast.makeText(this, "Dados do projeto indisponíveis", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseDatabase.getInstance().getReference("itens")
            .child(donoId!!)
            .child(itemId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val item = snapshot.getValue(Item::class.java)
                    if (item?.latitude == null || item.longitude == null) {
                        Toast.makeText(
                            this@ChatActivity,
                            "Este projeto não tem localização definida",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    val intent = Intent(this@ChatActivity, WorkerMapActivity::class.java).apply {
                        putExtra("DEST_LAT", item.latitude!!)
                        putExtra("DEST_LNG", item.longitude!!)
                        putExtra("PROJECT_NAME", projectName)
                        putExtra("CLIENT_ADDRESS", item.endereco ?: "")
                    }
                    startActivity(intent)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ChatActivity, "Erro ao buscar localização", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun listenForMessages() {
        database.child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatMessagesContainer.removeAllViews()
                val currentUserId = auth.currentUser?.uid
                for (msgSnapshot in snapshot.children) {
                    val msg = msgSnapshot.getValue(Message::class.java) ?: continue

                    val itemView = LayoutInflater.from(this@ChatActivity).inflate(
                        R.layout.item_message, chatMessagesContainer, false
                    ) as LinearLayout
                    val textSender = itemView.findViewById<TextView>(R.id.textMessageSenderName)
                    val textBody = itemView.findViewById<TextView>(R.id.textMessageBody)
                    val layoutBubble = itemView.findViewById<LinearLayout>(R.id.layoutMessageBubble)

                    textSender.text = msg.senderName
                    textBody.text = msg.text

                    val isMe = (msg.senderId == currentUserId)

                    if (isMe) {
                        itemView.gravity = Gravity.END
                        layoutBubble.setBackgroundResource(R.drawable.bg_chat_bubble_my)
                        textSender.visibility = View.GONE
                    } else {
                        itemView.gravity = Gravity.START
                        layoutBubble.setBackgroundResource(R.drawable.bg_chat_bubble_other)
                        textSender.visibility = View.VISIBLE
                    }

                    chatMessagesContainer.addView(itemView)
                }

                chatScrollView.post {
                    chatScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun sendMessage() {
        val text = edtMessageInput.text.toString().trim()
        if (text.isEmpty() || propostaId == null) return

        val user = auth.currentUser ?: return
        val msgRef = database.child("messages").push()
        val msg = Message(
            senderId = user.uid,
            senderName = user.displayName ?: "Usuário",
            text = text,
            timestamp = System.currentTimeMillis()
        )

        msgRef.setValue(msg).addOnSuccessListener {
            edtMessageInput.text.clear()
        }
    }
}
