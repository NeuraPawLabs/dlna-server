package labs.newrapaw.dlna.probe.dlna

internal data class DlnaHttpResponse(
    val statusCode: Int,
    val contentType: String = "text/plain; charset=utf-8",
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

internal data class DlnaEventNotifyRequest(
    val callbackUrl: String,
    val headers: Map<String, String>,
    val body: String,
)

internal class DlnaEventing(
    private val sendNotify: (DlnaEventNotifyRequest) -> Unit = {},
    private val currentAvTransportSnapshot: () -> DlnaRendererSnapshot? = { null },
    private val currentRenderingControlSnapshot: () -> DlnaRendererSnapshot? = { null },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val subscriptionStore = DlnaEventSubscriptionStore(
        nowMs = nowMs,
    )

    fun subscribe(
        serviceName: String,
        callbackHeader: String?,
        timeoutHeader: String?,
        sidHeader: String?,
    ): DlnaHttpResponse {
        val currentSnapshot = if (sidHeader == null) currentSnapshotFor(serviceName) else null
        val result = subscriptionStore.subscribe(
            serviceName = serviceName,
            callbackHeader = callbackHeader,
            timeoutHeader = timeoutHeader,
            sidHeader = sidHeader,
            advanceSequence = currentSnapshot != null,
        )
        val initialNotify = result.initialNotifySubscription
            ?.let { subscription -> currentSnapshot?.let { snapshot -> initialNotifyFor(subscription, snapshot) } }
        initialNotify?.let(::dispatchNotifySafely)
        return result.response
    }

    fun unsubscribe(
        serviceName: String,
        sidHeader: String?,
    ): DlnaHttpResponse = subscriptionStore.unsubscribe(
        serviceName = serviceName,
        sidHeader = sidHeader,
    )

    fun publishAvTransport(snapshot: DlnaRendererSnapshot) {
        publish(
            serviceName = "AVTransport",
            lastChange = buildAvTransportLastChange(snapshot),
        )
    }

    fun publishRenderingControl(snapshot: DlnaRendererSnapshot) {
        publish(
            serviceName = "RenderingControl",
            lastChange = buildRenderingControlLastChange(snapshot),
        )
    }

    fun recordNotifyDeliveryResult(
        sid: String,
        success: Boolean,
    ) = subscriptionStore.recordDeliveryResult(
        sid = sid,
        success = success,
    )

    private fun publish(serviceName: String, lastChange: String) {
        val notifications = subscriptionStore.notificationsForService(serviceName)
            .map { subscription -> notifyRequest(subscription, lastChange) }
        notifications.forEach(::dispatchNotifySafely)
    }

    private fun initialNotifyFor(
        subscription: DlnaEventSubscription,
        snapshot: DlnaRendererSnapshot,
    ): DlnaEventNotifyRequest =
        notifyRequest(
            subscription = subscription,
            lastChange = when (subscription.serviceName) {
                "AVTransport" -> buildAvTransportLastChange(snapshot)
                "RenderingControl" -> buildRenderingControlLastChange(snapshot)
                else -> ""
            },
        )

    private fun notifyRequest(
        subscription: DlnaEventSubscription,
        lastChange: String,
    ): DlnaEventNotifyRequest =
        DlnaEventNotifyRequest(
            callbackUrl = subscription.callbackUrl,
            headers = mapOf(
                "CONTENT-TYPE" to "text/xml; charset=\"utf-8\"",
                "NT" to "upnp:event",
                "NTS" to "upnp:propchange",
                "SID" to subscription.sid,
                "SEQ" to subscription.sequence.toString(),
            ),
            body = buildPropertySetXml(lastChange),
        )

    private fun currentSnapshotFor(serviceName: String): DlnaRendererSnapshot? =
        when (serviceName) {
            "AVTransport" -> currentAvTransportSnapshot()
            "RenderingControl" -> currentRenderingControlSnapshot()
            else -> null
        }

    private fun dispatchNotifySafely(request: DlnaEventNotifyRequest) {
        runCatching {
            sendNotify(request)
        }.onFailure {
            request.headers["SID"]?.let { sid ->
                recordNotifyDeliveryResult(
                    sid = sid,
                    success = false,
                )
            }
        }
    }
}
