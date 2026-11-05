package com.dmwnezes.sintonia.translate

import com.dmwnezes.sintonia.data.Track
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** O Gemini não entregou a tradução; [message] explica o motivo. Com [fatal], não adianta tentar outro modelo. */
class GeminiFailure(message: String, val fatal: Boolean = false) : Exception(message)

/**
 * Traduz a letra inteira de uma vez, com título e artista, para a IA entender a música
 * antes de traduzir cada linha. A chave é do próprio usuário (plano grátis do Google AI Studio).
 */
class GeminiTranslator(http: OkHttpClient) {
    private val client = http.newBuilder()
        .readTimeout(TOTAL_MS, TimeUnit.MILLISECONDS)
        .callTimeout(TOTAL_MS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun translate(key: String, track: Track, code: String, lines: List<String>): List<String?> {
        val body = buildRequest(track, code, lines).toString().toRequestBody("application/json".toMediaType())
        return withTimeoutOrNull(TOTAL_MS) { race(MODELS, HEDGE_MS) { ask(key, it, body, lines) } }
            ?: throw GeminiFailure("O Gemini demorou demais.")
    }

    private suspend fun ask(key: String, model: String, body: RequestBody, lines: List<String>): List<String?> {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body)
            .build()
        val (status, text) = try {
            client.newCall(request).await()
        } catch (e: UnknownHostException) {
            throw GeminiFailure("Sem conexão com o Gemini.", fatal = true)
        } catch (e: ConnectException) {
            throw GeminiFailure("Sem conexão com o Gemini.", fatal = true)
        } catch (e: IOException) {
            throw GeminiFailure("O Gemini não respondeu.")
        }
        if (status == 401 || status == 403 || (status == 400 && "API_KEY_INVALID" in text)) {
            throw GeminiFailure("A chave do Gemini foi recusada. Confira em Ajustes.", fatal = true)
        }
        if (status == 429) throw GeminiFailure("O limite grátis do Gemini foi atingido.")
        if (status != 200) throw GeminiFailure("O Gemini não respondeu (erro $status).")
        return try {
            parseResponse(text, lines)
        } catch (e: JSONException) {
            throw GeminiFailure("O Gemini não devolveu a tradução desta música.")
        }
    }

    // Com enqueue, cancelar fecha a conexão na hora; uma chamada bloqueante prenderia o resultado do modelo vencedor.
    private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) {
                try {
                    cont.resume(response.use { it.code to it.body?.string().orEmpty() })
                } catch (e: IOException) {
                    cont.resumeWithException(e)
                }
            }
        })
    }

    companion object {
        /** Todos com uso grátis em nov/2026. O Flash-Lite vem antes do 3.6 por ser o mais rápido. */
        val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.6-flash")
        private const val HEDGE_MS = 12_000L
        private const val TOTAL_MS = 45_000L

        /**
         * Às vezes o Gemini trava sem responder. Se um modelo falhar, ou passar [hedgeMs] sem responder,
         * chama o próximo sem desistir dos anteriores: vale a primeira tradução que chegar.
         */
        suspend fun race(models: List<String>, hedgeMs: Long, ask: suspend (String) -> List<String?>): List<String?> = coroutineScope {
            // null = passou o tempo de espera e é hora de chamar o próximo modelo.
            val events = Channel<Result<List<String?>>?>(Channel.UNLIMITED)
            var started = 0
            var finished = 0
            fun startNext() {
                val model = models[started++]
                launch {
                    events.send(try { Result.success(ask(model)) } catch (e: GeminiFailure) { Result.failure(e) })
                }
            }
            launch { while (true) { delay(hedgeMs); events.send(null) } }
            startNext()
            var winner: List<String?>? = null
            while (winner == null) {
                val event = events.receive()
                if (event == null) {
                    if (started < models.size) startNext()
                    continue
                }
                finished++
                winner = event.getOrNull()
                if (winner != null) break
                val failure = event.exceptionOrNull() as GeminiFailure
                if (failure.fatal) throw failure
                if (started < models.size) startNext() else if (finished == started) throw failure
            }
            coroutineContext.cancelChildren()
            winner!!
        }

        private val SYSTEM = """
            Você é tradutor literário de letras de música para o português do Brasil. A tradução precisa soar como algo que um brasileiro diria e preservar o sentido, a emoção e a intenção da música.

            Antes de traduzir, escreva em "analise" (até 5 frases): do que a música fala de verdade, quem narra e para quem, o tom (irônico, sensual, de luto, de deboche...), o registro (gíria, formal, poético) e as expressões, gírias, duplos sentidos e referências culturais que não podem ir ao pé da letra. Use o que souber sobre a música e o artista, sem inventar fatos. Se não reconhecer a música, baseie-se só na letra.

            Depois traduza em "linhas", seguindo essa análise:
            - Sentido acima da palavra: expressões e gírias viram o equivalente brasileiro. Ex.: "over the moon" é "nas nuvens", nunca "sobre a lua"; "I'm down" pode ser "tô dentro" ou "tô mal", conforme o contexto.
            - Cada linha depende da música inteira: decida o significado de palavras ambíguas pelo contexto. Mantenha o duplo sentido quando der.
            - Metáforas: mantenha a imagem quando ela funciona em português; se não funcionar, use outra de mesmo efeito.
            - Registro: gíria vira gíria, palavrão vira palavrão, poético continua poético. Não suavize, não censure, não formalize.
            - Coerência: a mesma voz do começo ao fim, o mesmo gênero para cada pessoa, o mesmo pronome para quem é tratado (prefira "você") e o refrão igual nas repetições, salvo se o contexto mudar.
            - Português falado do Brasil ("tô", "pra", "a gente") quando combinar com o tom. Nada de frase de dicionário nem construção de Portugal.
            - Mantenha nomes próprios, marcas, lugares, interjeições e vocalizações ("oh", "yeah", "la la"). Trechos já em português ficam iguais.
            - Rima e métrica são bônus, nunca à custa do sentido.

            Formato: cada "n" corresponde a uma linha recebida. Uma tradução para cada "n", sem pular nenhum e sem criar outros. Não junte nem divida linhas: se a frase continua na linha seguinte, traduza pensando na frase inteira e reparta o texto para cada parte cair na sua linha. Em "t" vai só a tradução, sem notas nem explicações.
        """.trimIndent()

        fun buildRequest(track: Track, code: String, lines: List<String>): JSONObject {
            val lyrics = lines.mapIndexedNotNull { i, l -> l.trim().takeIf { it.isNotEmpty() }?.let { "${i + 1}| $it" } }
                .joinToString("\n")
            val prompt = "Música: ${track.name}\nArtista: ${track.artistLine}\nÁlbum: ${track.album}\n" +
                "Idioma original: $code\n\nLetra:\n$lyrics"
            // Com o filtro padrão, músicas sobre sexo ou violência voltariam sem tradução.
            val safety = listOf("HARASSMENT", "HATE_SPEECH", "SEXUALLY_EXPLICIT", "DANGEROUS_CONTENT").map {
                JSONObject().put("category", "HARM_CATEGORY_$it").put("threshold", "BLOCK_NONE")
            }
            return JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("safetySettings", JSONArray(safety))
                .put("generationConfig", JSONObject()
                    .put("temperature", 0.5)
                    // A análise da música já é pedida na resposta; pensar mais só deixa a tradução mais lenta.
                    .put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema()))
        }

        // "analise" vem primeiro de propósito: a IA pensa na música antes de traduzir a primeira linha.
        private fun schema(): JSONObject {
            val item = JSONObject().put("type", "OBJECT")
                .put("properties", JSONObject().put("n", JSONObject().put("type", "INTEGER")).put("t", JSONObject().put("type", "STRING")))
                .put("required", JSONArray(listOf("n", "t")))
                .put("propertyOrdering", JSONArray(listOf("n", "t")))
            return JSONObject().put("type", "OBJECT")
                .put("properties", JSONObject()
                    .put("analise", JSONObject().put("type", "STRING"))
                    .put("linhas", JSONObject().put("type", "ARRAY").put("items", item)))
                .put("required", JSONArray(listOf("analise", "linhas")))
                .put("propertyOrdering", JSONArray(listOf("analise", "linhas")))
        }

        /** Uma tradução por linha; linhas vazias ou iguais ao original ficam null. Resposta incompleta gera JSONException. */
        fun parseResponse(body: String, lines: List<String>): List<String?> {
            val parts = JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val text = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
            val items = JSONObject(text).getJSONArray("linhas")
            val byLine = (0 until items.length()).associate {
                val item = items.getJSONObject(it)
                item.getInt("n") to item.getString("t").trim()
            }
            return lines.mapIndexed { i, line ->
                val original = line.trim()
                if (original.isEmpty()) return@mapIndexed null
                val t = byLine[i + 1]?.takeIf { it.isNotEmpty() } ?: throw JSONException("faltou a linha ${i + 1}")
                t.takeIf { !it.equals(original, ignoreCase = true) }
            }
        }
    }
}

/** Guarda a tradução da IA no celular: cada música gasta só um pedido do limite grátis. */
class TranslationStore(private val dir: File) {
    private fun file(trackId: String) = File(dir, trackId.replace(Regex("[^A-Za-z0-9]"), "_") + ".json")

    /** Null se não houver tradução salva ou se a letra tiver mudado desde então. */
    fun get(trackId: String, lines: List<String>): List<String?>? = try {
        val json = JSONObject(file(trackId).readText())
        val saved = json.getJSONArray("lines")
        if (json.getInt("hash") != lines.hashCode() || saved.length() != lines.size) null
        else List(lines.size) { i -> if (saved.isNull(i)) null else saved.getString(i) }
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    }

    fun put(trackId: String, lines: List<String>, translated: List<String?>) {
        val saved = JSONArray()
        translated.forEach { saved.put(it ?: JSONObject.NULL) }
        try {
            dir.mkdirs()
            file(trackId).writeText(JSONObject().put("hash", lines.hashCode()).put("lines", saved).toString())
        } catch (e: IOException) {
            // Sem salvar, a música só será traduzida de novo na próxima vez.
        }
    }
}
