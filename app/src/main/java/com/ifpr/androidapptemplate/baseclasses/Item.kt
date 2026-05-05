package com.ifpr.androidapptemplate.baseclasses

data class Item(
    var id: String? = null,
    var endereco: String? = null,
    val base64Image: String? = null,
    val imageUrl: String? = null,
    var titulo: String? = null,
    var descricao: String? = null,
    var categoria: String? = null,
    var nomeUsuario: String? = null,
    var uidUsuario: String? = null,
    var status: String? = "aberto", // aberto, em_andamento, concluido
    var latitude: Double? = null,
    var longitude: Double? = null
)
