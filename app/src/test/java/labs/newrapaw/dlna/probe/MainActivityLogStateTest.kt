package labs.newrapaw.dlna.probe

import labs.newrapaw.dlna.probe.platform.RendererLogState
import labs.newrapaw.dlna.probe.ui.MainActivityLogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLogStateTest {
    @Test
    fun preBindLogsAreReplayedIntoAttachedServiceLog() {
        val logState = MainActivityLogState()
        logState.append("Renderer service bind failed")
        val serviceLog = RendererLogState()

        val attachMethod = MainActivityLogState::class.java.methods.firstOrNull {
            it.name == "attach" &&
                it.parameterCount == 1 &&
                it.parameterTypes.single() == RendererLogState::class.java
        }

        assertNotNull("MainActivityLogState.attach(RendererLogState) should exist", attachMethod)
        attachMethod!!.invoke(logState, serviceLog)

        assertTrue(serviceLog.snapshot().contains("Renderer service bind failed"))
        assertEquals(serviceLog.snapshot(), logState.snapshot())
    }

    @Test
    fun attachedServiceLogReceivesNewEntries() {
        val logState = MainActivityLogState()
        val serviceLog = RendererLogState()
        val attachMethod = MainActivityLogState::class.java.methods.firstOrNull {
            it.name == "attach" &&
                it.parameterCount == 1 &&
                it.parameterTypes.single() == RendererLogState::class.java
        }

        assertNotNull("MainActivityLogState.attach(RendererLogState) should exist", attachMethod)
        attachMethod!!.invoke(logState, serviceLog)
        logState.append("Renderer service unavailable")

        assertTrue(serviceLog.snapshot().contains("Renderer service unavailable"))
        assertEquals(serviceLog.snapshot(), logState.snapshot())
    }
}
