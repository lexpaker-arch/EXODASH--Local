package com.exodash

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GroqClient {

    private const val URL_API = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODELO = "qwen/qwen3.8-27b"

    /**
     * Envia uma pergunta e retorna a resposta.
     * O system prompt inclui o nome do usuario (Senhor, Gleici, etc).
     */
    fun perguntar(apiKey: String, pergunta: String, nomeUsuario: String): String? {
        val systemPrompt = buildString {
            append("Voce e BOSS, um assistente automotivo formal e direto dentro de um carro. ")
            append("Trate o usuario por '")
            append(nomeUsuario)
            append("'. ")
            append("Responda em portugues brasileiro, em UMA frase curta (maximo 25 palavras). ")
            append("Nunca sugira conserto de pecas. ")
            append("Se nao souber, diga que nao tem essa informacao.")
        }

        return try {
            val url = URL(URL_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("model", MODELO)
                put("max_tokens", 200)
                put("reasoning_effort", "none")
                put("temperature", 0.3)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", pergunta)
                    })
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            if (conn.responseCode != 200) return null

            val resposta = BufferedReader(conn.inputStream.reader()).use { it.readText() }
            val json = JSONObject(resposta)
            val choices = json.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val msg = choices.getJSONObject(0).optJSONObject("message") ?: return null
            msg.optString("content", "").trim().ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }
}
