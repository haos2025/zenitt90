package com.platinum.ott.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.random.Random

/**
 * Retry с exponential backoff + jitter — тот же принцип, что в
 * zenith-backend/app/plugins/resilience.py (with_retry), только на стороне
 * Android-клиента. В ZENITH_OTT_HANDOFF.md было написано, что этот файл
 * "качественная реализация... перенесён без изменений из Enterprise-ветки" —
 * по факту его не оказалось ни в setup_zenith_v2_combined.py, ни в
 * kinobox_pro.zip, ни где-либо ещё в присланных архивах. Написан заново.
 *
 * Ретраит:
 *  - IOException — таймаут, обрыв соединения, DNS-сбой (запрос мог вообще
 *    не дойти до сервера).
 *  - HTTP 500-599 — сервер сломался, а не клиент ошибся.
 *  - HTTP 429 — ровно то, что отдаёт RateLimiter на backend; задержка
 *    перед повтором даёт лимиту время "остыть", вместо немедленного отказа.
 * НЕ ретраит остальные 4xx (400/401/403/404) — это ошибка самого запроса,
 * повтор с теми же данными даст тот же результат, только зря нагрузит сервер.
 *
 * Ретраит только GET/HEAD. POST/PUT (например /sync/push) не идемпотентны
 * безусловно — при неопределённом исходе первого запроса (сервер мог
 * успеть применить изменение, но упасть на отправке ответа) повтор рискует
 * задвоить данные. Для эндпоинтов, где нужен retry и на POST осознанно —
 * добавляйте отдельный interceptor с явным флагом, не трогая этот дефолт.
 */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 500L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isRetryableMethod = request.method == "GET" || request.method == "HEAD"
        var lastException: IOException? = null

        for (attempt in 0 until maxAttempts) {
            if (attempt > 0) {
                // 500мс, 1000мс, 2000мс... + случайный джиттер до половины
                // интервала — не даёт множеству клиентов синхронно повторять
                // запросы одной волной ровно через одинаковые интервалы
                val backoff = baseDelayMs * (1L shl (attempt - 1))
                val jitter = Random.nextLong(0, backoff / 2 + 1)
                try {
                    Thread.sleep(backoff + jitter)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("RetryInterceptor прерван во время ожидания", e)
                }
            }

            try {
                val response = chain.proceed(request)
                val isRetryableStatus = response.code in 500..599 || response.code == 429
                val isLastAttempt = attempt == maxAttempts - 1

                if (!isRetryableStatus || !isRetryableMethod || isLastAttempt) {
                    return response
                }
                response.close() // не течём — тело неиспользуемого промежуточного ответа
                lastException = null
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxAttempts - 1 || !isRetryableMethod) throw e
            }
        }

        throw lastException ?: IOException("RetryInterceptor: исчерпаны попытки без ответа")
    }
}
