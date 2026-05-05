package com.ifpr.androidapptemplate.ui.dashboard

import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DashboardFragment : Fragment() {

    private lateinit var enderecoEditText: EditText
    private lateinit var tituloEditText: EditText
    private lateinit var descricaoEditText: EditText
    private lateinit var radioGroupCategoria: RadioGroup
    private lateinit var itemImageView: ImageView
    private var imageUri: Uri? = null

    private lateinit var salvarButton: Button
    private lateinit var selectImageButton: Button
    private lateinit var databaseReference: DatabaseReference
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        itemImageView = view.findViewById(R.id.image_item)
        salvarButton = view.findViewById(R.id.salvarItemButton)
        selectImageButton = view.findViewById(R.id.button_select_image)
        enderecoEditText = view.findViewById(R.id.enderecoItemEditText)
        tituloEditText = view.findViewById(R.id.tituloItemEditText)
        descricaoEditText = view.findViewById(R.id.descricaoItemEditText)
        radioGroupCategoria = view.findViewById(R.id.radioGroupCategoria)

        auth = FirebaseAuth.getInstance()

        selectImageButton.setOnClickListener {
            openFileChooser()
        }

        salvarButton.setOnClickListener {
            salvarItem()
        }

        return view
    }

    private fun openFileChooser() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun salvarItem() {
        val titulo = tituloEditText.text.toString().trim()
        val descricao = descricaoEditText.text.toString().trim()
        val endereco = enderecoEditText.text.toString().trim()

        val selectedRadioId = radioGroupCategoria.checkedRadioButtonId
        val categoria = if (selectedRadioId != -1) {
            val selectedRadio = view?.findViewById<RadioButton>(selectedRadioId)
            selectedRadio?.text.toString()
        } else { "" }

        if (titulo.isEmpty()) {
            Toast.makeText(context, "Por favor, informe o título", Toast.LENGTH_SHORT).show()
            return
        }
        if (categoria.isEmpty()) {
            Toast.makeText(context, "Por favor, selecione uma categoria", Toast.LENGTH_SHORT).show()
            return
        }
        if (descricao.isEmpty()) {
            Toast.makeText(context, "Por favor, informe a descrição", Toast.LENGTH_SHORT).show()
            return
        }
        if (endereco.isEmpty()) {
            Toast.makeText(context, "Por favor, informe o endereço", Toast.LENGTH_SHORT).show()
            return
        }

        // Desabilitar botão durante geocoding
        salvarButton.isEnabled = false
        salvarButton.text = "Verificando endereço..."

        // Geocodificar o endereço para obter lat/lng (obrigatório para raio de notificações)
        CoroutineScope(Dispatchers.IO).launch {
            var latitude: Double? = null
            var longitude: Double? = null

            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val results = geocoder.getFromLocationName(endereco, 1)
                if (!results.isNullOrEmpty()) {
                    latitude = results[0].latitude
                    longitude = results[0].longitude
                }
            } catch (_: Exception) { }

            withContext(Dispatchers.Main) {
                salvarButton.isEnabled = true
                salvarButton.text = "Publicar Projeto"

                if (latitude == null || longitude == null) {
                    Toast.makeText(
                        context,
                        "❌ Endereço não encontrado. Use um endereço completo (ex: Rua XV de Novembro, 100, Curitiba, PR).",
                        Toast.LENGTH_LONG
                    ).show()
                    return@withContext
                }

                // Endereço válido — salvar com coordenadas
                if (imageUri != null) {
                    uploadImageAndSave(latitude, longitude)
                } else {
                    val user = auth.currentUser
                    databaseReference = FirebaseDatabase.getInstance().getReference("itens")
                    val itemId = databaseReference.push().key ?: return@withContext

                    val item = Item(
                        id = itemId,
                        endereco = endereco,
                        base64Image = null,
                        imageUrl = null,
                        titulo = titulo,
                        descricao = descricao,
                        categoria = categoria,
                        nomeUsuario = user?.displayName ?: "Usuário",
                        uidUsuario = user?.uid,
                        status = "aberto",
                        latitude = latitude,
                        longitude = longitude
                    )
                    saveItemIntoDatabase(item, itemId)
                }
            }
        }
    }

    private fun uploadImageAndSave(latitude: Double, longitude: Double) {
        if (imageUri != null) {
            val inputStream = context?.contentResolver?.openInputStream(imageUri!!)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)
                val endereco = enderecoEditText.text.toString().trim()
                val titulo = tituloEditText.text.toString().trim()
                val descricao = descricaoEditText.text.toString().trim()

                val selectedRadioId = radioGroupCategoria.checkedRadioButtonId
                val categoria = if (selectedRadioId != -1) {
                    val selectedRadio = view?.findViewById<RadioButton>(selectedRadioId)
                    selectedRadio?.text.toString()
                } else { "Outro" }

                val user = auth.currentUser

                databaseReference = FirebaseDatabase.getInstance().getReference("itens")
                val itemId = databaseReference.push().key ?: return

                val item = Item(
                    id = itemId,
                    endereco = endereco,
                    base64Image = base64Image,
                    imageUrl = null,
                    titulo = titulo,
                    descricao = descricao,
                    categoria = categoria,
                    nomeUsuario = user?.displayName ?: "Usuário",
                    uidUsuario = user?.uid,
                    status = "aberto",
                    latitude = latitude,
                    longitude = longitude
                )

                saveItemIntoDatabase(item, itemId)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK
            && data != null && data.data != null
        ) {
            imageUri = data.data
            Glide.with(this).load(imageUri).into(itemImageView)
        }
    }

    private fun saveItemIntoDatabase(item: Item, itemId: String) {
        databaseReference.child(auth.uid.toString()).child(itemId).setValue(item)
            .addOnSuccessListener {
                Toast.makeText(context, "Projeto publicado com sucesso!", Toast.LENGTH_SHORT).show()
                limparFormulario()
                findNavController().navigate(R.id.navigation_home)
            }.addOnFailureListener {
                Toast.makeText(context, "Falha ao publicar", Toast.LENGTH_SHORT).show()
            }
    }

    private fun limparFormulario() {
        tituloEditText.text.clear()
        descricaoEditText.text.clear()
        enderecoEditText.text.clear()
        radioGroupCategoria.clearCheck()
        imageUri = null
        itemImageView.setImageResource(android.R.drawable.ic_menu_camera)
    }
}