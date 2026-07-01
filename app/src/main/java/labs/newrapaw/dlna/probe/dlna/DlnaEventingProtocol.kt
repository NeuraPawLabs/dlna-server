package labs.newrapaw.dlna.probe.dlna

internal data class DlnaSubscriptionTimeout(
    val timeoutSeconds: Int,
    val timeoutHeaderValue: String,
    val expiresAtMs: Long,
)

internal fun parseCallbackUrl(callbackHeader: String?): String? =
    callbackHeader
        ?.trim()
        ?.removePrefix("<")
        ?.removeSuffix(">")
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

internal fun parseTimeout(
    timeoutHeader: String?,
    nowMs: Long,
): DlnaSubscriptionTimeout {
    val normalized = timeoutHeader?.trim().orEmpty()
    if (normalized.equals("Second-infinite", ignoreCase = true)) {
        return DlnaSubscriptionTimeout(
            timeoutSeconds = Int.MAX_VALUE,
            timeoutHeaderValue = "Second-infinite",
            expiresAtMs = Long.MAX_VALUE,
        )
    }

    val seconds = Regex("""(?i)^second-(\d+)$""")
        .matchEntire(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.coerceAtLeast(1)
        ?: 1_800
    return DlnaSubscriptionTimeout(
        timeoutSeconds = seconds,
        timeoutHeaderValue = "Second-$seconds",
        expiresAtMs = safeAddMillis(nowMs, seconds * 1_000L),
    )
}

internal fun safeAddMillis(baseMs: Long, deltaMs: Long): Long {
    val result = baseMs + deltaMs
    return if (result < baseMs) Long.MAX_VALUE else result
}

internal fun buildPropertySetXml(lastChange: String): String =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
      <e:property>
        <LastChange>${escapeXml(lastChange)}</LastChange>
      </e:property>
    </e:propertyset>
    """.trimIndent()

internal fun buildAvTransportLastChange(snapshot: DlnaRendererSnapshot): String =
    """
    <Event xmlns="urn:schemas-upnp-org:metadata-1-0/AVT/">
      <InstanceID val="0">
        <TransportState val="${escapeXml(snapshot.transportState)}"/>
        <TransportStatus val="${escapeXml(snapshot.transportStatus)}"/>
        <CurrentTrackURI val="${escapeXml(snapshot.currentUri)}"/>
        <RelativeTimePosition val="${escapeXml(snapshot.relativeTimePosition)}"/>
      </InstanceID>
    </Event>
    """.trimIndent()

internal fun buildRenderingControlLastChange(snapshot: DlnaRendererSnapshot): String =
    """
    <Event xmlns="urn:schemas-upnp-org:metadata-1-0/RCS/">
      <InstanceID val="0">
        <Volume channel="Master" val="${snapshot.volume}"/>
        <Mute channel="Master" val="${if (snapshot.muted) 1 else 0}"/>
      </InstanceID>
    </Event>
    """.trimIndent()
