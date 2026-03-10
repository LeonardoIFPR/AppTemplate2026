package com.ifpr.androidapptemplate.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.ui.usuario.CadastroUsuarioActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerLink: TextView

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "signInWithEmail"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.edit_text_email)
        passwordEditText = findViewById(R.id.edit_text_password)
        loginButton = findViewById(R.id.button_login)
        registerLink = findViewById(R.id.registerLink)

        val registerLink: TextView = findViewById(R.id.registerLink)
        registerLink.setOnClickListener {
            val intent: Intent = Intent(
                applicationContext,
                CadastroUsuarioActivity::class.java
            )
            startActivity(intent)
        }
    }
}