package com.ifpr.androidapptemplate.ui.usuario

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ifpr.androidapptemplate.R


class CadastroUsuarioActivity  : AppCompatActivity() {
    private lateinit var textCadastroUsuarioTitle: TextView
    private lateinit var registerNameEditText: EditText
    private lateinit var registerEmailEditText: EditText
    private lateinit var registerPasswordEditText: EditText
    private lateinit var registerConfirmPasswordEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var sairButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_usuario)

        textCadastroUsuarioTitle = findViewById(R.id.textCadastroUsuarioTitle)
        registerNameEditText = findViewById(R.id.registerNameEditText)
        registerEmailEditText = findViewById(R.id.registerEmailEditText)
        registerPasswordEditText = findViewById(R.id.registerPasswordEditText)
        registerConfirmPasswordEditText = findViewById(R.id.registerConfirmPasswordEditText)
        registerButton = findViewById(R.id.salvarButton)
        sairButton = findViewById(R.id.sairButton)

        sairButton.setOnClickListener {
            finish()
        }
    }
}