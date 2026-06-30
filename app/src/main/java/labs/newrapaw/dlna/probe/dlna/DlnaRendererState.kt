package labs.newrapaw.dlna.probe.dlna

internal class DlnaRendererState {
    var currentUri: String = ""
    var currentUriMetadata: String = ""
    var transportState: String = "STOPPED"
    var transportStatus: String = "OK"
    var relativeTimePosition: String = "00:00:00"
    var volume: Int = 50
    var muted: Boolean = false
}
