package labs.newrapaw.dlna.probe.platform

import android.content.Context
import java.util.UUID

private const val RENDERER_INSTALLATION_ID_PREFS = "newrapaw_renderer_identity"
private const val RENDERER_INSTALLATION_ID_KEY = "renderer_installation_id"

fun rendererInstallationId(context: Context): String {
    val preferences = context.applicationContext.getSharedPreferences(
        RENDERER_INSTALLATION_ID_PREFS,
        Context.MODE_PRIVATE,
    )
    return resolveRendererInstallationId(
        currentValue = preferences.getString(RENDERER_INSTALLATION_ID_KEY, null),
        persist = { installationId ->
            preferences.edit()
                .putString(RENDERER_INSTALLATION_ID_KEY, installationId)
                .apply()
        },
    )
}

internal fun resolveRendererInstallationId(
    currentValue: String?,
    persist: (String) -> Unit,
    generate: () -> String = { UUID.randomUUID().toString() },
): String {
    val existingValue = currentValue?.trim().orEmpty()
    if (existingValue.isNotEmpty()) {
        return existingValue
    }

    val generatedValue = generate()
    persist(generatedValue)
    return generatedValue
}
