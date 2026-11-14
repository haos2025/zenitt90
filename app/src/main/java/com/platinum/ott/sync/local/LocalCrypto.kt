package com.platinum.ott.sync.local

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Ошибка шифрования/проверки (подделка, неверный ключ, повреждённые данные). */
class LocalCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Примитивы локального протокола v2. Только стандартная Java-криптография (работает на Android с minSdk 26):
 *  - обмен ключами ECDH на кривой secp256r1;
 *  - HKDF-SHA256 (реализован здесь: в JCA на API 26 его нет);
 *  - AES-256-GCM для шифрования тел сообщений;
 *  - HMAC-SHA256 для подтверждения знания кода.
 */
object LocalCrypto {
    private val rnd = SecureRandom()
    private const val CURVE = "secp256r1"

    class EcKeyPair(val private: PrivateKey, val publicEncoded: ByteArray)

    // Параметры кривой берём у собственной ключевой пары: чужой открытый ключ принимается только на ЭТОЙ кривой
    // (иначе можно подсунуть ключ на слабой кривой).
    private val curveParams: ECParameterSpec by lazy {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec(CURVE), rnd)
        (g.generateKeyPair().public as ECPublicKey).params
    }

    fun newKeyPair(): EcKeyPair {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec(CURVE), rnd)
        val kp = g.generateKeyPair()
        return EcKeyPair(kp.private, kp.public.encoded)
    }

    /** Общий секрет ECDH. Бросает LocalCryptoException, если ключ собеседника некорректен или на другой кривой. */
    fun agree(own: PrivateKey, peerPublicEncoded: ByteArray): ByteArray {
        try {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicEncoded)) as ECPublicKey
            val p = pub.params
            val ok = p.order == curveParams.order && p.cofactor == curveParams.cofactor &&
                p.curve == curveParams.curve && p.generator == curveParams.generator
            if (!ok) throw LocalCryptoException("Открытый ключ на другой кривой")
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(own)
            ka.doPhase(pub, true)
            return ka.generateSecret()
        } catch (e: LocalCryptoException) {
            throw e
        } catch (e: Exception) {
            throw LocalCryptoException("Некорректный открытый ключ", e)
        }
    }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(if (key.isEmpty()) ByteArray(32) else key, "HmacSHA256"))
        return m.doFinal(data)
    }

    /** HKDF (RFC 5869) на HMAC-SHA256. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32))
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            t = hmac(prk, t + info + byteArrayOf(counter.toByte()))
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = 32): ByteArray =
        hkdf(ikm, salt, info.toByteArray(Charsets.UTF_8), length)

    /** AES-256-GCM. Результат: 12 байт IV + шифртекст с тегом. aad аутентифицируется, но не шифруется. */
    fun encrypt(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(aad)
        return iv + c.doFinal(plain)
    }

    fun decrypt(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < 12 + 16) throw LocalCryptoException("Сообщение повреждено")
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
            c.updateAAD(aad)
            return c.doFinal(sealed, 12, sealed.size - 12)
        } catch (e: Exception) {
            throw LocalCryptoException("Не удалось расшифровать (неверный ключ или данные изменены)", e)
        }
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    /**
     * Контрольная строка из 4 цифр: обе стороны считают её из общего секрета и обоих открытых ключей.
     * Если кто-то вклинился в обмен («человек посередине»), у двух устройств получатся РАЗНЫЕ строки.
     */
    fun sas(shared: ByteArray, pubA: ByteArray, pubB: ByteArray): String {
        val salt = sha256(pubA + pubB)
        val b = hkdf(shared, salt, "zenith-local-v2|sas", 4)
        val n = BigInteger(1, b).mod(BigInteger.valueOf(10_000)).toInt()
        return n.toString().padStart(4, '0')
    }

    fun b64(data: ByteArray): String = java.util.Base64.getEncoder().encodeToString(data)

    fun unb64(s: String): ByteArray = try {
        java.util.Base64.getDecoder().decode(s)
    } catch (e: IllegalArgumentException) {
        throw LocalCryptoException("Некорректная кодировка данных", e)
    }

    fun hex(data: ByteArray): String = data.joinToString("") { "%02x".format(it) }
}
