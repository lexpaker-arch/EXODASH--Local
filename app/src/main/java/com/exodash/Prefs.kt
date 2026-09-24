package com.exodash

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("exodash_prefs", Context.MODE_PRIVATE)

    // Apps escolhidos
    var appTelefone: String
        get() = prefs.getString(KEY_TELEFONE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TELEFONE, v).apply()

    var appMusica: String
        get() = prefs.getString(KEY_MUSICA, "") ?: ""
        set(v) = prefs.edit().putString(KEY_MUSICA, v).apply()

    var appGps: String
        get() = prefs.getString(KEY_GPS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_GPS, v).apply()

    // Usuário
    var nomeUsuario: String
        get() = prefs.getString(KEY_USUARIO, "Senhor") ?: "Senhor"
        set(v) = prefs.edit().putString(KEY_USUARIO, v).apply()

    /**
     * Nome do assistente. Padrao: EXO.
     * Pode ser alterado para BOSS via opcao escondida (7 toques na versao).
     * Fica em SharedPreferences (filesDir), sobrevive a updates.
     */
    var nomeAssistente: String
        get() = prefs.getString(KEY_ASSISTENTE, "EXO") ?: "EXO"
        set(v) = prefs.edit().putString(KEY_ASSISTENTE, v).apply()

    /** Modo avancado (desbloqueado com 7 toques na versao). */
    var modoAvancado: Boolean
        get() = prefs.getBoolean(KEY_MODO_AVANCADO, false)
        set(v) = prefs.edit().putBoolean(KEY_MODO_AVANCADO, v).apply()

    // Voz
    var vozAtiva: Boolean
        get() = prefs.getBoolean(KEY_VOZ_ATIVA, true)
        set(v) = prefs.edit().putBoolean(KEY_VOZ_ATIVA, v).apply()

    var bipeAtivo: Boolean
        get() = prefs.getBoolean(KEY_BIPE_ATIVO, true)
        set(v) = prefs.edit().putBoolean(KEY_BIPE_ATIVO, v).apply()

    var idioma: String
        get() = prefs.getString(KEY_IDIOMA, "pt-BR") ?: "pt-BR"
        set(v) = prefs.edit().putString(KEY_IDIOMA, v).apply()

    // OBD
    var obdHost: String
        get() = prefs.getString(KEY_OBD_HOST, "127.0.0.1") ?: "127.0.0.1"
        set(v) = prefs.edit().putString(KEY_OBD_HOST, v).apply()

    var obdPorta: Int
        get() = prefs.getInt(KEY_OBD_PORTA, 35000)
        set(v) = prefs.edit().putInt(KEY_OBD_PORTA, v).apply()

    // Veículo
    var veiculoMarca: String
        get() = prefs.getString(KEY_VEICULO_MARCA, "") ?: ""
        set(v) = prefs.edit().putString(KEY_VEICULO_MARCA, v).apply()

    var veiculoModelo: String
        get() = prefs.getString(KEY_VEICULO_MODELO, "") ?: ""
        set(v) = prefs.edit().putString(KEY_VEICULO_MODELO, v).apply()

    var veiculoAno: Int
        get() = prefs.getInt(KEY_VEICULO_ANO, 0)
        set(v) = prefs.edit().putInt(KEY_VEICULO_ANO, v).apply()

    var veiculoVin: String
        get() = prefs.getString(KEY_VEICULO_VIN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_VEICULO_VIN, v).apply()

    // IA
    /**
     * Retorna a chave em ordem de prioridade:
     * 1. Chave salva pelo usuario nas configuracoes
     * 2. Chave embutida no APK (BuildConfig.GROQ_API_KEY)
     */
    var groqApiKey: String
        get() {
            val salva = prefs.getString(KEY_GROQ_KEY, "") ?: ""
            if (salva.isNotEmpty()) return salva
            return try {
                BuildConfig.GROQ_API_KEY
            } catch (e: Exception) {
                ""
            }
        }
        set(v) = prefs.edit().putString(KEY_GROQ_KEY, v).apply()

    /** Indica se ha uma chave configurada (salva ou embutida). */
    val groqApiKeyConfigurada: Boolean
        get() = groqApiKey.isNotEmpty()

    /** Indica se a chave veio do BuildConfig (embutida no APK). */
    val groqApiKeyEmbutida: Boolean
        get() = (prefs.getString(KEY_GROQ_KEY, "") ?: "").isEmpty() &&
                try { BuildConfig.GROQ_API_KEY.isNotEmpty() } catch (e: Exception) { false }

    var modoIA: String
        get() = prefs.getString(KEY_MODO_IA, "online_primeiro") ?: "online_primeiro"
        set(v) = prefs.edit().putString(KEY_MODO_IA, v).apply()

    // Auto-update
    var autoUpdateAtivo: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    var ultimaVersaoVerificada: String
        get() = prefs.getString(KEY_ULTIMA_VERSAO, "") ?: ""
        set(v) = prefs.edit().putString(KEY_ULTIMA_VERSAO, v).apply()

    companion object {
        private const val KEY_TELEFONE = "app_telefone"
        private const val KEY_MUSICA = "app_musica"
        private const val KEY_GPS = "app_gps"
        private const val KEY_USUARIO = "nome_usuario"
        private const val KEY_ASSISTENTE = "nome_assistente"
        private const val KEY_MODO_AVANCADO = "modo_avancado"
        private const val KEY_VOZ_ATIVA = "voz_ativa"
        private const val KEY_BIPE_ATIVO = "bipe_ativo"
        private const val KEY_IDIOMA = "idioma"
        private const val KEY_OBD_HOST = "obd_host"
        private const val KEY_OBD_PORTA = "obd_porta"
        private const val KEY_VEICULO_MARCA = "veiculo_marca"
        private const val KEY_VEICULO_MODELO = "veiculo_modelo"
        private const val KEY_VEICULO_ANO = "veiculo_ano"
        private const val KEY_VEICULO_VIN = "veiculo_vin"
        private const val KEY_GROQ_KEY = "groq_api_key"
        private const val KEY_MODO_IA = "modo_ia"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_ULTIMA_VERSAO = "ultima_versao"
    }
}
