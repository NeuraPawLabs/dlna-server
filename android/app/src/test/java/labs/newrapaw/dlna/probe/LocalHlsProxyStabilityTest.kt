package labs.newrapaw.dlna.probe

import java.net.Socket
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHlsProxyStabilityTest {
    @Test
    fun routeExceptionsReturnHttp500AndLogError() {
        val logs = mutableListOf<String>()
        val proxy = LocalHlsProxy(
            log = { logs.add(it) },
            dlnaConfig = { error("description failed") },
        )

        proxy.start()
        try {
            val response = rawHttpRequest(proxy.port, "GET /description.xml HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")

            assertTrue(response.startsWith("HTTP/1.1 500"))
            assertTrue(response.contains("Internal Server Error"))
            assertTrue(logs.any { it.contains("Request failed") && it.contains("description failed") })
        } finally {
            proxy.close()
        }
    }

    private fun rawHttpRequest(port: Int, request: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            socket.shutdownOutput()
            return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }
}
