package labs.newrapaw.dlna.probe.ui

import labs.newrapaw.dlna.probe.platform.RendererLogState

class MainActivityLogState {
    private val pending = RendererLogState()
    private val lock = Any()
    private var attached: RendererLogState? = null

    fun attach(serviceLogState: RendererLogState) = synchronized(lock) {
        if (attached === serviceLogState) return
        pending.snapshot().forEach(serviceLogState::append)
        attached = serviceLogState
    }

    fun append(message: String): List<String> = synchronized(lock) {
        val active = attached
        if (active != null) {
            active.append(message)
        } else {
            pending.append(message)
        }
    }

    fun snapshot(): List<String> = synchronized(lock) {
        attached?.snapshot() ?: pending.snapshot()
    }
}
