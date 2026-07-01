package labs.newrapaw.dlna.probe.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaEventingTest {
    @Test
    fun subscribeCreatesEventSubscriptionHeaders() {
        val eventing = DlnaEventing()
        val response = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = "Second-600",
            sidHeader = null,
        )

        assertEquals(200, response.statusCode)
        assertTrue(response.headers["SID"].orEmpty().startsWith("uuid:"))
        assertEquals("Second-600", response.headers["TIMEOUT"])
    }

    @Test
    fun subscribeAcceptsLowercaseTimeoutValue() {
        val eventing = DlnaEventing()
        val response = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = "second-600",
            sidHeader = null,
        )

        assertEquals(200, response.statusCode)
        assertEquals("Second-600", response.headers["TIMEOUT"])
    }

    @Test
    fun subscribePreservesInfiniteTimeoutValue() {
        val eventing = DlnaEventing()
        val response = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = "Second-infinite",
            sidHeader = null,
        )

        assertEquals(200, response.statusCode)
        assertEquals("Second-infinite", response.headers["TIMEOUT"])
    }

    @Test
    fun subscribeWithoutCallbackReturnsPreconditionFailed() {
        val eventing = DlnaEventing()

        val response = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = null,
            timeoutHeader = "Second-600",
            sidHeader = null,
        )

        assertEquals(412, response.statusCode)
        assertTrue(response.headers["SID"].isNullOrEmpty())
    }

    @Test
    fun renewingSubscriptionOnWrongServiceReturnsPreconditionFailed() {
        val eventing = DlnaEventing()
        val subscribe = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = "Second-600",
            sidHeader = null,
        )

        val response = eventing.subscribe(
            serviceName = "RenderingControl",
            callbackHeader = null,
            timeoutHeader = "Second-600",
            sidHeader = subscribe.headers["SID"],
        )

        assertEquals(412, response.statusCode)
    }

    @Test
    fun unsubscribeReturnsOkWithoutBody() {
        val eventing = DlnaEventing()
        val subscribe = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = null,
            sidHeader = null,
        )
        val response = eventing.unsubscribe(
            serviceName = "AVTransport",
            sidHeader = subscribe.headers["SID"],
        )

        assertEquals(200, response.statusCode)
        assertTrue(response.body.isEmpty())
    }

    @Test
    fun unsubscribeWithoutSidReturnsPreconditionFailed() {
        val eventing = DlnaEventing()

        val response = eventing.unsubscribe(
            serviceName = "AVTransport",
            sidHeader = null,
        )

        assertEquals(412, response.statusCode)
    }

    @Test
    fun stateChangePublishesNotifyToSubscribedCallback() {
        val sent = mutableListOf<DlnaEventNotifyRequest>()
        val eventing = DlnaEventing(sendNotify = sent::add)
        val subscribe = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = "Second-300",
            sidHeader = null,
        )

        eventing.publishAvTransport(
            DlnaRendererSnapshot(
                currentUri = "https://example.com/video.m3u8",
                currentUriMetadata = "",
                transportState = "PLAYING",
                transportStatus = "OK",
                relativeTimePosition = "00:00:12",
                volume = 50,
                muted = false,
            ),
        )

        assertEquals(1, sent.size)
        assertEquals("http://127.0.0.1:1400/callback", sent.single().callbackUrl)
        assertEquals(subscribe.headers["SID"], sent.single().headers["SID"])
        assertEquals("upnp:event", sent.single().headers["NT"])
        assertEquals("upnp:propchange", sent.single().headers["NTS"])
        assertTrue(sent.single().body.contains("LastChange"))
        assertTrue(sent.single().body.contains("TransportState"))
        assertTrue(sent.single().body.contains("PLAYING"))
    }

    @Test
    fun subscribeImmediatelyPublishesCurrentSnapshotForNewSubscriber() {
        val sent = mutableListOf<DlnaEventNotifyRequest>()
        val snapshot = DlnaRendererSnapshot(
            currentUri = "https://example.com/video.m3u8",
            currentUriMetadata = "",
            transportState = "PAUSED_PLAYBACK",
            transportStatus = "OK",
            relativeTimePosition = "00:00:42",
            volume = 33,
            muted = true,
        )
        val eventing = DlnaEventing(
            sendNotify = sent::add,
            currentAvTransportSnapshot = { snapshot },
        )

        val subscribe = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = "Second-300",
            sidHeader = null,
        )

        assertEquals(1, sent.size)
        assertEquals(subscribe.headers["SID"], sent.single().headers["SID"])
        assertEquals("0", sent.single().headers["SEQ"])
        assertTrue(sent.single().body.contains("PAUSED_PLAYBACK"))
        assertTrue(sent.single().body.contains("00:00:42"))
    }

    @Test
    fun unsubscribeStopsFutureNotifications() {
        val sent = mutableListOf<DlnaEventNotifyRequest>()
        val eventing = DlnaEventing(sendNotify = sent::add)
        val subscribe = eventing.subscribe(
            serviceName = "RenderingControl",
            callbackHeader = "<http://127.0.0.1:1400/rendering>",
            timeoutHeader = null,
            sidHeader = null,
        )
        eventing.unsubscribe(
            serviceName = "RenderingControl",
            sidHeader = subscribe.headers["SID"],
        )

        eventing.publishRenderingControl(
            DlnaRendererSnapshot(
                currentUri = "",
                currentUriMetadata = "",
                transportState = "STOPPED",
                transportStatus = "OK",
                relativeTimePosition = "00:00:00",
                volume = 15,
                muted = true,
            ),
        )

        assertFalse(sent.isNotEmpty())
    }

    @Test
    fun expiredSubscriptionsDoNotReceiveFutureNotifications() {
        val sent = mutableListOf<DlnaEventNotifyRequest>()
        var nowMs = 10_000L
        val eventing = DlnaEventing(
            sendNotify = sent::add,
            nowMs = { nowMs },
        )
        eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = "Second-1",
            sidHeader = null,
        )
        sent.clear()

        nowMs += 1_500L
        eventing.publishAvTransport(
            DlnaRendererSnapshot(
                currentUri = "https://example.com/video.m3u8",
                currentUriMetadata = "",
                transportState = "PLAYING",
                transportStatus = "OK",
                relativeTimePosition = "00:00:12",
                volume = 50,
                muted = false,
            ),
        )

        assertTrue(sent.isEmpty())
    }

    @Test
    fun repeatedNotifyFailuresPruneSubscription() {
        val sent = mutableListOf<DlnaEventNotifyRequest>()
        val eventing = DlnaEventing(sendNotify = sent::add)
        val subscribe = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/callback>",
            timeoutHeader = "Second-300",
            sidHeader = null,
        )
        val sid = requireNotNull(subscribe.headers["SID"])

        repeat(3) {
            eventing.publishAvTransport(
                DlnaRendererSnapshot(
                    currentUri = "https://example.com/video.m3u8",
                    currentUriMetadata = "",
                    transportState = "PLAYING",
                    transportStatus = "OK",
                    relativeTimePosition = "00:00:12",
                    volume = 50,
                    muted = false,
                ),
            )
            eventing.recordNotifyDeliveryResult(
                sid = sid,
                success = false,
            )
        }
        sent.clear()

        eventing.publishAvTransport(
            DlnaRendererSnapshot(
                currentUri = "https://example.com/video.m3u8",
                currentUriMetadata = "",
                transportState = "PLAYING",
                transportStatus = "OK",
                relativeTimePosition = "00:00:12",
                volume = 50,
                muted = false,
            ),
        )

        assertTrue(sent.isEmpty())
    }

    @Test
    fun publishContinuesWhenOneNotifyCallbackThrows() {
        val sent = mutableListOf<DlnaEventNotifyRequest>()
        val eventing = DlnaEventing(
            sendNotify = { request ->
                if (request.callbackUrl.contains("throwing")) {
                    throw IllegalStateException("notify boom")
                }
                sent += request
            },
        )
        eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/throwing>",
            timeoutHeader = "Second-300",
            sidHeader = null,
        )
        sent.clear()
        val healthySubscribe = eventing.subscribe(
            serviceName = "AVTransport",
            callbackHeader = "<http://127.0.0.1:1400/healthy>",
            timeoutHeader = "Second-300",
            sidHeader = null,
        )
        sent.clear()

        val failure = runCatching {
            eventing.publishAvTransport(
                DlnaRendererSnapshot(
                    currentUri = "https://example.com/video.m3u8",
                    currentUriMetadata = "",
                    transportState = "PLAYING",
                    transportStatus = "OK",
                    relativeTimePosition = "00:00:12",
                    volume = 50,
                    muted = false,
                ),
            )
        }.exceptionOrNull()

        assertEquals(null, failure)
        assertEquals(1, sent.size)
        assertEquals("http://127.0.0.1:1400/healthy", sent.single().callbackUrl)
        assertEquals(healthySubscribe.headers["SID"], sent.single().headers["SID"])
    }

    @Test
    fun subscribeStillSucceedsWhenInitialNotifyDispatchThrows() {
        val eventing = DlnaEventing(
            sendNotify = { throw IllegalStateException("notify boom") },
            currentAvTransportSnapshot = {
                DlnaRendererSnapshot(
                    currentUri = "https://example.com/video.m3u8",
                    currentUriMetadata = "",
                    transportState = "PLAYING",
                    transportStatus = "OK",
                    relativeTimePosition = "00:00:12",
                    volume = 50,
                    muted = false,
                )
            },
        )

        val result = runCatching {
            eventing.subscribe(
                serviceName = "AVTransport",
                callbackHeader = "<http://127.0.0.1:1400/throwing>",
                timeoutHeader = "Second-300",
                sidHeader = null,
            )
        }

        assertTrue(result.isSuccess)
        assertEquals(200, result.getOrThrow().statusCode)
    }

    @Test
    fun subscribePrunesOldestInfiniteSubscriptionsWhenActiveListExceedsCap() {
        val sent = mutableListOf<DlnaEventNotifyRequest>()
        val eventing = DlnaEventing(sendNotify = sent::add)

        repeat(70) { index ->
            val response = eventing.subscribe(
                serviceName = "AVTransport",
                callbackHeader = "<http://127.0.0.1:1400/callback/$index>",
                timeoutHeader = "Second-infinite",
                sidHeader = null,
            )

            assertEquals(200, response.statusCode)
        }

        eventing.publishAvTransport(
            DlnaRendererSnapshot(
                currentUri = "https://example.com/video.m3u8",
                currentUriMetadata = "",
                transportState = "PLAYING",
                transportStatus = "OK",
                relativeTimePosition = "00:00:12",
                volume = 50,
                muted = false,
            ),
        )

        assertEquals(64, sent.size)
        assertFalse(sent.any { it.callbackUrl.endsWith("/0") })
        assertFalse(sent.any { it.callbackUrl.endsWith("/5") })
        assertTrue(sent.any { it.callbackUrl.endsWith("/6") })
        assertTrue(sent.any { it.callbackUrl.endsWith("/69") })
    }
}
