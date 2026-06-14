# Linux DLNA Renderer Prototype Design

## Goal

Build a Linux prototype of a DLNA Digital Media Renderer (DMR) so a phone or desktop DLNA controller can discover this machine, send a media URL, and control playback through a local player.

## Scope

The prototype runs as a Node.js process on Linux. It advertises itself on the local network using SSDP, serves UPnP device and service descriptions over HTTP, accepts SOAP control requests for a minimal DMR command set, and delegates playback to `mpv`.

The first version is intentionally not a full DLNA compliance implementation. It is built to validate receiver behavior, controller interoperability, and the shape of the future TV receiver.

## Architecture

The service has four small units:

- SSDP advertiser: joins the UPnP multicast group, responds to `M-SEARCH`, and periodically sends `NOTIFY` advertisements.
- HTTP description server: serves `/description.xml` and service control/SCPD endpoints.
- SOAP control layer: parses action requests for `AVTransport`, `RenderingControl`, and `ConnectionManager`, updates in-memory renderer state, and returns UPnP SOAP responses.
- Player adapter: starts, pauses, stops, and adjusts volume through `mpv` in slave IPC mode.

The in-memory renderer state stores current URI, transport state, volume, and mute status. It is enough for one active playback session.

## Supported UPnP Services

`AVTransport` supports:

- `SetAVTransportURI`
- `GetMediaInfo`
- `GetTransportInfo`
- `GetPositionInfo`
- `Play`
- `Pause`
- `Stop`
- `Seek`

`RenderingControl` supports:

- `GetVolume`
- `SetVolume`
- `GetMute`
- `SetMute`

`ConnectionManager` supports:

- `GetProtocolInfo`
- `GetCurrentConnectionIDs`
- `GetCurrentConnectionInfo`

## Playback Behavior

`SetAVTransportURI` records the target URL and moves the renderer to `STOPPED`.

`Play` launches `mpv --input-ipc-server=<socket> --idle=yes --force-window=yes <url>` if a URL exists. If playback is already active, `Play` sends a pause-off command to mpv. `Pause`, `Stop`, `SetVolume`, and `SetMute` send JSON IPC commands when mpv is running and still update renderer state when it is not.

## Error Handling

Unsupported SOAP actions return a UPnP SOAP fault with HTTP 500. Malformed XML returns HTTP 400. `Play` without a current URI returns a SOAP fault. Missing `mpv` is surfaced in logs and through the SOAP response.

## Testing

Automated tests cover pure logic:

- device description XML contains the expected DMR services and URLs
- SOAP parser extracts action names and arguments
- renderer state transitions for URL, play, pause, stop, volume, and mute
- SOAP response/fault builders produce valid envelopes

Manual verification covers network behavior:

- run the service
- discover it from a DLNA controller on the same LAN
- push an HTTP media URL
- verify `mpv` starts and responds to pause/stop/volume commands
