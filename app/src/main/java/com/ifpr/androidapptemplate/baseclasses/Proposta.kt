package com.ifpr.androidapptemplate.baseclasses

data class Proposta(
    var id: String? = null,
    var projetoId: String? = null,
    var uidCandidato: String? = null,
    var nomeCandidato: String? = null,
    var mensagem: String? = null,
    var status: String? = "pendente", // pendente, aceita, recusada
    var timestamp: Long? = null
)
