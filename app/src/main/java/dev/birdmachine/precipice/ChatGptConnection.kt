package dev.birdmachine.precipice

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Tokens stay in Android Keystore-encrypted app-private storage, never in logs. */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("precipice_credentials", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey("precipice-credentials-v1", null)
        if (existing != null) return existing as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("precipice-credentials-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): JSONObject {
        val saved = prefs.getString("record", null) ?: return JSONObject()
        val bytes = Base64.decode(saved, Base64.NO_WRAP)
        require(bytes.size > 12) { "Saved connection is damaged. Reconnect in settings." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    @Synchronized fun write(record: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(record.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("record", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
}

internal object Web {
    fun request(url: String, body: String? = null, token: String? = null,
                contentType: String = "application/json", stream: ((java.io.BufferedReader) -> String)? = null): String {
        require(URL(url).protocol == "https") { "The connection must use HTTPS." }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 25_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Accept", if (stream != null) "text/event-stream" else "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                // Do not include bodies: token endpoints can contain sensitive fields.
                throw IllegalStateException(when (code) {
                    401 -> "Connection expired. Reconnect with ChatGPT in settings."
                    403 -> "ChatGPT plan access was not granted for this request."
                    429 -> "ChatGPT usage limit reached. Open Manage usage in settings."
                    else -> "Connection returned HTTP $code. Try again or reconnect."
                })
            }
            return connection.inputStream.bufferedReader().use { if (stream != null) stream(it) else it.readText() }
        } finally { connection.disconnect() }
    }
    fun form(values: Map<String, String>) = values.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }
}

data class AvailableModel(val slug: String, val label: String)
data class ChatTurn(val role: String, val text: String)

/** Official local-project sign-in contract, not ChatGPT website scraping. */
class ChatGptConnection(private val store: CredentialStore) {
    private val authorizationEpoch = AtomicInteger()
    private val credentialLock = Any()
    @Volatile private var listener: ServerSocket? = null
    private val issuer = "https://auth.openai.com"
    private val tokenEndpoint = "$issuer/api/accounts/oauth/token"
    private val resource = "https://api.openai.com/v1"
    private val random = SecureRandom()
    private fun randomValue() = ByteArray(32).also(random::nextBytes).let(::base64url)
    private fun base64url(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun decode(value: String) = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun accountLabel(): String = store.read().optString("email", "")
    fun connected(): Boolean = store.read().optString("access_token").isNotBlank()
    fun cancelSignIn() {
        synchronized(credentialLock) { authorizationEpoch.incrementAndGet() }
        listener?.close(); listener = null
    }
    private fun saveIfCurrent(epoch: Int, record: JSONObject) {
        synchronized(credentialLock) {
            check(authorizationEpoch.get() == epoch) { "Connection attempt cancelled." }
            store.write(record)
        }
    }

    /** Run on an IO thread. openBrowser must dispatch to the Android main thread. */
    fun signIn(openBrowser: (String) -> Unit) {
        val epoch = authorizationEpoch.get()
        val old = store.read()
        val host = old.optString("host_id").ifBlank { "urn:uuid:" + java.util.UUID.randomUUID() }
        old.put("host_id", host); saveIfCurrent(epoch, old)
        val priorClient = old.optString("client_id")
        val state = randomValue(); val nonce = randomValue(); val verifier = randomValue()
        val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        listener = socket
        socket.soTimeout = 180_000
        val redirect = "http://127.0.0.1:${socket.localPort}/auth/callback"
        val fields = linkedMapOf(
            "client_id" to priorClient.ifBlank { "dynamic_agent_client" },
            "ext_agent_host_id" to host, "response_type" to "code", "redirect_uri" to redirect,
            "scope" to "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",
            "resource" to resource, "state" to state, "nonce" to nonce,
            "code_challenge_method" to "S256",
            "code_challenge" to base64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))))
        if (priorClient.isBlank()) fields["agent_name_hint"] = "Precipice"
        // Keep authorization URLs out of diagnostics: they include single-use state.
        openBrowser("$issuer/api/accounts/authorize?" + Web.form(fields))
        try {
            val deadline = System.currentTimeMillis() + 180_000
            while (System.currentTimeMillis() < deadline) {
                socket.soTimeout = (deadline - System.currentTimeMillis()).coerceAtLeast(1).toInt()
                socket.accept().use { client ->
                    client.soTimeout = 3000
                    val line = client.getInputStream().bufferedReader().readLine() ?: ""
                    val parts = line.split(' ')
                    val uri = Uri.parse("http://127.0.0.1" + parts.getOrElse(1) { "/" })
                    val valid = parts.firstOrNull() == "GET" && uri.path == "/auth/callback" &&
                        MessageDigest.isEqual((uri.getQueryParameter("state") ?: "").toByteArray(), state.toByteArray())
                    val page = if (valid) "Return to Precipice. Completing your connection…" else "Invalid callback. Return to Precipice and retry."
                    val bytes = page.toByteArray(Charsets.UTF_8)
                    client.getOutputStream().apply {
                        write("HTTP/1.1 ${if (valid) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                    if (!valid) return@use
                    require(uri.getQueryParameter("error") == null) { "ChatGPT sign-in was declined. Your existing connection is unchanged." }
                    val clientId = uri.getQueryParameter("client_id") ?: priorClient
                    require(clientId.isNotBlank() && clientId != "dynamic_agent_client") { "Sign-in did not return a client registration." }
                    require(priorClient.isBlank() || clientId == priorClient) { "Sign-in returned a different client registration." }
                    // Save registration even if code exchange fails, for safe reauthorization.
                    old.put("client_id", clientId); saveIfCurrent(epoch, old)
                    val code = uri.getQueryParameter("code") ?: error("Sign-in did not return a code.")
                    val token = JSONObject(Web.request(tokenEndpoint, Web.form(mapOf(
                        "grant_type" to "authorization_code", "client_id" to clientId, "code" to code,
                        "code_verifier" to verifier, "redirect_uri" to redirect, "resource" to resource)),
                        contentType = "application/x-www-form-urlencoded"))
                    val identity = validateIdentity(token.getString("id_token"), clientId, nonce)
                    val subject = identity.getString("sub")
                    require(old.optString("subject").isBlank() || old.getString("subject") == subject) { "This registration belongs to a different account." }
                    require(token.getString("scope").split(' ').contains("chatgpt.tokens.use.direct")) { "Sign in again and enable ChatGPT plan usage." }
                    saveTokens(old, token)
                    old.put("email", identity.optString("email", "Connected ChatGPT account"))
                    old.put("subject", subject); old.put("client_id", clientId); saveIfCurrent(epoch, old)
                    return
                }
            }
            error("ChatGPT sign-in timed out. Tap Continue with ChatGPT to retry.")
        } finally { socket.close(); if (listener === socket) listener = null }
    }

    private fun validateIdentity(jwt: String, clientId: String, nonce: String?): JSONObject {
        val pieces = jwt.split('.')
        require(pieces.size == 3) { "Invalid sign-in identity." }
        val header = JSONObject(String(decode(pieces[0]), Charsets.UTF_8))
        val claims = JSONObject(String(decode(pieces[1]), Charsets.UTF_8))
        require(header.getString("alg") == "RS256") { "Unsupported identity signature. Connection not saved." }
        val discovery = JSONObject(Web.request("$issuer/.well-known/openid-configuration"))
        require(discovery.getString("issuer") == issuer)
        val jwksUrl = discovery.getString("jwks_uri")
        require(URL(jwksUrl).protocol == "https" && URL(jwksUrl).host == "auth.openai.com")
        val keys = JSONObject(Web.request(jwksUrl)).getJSONArray("keys")
        val jwk = (0 until keys.length()).map { keys.getJSONObject(it) }.firstOrNull {
            it.optString("kid") == header.getString("kid") && it.optString("kty") == "RSA" &&
                it.optString("use", "sig") == "sig" && it.optString("alg", "RS256") == "RS256"
        } ?: error("Identity signing certificate unavailable. Retry sign-in.")
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(
            BigInteger(1, decode(jwk.getString("n"))), BigInteger(1, decode(jwk.getString("e")))))
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(publicKey); update((pieces[0] + "." + pieces[1]).toByteArray(Charsets.US_ASCII))
        }
        require(verifier.verify(decode(pieces[2]))) { "Identity signature invalid." }
        require(claims.getString("iss") == issuer) { "Unexpected identity issuer." }
        val audience = claims.get("aud")
        val audiences = if (audience is JSONArray) (0 until audience.length()).map { audience.getString(it) } else listOf(audience.toString())
        require(clientId in audiences && (audiences.size == 1 || claims.optString("azp") == clientId)) { "Unexpected identity audience." }
        val now = System.currentTimeMillis() / 1000
        require(claims.getLong("exp") > now - 30 && claims.optLong("nbf", 0) <= now + 30 && claims.optLong("iat", now) <= now + 30)
        if (nonce != null) require(MessageDigest.isEqual(claims.getString("nonce").toByteArray(), nonce.toByteArray())) { "Identity nonce mismatch." }
        require(claims.getString("sub").isNotBlank())
        return claims
    }

    private fun saveTokens(record: JSONObject, token: JSONObject) {
        require(token.optString("token_type", "Bearer").equals("Bearer", true))
        record.put("access_token", token.getString("access_token"))
        if (token.has("refresh_token")) record.put("refresh_token", token.getString("refresh_token"))
        if (token.has("id_token")) record.put("id_token", token.getString("id_token"))
        if (token.has("scope")) record.put("scope", token.getString("scope"))
        record.put("expires_at", System.currentTimeMillis() / 1000 + token.getLong("expires_in"))
    }

    @Synchronized private fun accessToken(): String {
        val epoch = authorizationEpoch.get()
        val record = store.read()
        require(record.optString("scope").split(' ').contains("chatgpt.tokens.use.direct")) { "Connect your ChatGPT plan in settings first." }
        if (record.optLong("expires_at") <= System.currentTimeMillis() / 1000 + 30) {
            require(record.optString("refresh_token").isNotBlank()) { "Reconnect with ChatGPT in settings." }
            val token = JSONObject(Web.request(tokenEndpoint, Web.form(mapOf(
                "grant_type" to "refresh_token", "client_id" to record.getString("client_id"),
                "refresh_token" to record.getString("refresh_token"), "resource" to resource)), contentType = "application/x-www-form-urlencoded"))
            if (token.has("id_token")) require(validateIdentity(token.getString("id_token"), record.getString("client_id"), null).getString("sub") == record.getString("subject"))
            saveTokens(record, token); saveIfCurrent(epoch, record)
        }
        return record.getString("access_token")
    }

    fun models(): List<AvailableModel> {
        val result = JSONObject(Web.request("$resource/models", token = accessToken())).getJSONArray("models")
        return (0 until result.length()).map { result.getJSONObject(it) }.filter { it.optString("visibility") == "list" }
            .map { AvailableModel(it.getString("slug"), it.getString("display_name")) }
    }

    fun respond(model: String, turns: List<ChatTurn>): String {
        val input = JSONArray().apply { turns.forEach { put(JSONObject().put("role", it.role).put("content", it.text)) } }
        val payload = JSONObject().put("model", model).put("input", input).put("store", false).put("stream", true)
            .put("instructions", "You are Birdie's warm, playful, nerdy assistant speaking through Precipice, an Android voice shell. Brill is a separate future assistant; do not claim to be Brill. Speak naturally and keep spoken answers fairly concise. Never claim access to prior ChatGPT chats, memories or connected apps unless their contents or tools are explicitly supplied.")
        return Web.request("$resource/responses", payload.toString(), accessToken(), stream = ReplyStream::read)
    }

    fun disconnect(): Boolean {
        cancelSignIn()
        val record = synchronized(credentialLock) {
            val saved = store.read()
            val cleared = JSONObject(saved.toString())
            listOf("access_token", "refresh_token", "id_token", "scope", "expires_at").forEach(cleared::remove)
            store.write(cleared)
            saved
        }
        var revoked = true
        if (record.optString("refresh_token").isNotBlank()) {
            revoked = runCatching {
                val discovery = JSONObject(Web.request("$issuer/.well-known/openid-configuration"))
                val endpoint = discovery.getString("revocation_endpoint")
                require(URL(endpoint).host == "auth.openai.com")
                Web.request(endpoint, Web.form(mapOf("token" to record.getString("refresh_token"),
                    "token_type_hint" to "refresh_token", "client_id" to record.getString("client_id"))), contentType = "application/x-www-form-urlencoded")
            }.isSuccess
        }
        return revoked
    }
}
