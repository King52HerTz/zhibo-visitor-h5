package cn.xiangyu.museum

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(context: Context) : TokenVault {
    private val preferences = context.getSharedPreferences("museum_login", Context.MODE_PRIVATE)
    private val alias = "museum_login_aes"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    override suspend fun save(session: LoginSession) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = JSONObject().put("username", session.username).put("token", session.token)
            .put("endpoint", session.endpoint).toString().toByteArray(Charsets.UTF_8)
        val encrypted = Base64.encodeToString(cipher.iv + cipher.doFinal(payload), Base64.NO_WRAP)
        check(preferences.edit().putString("sealed", encrypted).commit())
    }

    override suspend fun load(): LoginSession? = withContext(Dispatchers.IO) {
        val encrypted = preferences.getString("sealed", null) ?: return@withContext null
        try {
            val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            val payload = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            LoginSession(payload.getString("username"), payload.getString("token"), payload.getString("endpoint"))
        } catch (_: Exception) {
            preferences.edit().clear().commit()
            null
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        check(preferences.edit().clear().commit())
    }
}
