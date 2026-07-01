package labs.newrapaw.dlna.probe.platform

import android.content.Context
import android.net.ConnectivityManager
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

enum class RendererNetworkRecoveryAction {
    NONE,
    PAUSE_PLAYBACK,
    REBUILD_SESSION,
}

data class RendererNetworkRecoveryDecision(
    val action: RendererNetworkRecoveryAction,
    val resumePositionMs: Long? = null,
    val resumePlayback: Boolean = false,
)

fun applyRendererNetworkRecoveryDecision(
    reason: String,
    decision: RendererNetworkRecoveryDecision,
    pauseForNetworkLoss: () -> Unit,
    rebuildSessionAfterNetworkRecovery: (Long?, Boolean) -> Unit,
    appendLog: (String) -> Unit,
) {
    when (decision.action) {
        RendererNetworkRecoveryAction.NONE -> Unit
        RendererNetworkRecoveryAction.PAUSE_PLAYBACK -> {
            appendLog("Network unavailable, pausing playback")
            runCatching { pauseForNetworkLoss() }
                .onFailure { appendLog("Network recovery failed: ${it::class.java.simpleName}: ${it.message}") }
        }
        RendererNetworkRecoveryAction.REBUILD_SESSION -> {
            appendLog("Network $reason, rebuilding active session")
            runCatching {
                rebuildSessionAfterNetworkRecovery(
                    decision.resumePositionMs,
                    decision.resumePlayback,
                )
            }.onFailure {
                appendLog("Network recovery failed: ${it::class.java.simpleName}: ${it.message}")
            }
        }
    }
}

class RendererServiceNetworkRecoveryCoordinator(
    initialIpAddress: String = "unknown",
) {
    private val lock = Any()
    private var lastKnownIpAddress: String = initialIpAddress
    private var networkUnavailable: Boolean = false
    private var capturedPositionMs: Long? = null
    private var capturedResumePlayback: Boolean = false

    fun onNetworkLost(
        currentPositionMs: Long?,
        isPlaying: Boolean,
        hasActiveSession: Boolean,
    ): RendererNetworkRecoveryDecision = synchronized(lock) {
        if (!hasActiveSession || networkUnavailable) {
            return RendererNetworkRecoveryDecision(RendererNetworkRecoveryAction.NONE)
        }
        networkUnavailable = true
        capturedPositionMs = currentPositionMs?.takeIf { it >= 0L }
        capturedResumePlayback = isPlaying
        RendererNetworkRecoveryDecision(RendererNetworkRecoveryAction.PAUSE_PLAYBACK)
    }

    fun onNetworkAvailable(
        currentIpAddress: String,
        currentPositionMs: Long?,
        isPlaying: Boolean,
        hasActiveSession: Boolean,
    ): RendererNetworkRecoveryDecision = synchronized(lock) {
        val ipChanged = currentIpAddress != "unknown" &&
            lastKnownIpAddress != "unknown" &&
            currentIpAddress != lastKnownIpAddress
        if (currentIpAddress != "unknown") {
            lastKnownIpAddress = currentIpAddress
        }
        if (!hasActiveSession) {
            networkUnavailable = false
            capturedPositionMs = null
            capturedResumePlayback = false
            return RendererNetworkRecoveryDecision(RendererNetworkRecoveryAction.NONE)
        }
        if (!networkUnavailable && !ipChanged) {
            return RendererNetworkRecoveryDecision(RendererNetworkRecoveryAction.NONE)
        }
        val resumePositionMs = capturedPositionMs ?: currentPositionMs?.takeIf { it >= 0L }
        val resumePlayback = if (networkUnavailable) capturedResumePlayback else isPlaying
        networkUnavailable = false
        capturedPositionMs = null
        capturedResumePlayback = false
        RendererNetworkRecoveryDecision(
            action = RendererNetworkRecoveryAction.REBUILD_SESSION,
            resumePositionMs = resumePositionMs,
            resumePlayback = resumePlayback,
        )
    }
}

fun startRendererServiceNetworkMonitor(
    context: Context,
    currentPositionMs: () -> Long?,
    isPlaying: () -> Boolean,
    hasActiveSession: () -> Boolean,
    pauseForNetworkLoss: () -> Unit,
    rebuildSessionAfterNetworkRecovery: (Long?, Boolean) -> Unit,
    appendLog: (String) -> Unit,
): Closeable? {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return null
    return RendererServiceNetworkMonitor(
        connectivityManager = connectivityManager,
        currentIpAddress = ::resolveRendererServiceIpAddress,
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
        hasActiveSession = hasActiveSession,
        pauseForNetworkLoss = pauseForNetworkLoss,
        rebuildSessionAfterNetworkRecovery = rebuildSessionAfterNetworkRecovery,
        appendLog = appendLog,
    ).also { it.start() }
}

private class RendererServiceNetworkMonitor(
    private val connectivityManager: ConnectivityManager,
    private val currentIpAddress: () -> String,
    private val currentPositionMs: () -> Long?,
    private val isPlaying: () -> Boolean,
    private val hasActiveSession: () -> Boolean,
    private val pauseForNetworkLoss: () -> Unit,
    private val rebuildSessionAfterNetworkRecovery: (Long?, Boolean) -> Unit,
    private val appendLog: (String) -> Unit,
) : Closeable {
    private val coordinator = RendererServiceNetworkRecoveryCoordinator(
        initialIpAddress = currentIpAddress(),
    )
    private val closed = AtomicBoolean(false)
    private val callbacks = RendererServiceNetworkCallbacks(
        connectivityManager = connectivityManager,
        closed = closed,
        onAvailable = { handleAvailable("available") },
        onLost = {
            val decision = coordinator.onNetworkLost(
                currentPositionMs = currentPositionMs(),
                isPlaying = isPlaying(),
                hasActiveSession = hasActiveSession(),
            )
            applyRendererNetworkRecoveryDecision(
                reason = "unavailable",
                decision = decision,
                pauseForNetworkLoss = pauseForNetworkLoss,
                rebuildSessionAfterNetworkRecovery = rebuildSessionAfterNetworkRecovery,
                appendLog = appendLog,
            )
        },
        onLinkPropertiesChanged = { handleAvailable("link properties changed") },
    )

    fun start() {
        callbacks.register()
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        callbacks.close()
    }

    private fun handleAvailable(reason: String) {
        if (closed.get()) return
        val decision = coordinator.onNetworkAvailable(
            currentIpAddress = currentIpAddress(),
            currentPositionMs = currentPositionMs(),
            isPlaying = isPlaying(),
            hasActiveSession = hasActiveSession(),
        )
        applyRendererNetworkRecoveryDecision(
            reason = reason,
            decision = decision,
            pauseForNetworkLoss = pauseForNetworkLoss,
            rebuildSessionAfterNetworkRecovery = rebuildSessionAfterNetworkRecovery,
            appendLog = appendLog,
        )
    }
}
