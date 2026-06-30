package labs.newrapaw.dlna.probe.dlna

internal class DlnaConnectionManagerService {
    fun handleAction(actionName: String): Map<String, Any> =
        when (actionName) {
            "GetProtocolInfo" -> mapOf(
                "Source" to "",
                "Sink" to SUPPORTED_SINK_PROTOCOLS.joinToString(","),
            )
            "GetCurrentConnectionIDs" -> mapOf("ConnectionIDs" to "0")
            "GetCurrentConnectionInfo" -> mapOf(
                "RcsID" to 0,
                "AVTransportID" to 0,
                "ProtocolInfo" to "",
                "PeerConnectionManager" to "",
                "PeerConnectionID" to -1,
                "Direction" to "Input",
                "Status" to "OK",
            )
            else -> throw IllegalArgumentException("Unsupported ConnectionManager action $actionName")
        }

    private companion object {
        val SUPPORTED_SINK_PROTOCOLS = listOf(
            "http-get:*:video/mp4:*",
            "http-get:*:application/vnd.apple.mpegurl:*",
            "http-get:*:application/x-mpegURL:*",
            "http-get:*:video/mp2t:*",
            "http-get:*:audio/mpeg:*",
            "http-get:*:image/jpeg:*",
        )
    }
}
