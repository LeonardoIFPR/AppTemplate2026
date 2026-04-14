package com.ifpr.androidapptemplate.ui.chat

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Message

class ChatActivity : AppCompatActivity() {

    private lateinit var btnBackChat: ImageButton
    private lateinit var txtChatParticipantName: TextView
    private lateinit var txtChatProjectName: TextView
    private lateinit var edtMessageInput: EditText
    private lateinit var btnSendMessage: FrameLayout
    private lateinit var chatMessagesContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private var propostaId: String? = null
    private var participantName: String? = null
    private var projectName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        propostaId = intent.getStringExtra("PROPOSTA_ID")
        participantName = intent.getStringExtra("PARTICIPANT_NAME") ?: "Contato"
        projectName = intent.getStringExtra("PROJECT_NAME") ?: "Negociação"

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("chats").child(propostaId ?: "")

        btnBackChat = findViewById(R.id.btnBackChat)
        txtChatParticipantName = findViewById(R.id.txtChatParticipantName)
        txtChatProjectName = findViewById(R.id.txtChatProjectName)
        edtMessageInput = findViewById(R.id.edtMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer)
        chatScrollView = findViewById(R.id.chatScrollView)

        txtChatParticipantName.text = participantName
        txtChatProjectName.text = projectName

        btnBackChat.setOnClickListener { finish() }

        btnSendMessage.setOnClickListener { sendMessage() }

        if (propostaId != null) {
            listenForMessages()
        }
    }

    private fun listenForMessages() {
        database.child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatMessagesContainer.removeAllViews()
                val currentUserId = auth.currentUser?.uid
                for (msgSnapshot in snapshot.children) {
                    val msg = msgSnapshot.getValue(Message::class.java) ?: continue

                    val itemView = LayoutInflater.from(this@ChatActivity).inflate(R.layout.item_message, chatMessagesContainer, false) as LinearLayout
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

                // Scroll to bottom
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
