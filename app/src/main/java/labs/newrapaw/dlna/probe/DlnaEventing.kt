package labs.newrapaw.dlna.probe

import java.util.UUID

data class DlnaHttpResponse(
    val statusCode: Int,
    val contentType: String = "text/plain; charset=utf-8",
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

fun buildEventSubscribeResponse(): DlnaHttpResponse = DlnaHttpResponse(
    statusCode = 200,
    headers = mapOf(
        "SID" to "uuid:${UUID.randomUUID()}",
        "TIMEOUT" to "Second-1800",
    ),
)

fun buildEventUnsubscribeResponse(): DlnaHttpResponse = DlnaHttpResponse(statusCode = 200)
