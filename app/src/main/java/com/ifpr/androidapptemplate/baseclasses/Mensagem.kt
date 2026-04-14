package com.ifpr.androidapptemplate.baseclasses

data class Mensagem(
    var id: String? = null,
    var uidRemetente: String? = null,
    var nomeRemetente: String? = null,
    var texto: String? = null,
    var timestamp: Long? = null
)
