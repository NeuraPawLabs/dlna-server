package labs.newrapaw.dlna.probe.dlna

import java.util.UUID

internal data class DlnaEventSubscription(
    val serviceName: String,
    val sid: String,
    val callbackUrl: String,
    val timeoutSeconds: Int,
    val timeoutHeaderValue: String,
    val expiresAtMs: Long,
    val sequence: Int = 0,
    val consecutiveFailureCount: Int = 0,
)

internal data class DlnaEventSubscriptionSubscribeResult(
    val response: DlnaHttpResponse,
    val initialNotifySubscription: DlnaEventSubscription?,
)

internal class DlnaEventSubscriptionStore(
    private val nowMs: () -> Long,
) {
    private val lock = Any()
    private val subscriptions = linkedMapOf<String, DlnaEventSubscription>()

    fun subscribe(
        serviceName: String,
        callbackHeader: String?,
        timeoutHeader: String?,
        sidHeader: String?,
        advanceSequence: Boolean,
    ): DlnaEventSubscriptionSubscribeResult = synchronized(lock) {
        pruneExpiredSubscriptionsLocked()
        val timeout = parseTimeout(timeoutHeader, nowMs())
        val existing = sidHeader?.let(subscriptions::get)
        if (sidHeader != null && (existing == null || existing.serviceName != serviceName)) {
            return@synchronized DlnaEventSubscriptionSubscribeResult(
                response = DlnaHttpResponse(statusCode = 412),
                initialNotifySubscription = null,
            )
        }
        val callbackUrl = parseCallbackUrl(callbackHeader)
        if (existing == null && callbackUrl == null) {
            return@synchronized DlnaEventSubscriptionSubscribeResult(
                response = DlnaHttpResponse(statusCode = 412),
                initialNotifySubscription = null,
            )
        }
        if (existing == null) {
            pruneOldestSubscriptionsLocked(MAX_ACTIVE_SUBSCRIPTIONS - 1)
        }
        val subscription = when {
            existing != null -> existing.copy(
                timeoutSeconds = timeout.timeoutSeconds,
                timeoutHeaderValue = timeout.timeoutHeaderValue,
                expiresAtMs = timeout.expiresAtMs,
            )
            else -> DlnaEventSubscription(
                serviceName = serviceName,
                sid = "uuid:${UUID.randomUUID()}",
                callbackUrl = callbackUrl!!,
                timeoutSeconds = timeout.timeoutSeconds,
                timeoutHeaderValue = timeout.timeoutHeaderValue,
                expiresAtMs = timeout.expiresAtMs,
            )
        }
        subscriptions[subscription.sid] =
            if (existing == null && advanceSequence) {
                subscription.copy(sequence = subscription.sequence + 1)
            } else {
                subscription
            }
        DlnaEventSubscriptionSubscribeResult(
            response = DlnaHttpResponse(
                statusCode = 200,
                headers = mapOf(
                    "SID" to subscription.sid,
                    "TIMEOUT" to subscription.timeoutHeaderValue,
                ),
            ),
            initialNotifySubscription = if (existing == null) subscription else null,
        )
    }

    fun unsubscribe(
        serviceName: String,
        sidHeader: String?,
    ): DlnaHttpResponse = synchronized(lock) {
        pruneExpiredSubscriptionsLocked()
        val sid = sidHeader ?: return@synchronized DlnaHttpResponse(statusCode = 412)
        val subscription = subscriptions[sid]
            ?: return@synchronized DlnaHttpResponse(statusCode = 412)
        if (subscription.serviceName != serviceName) {
            return@synchronized DlnaHttpResponse(statusCode = 412)
        }
        subscriptions.remove(sid)
        DlnaHttpResponse(statusCode = 200)
    }

    fun notificationsForService(serviceName: String): List<DlnaEventSubscription> = synchronized(lock) {
        pruneExpiredSubscriptionsLocked()
        subscriptions.values
            .filter { it.serviceName == serviceName }
            .map { subscription ->
                subscriptions[subscription.sid] = subscription.copy(sequence = subscription.sequence + 1)
                subscription
            }
    }

    fun recordDeliveryResult(
        sid: String,
        success: Boolean,
    ) = synchronized(lock) {
        val subscription = subscriptions[sid] ?: return@synchronized
        if (success) {
            subscriptions[sid] = subscription.copy(consecutiveFailureCount = 0)
            return@synchronized
        }
        val nextFailureCount = subscription.consecutiveFailureCount + 1
        if (nextFailureCount >= MAX_CONSECUTIVE_NOTIFY_FAILURES) {
            subscriptions.remove(sid)
        } else {
            subscriptions[sid] = subscription.copy(consecutiveFailureCount = nextFailureCount)
        }
    }

    private fun pruneExpiredSubscriptionsLocked() {
        val now = nowMs()
        val expiredSids = subscriptions.values
            .filter { it.expiresAtMs <= now }
            .map { it.sid }
        expiredSids.forEach(subscriptions::remove)
    }

    private fun pruneOldestSubscriptionsLocked(maxRetained: Int) {
        while (subscriptions.size > maxRetained) {
            val oldestSid = subscriptions.entries.firstOrNull()?.key ?: return
            subscriptions.remove(oldestSid)
        }
    }

    private companion object {
        const val MAX_ACTIVE_SUBSCRIPTIONS = 64
        const val MAX_CONSECUTIVE_NOTIFY_FAILURES = 3
    }
}
