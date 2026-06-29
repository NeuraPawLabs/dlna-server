# VOD Sessionized Proxy Redesign

Date: 2026-06-28

## Goal

Rebuild the current VOD playback path so the proxy layer becomes a true playback orchestration layer rather than a request forwarder with prefetch bolted on.

This redesign targets:

- VOD only
- Single-bitrate HLS only
- Resource coverage:
  - video segments
  - independent audio segments
  - subtitle segments
  - init segments
  - keys
- One end-to-end playback session model for:
  - download
  - local serving
  - player telemetry
  - diagnostics

The immediate purpose is to eliminate the current mismatch between:

- "many segments cached"
- "playback still stalls every few seconds"
- "diagnostics do not reflect actual playback state"

## Non-Goals

This phase does not include:

- live playback
- multi-bitrate adaptive HLS
- DRM-specific handling beyond ordinary HLS key resources
- subtitle styling or subtitle selection UI
- multi-audio selection UI

If a source falls outside this phase boundary, the system should fail explicitly rather than silently degrading into misleading diagnostics.

## Current Problem

The current architecture is centered on:

- local proxy forwarding
- segment-level caching
- request-driven prefetch
- diagnostics inferred from proxy request patterns

That model has structural weaknesses:

1. Cached segments do not guarantee stable playback at the active playhead.
2. Segment-level diagnostics do not model all playback dependencies.
3. Audio, subtitles, init segments, and keys are not part of a unified playable timeline model.
4. Playback truth and proxy truth come from different state sources.
5. Session cleanup depends too much on incidental flow rather than a strict session lifecycle.

The result is that the system can show many green cached blocks while the player still stalls at segment boundaries.

## Target Architecture

The new architecture has five core modules:

1. `PlaybackSessionManager`
2. `ManifestPlanner`
3. `SessionDownloader`
4. `SessionLocalServer`
5. `PlaybackTelemetryBridge`

The new core rule is:

ExoPlayer consumes only the current session's local resource view, and all download, cache, and diagnostics state is derived from that session.

## PlaybackSessionManager

`PlaybackSessionManager` owns the active VOD playback session.

Responsibilities:

- create a new session for each play request
- close the old session before the new one starts
- clear previous session cache and local files
- unify backend play requests and DLNA play requests into the same session startup path
- expose the active session to downloader, server, telemetry, and diagnostics layers

### Session Rules

- At most one active VOD session exists at any time.
- A new play request always closes the previous session.
- Old session cleanup must not depend on graceful stop.
- The session manager is the only owner of session lifecycle transitions.

### Session State

Each `PlaybackSession` contains:

- `sessionId`
- `sourceUrl`
- `entryManifestUrl`
- `createdAtMs`
- `localRootDir`
- `status`
- `timeline`
- `assets`
- `startupGate`
- `downloadSnapshot`
- `playbackSnapshot`
- `diagnosticsSnapshot`

### Session Status Values

- `PREPARING`
- `PRIMING`
- `READY`
- `PLAYING`
- `DEGRADED`
- `STALLED`
- `COMPLETED`
- `FAILED`
- `CLOSED`

### Session Status Semantics

- `PREPARING`
  - parsing source manifest
  - building timeline
  - allocating local session paths
- `PRIMING`
  - fetching startup-critical assets
- `READY`
  - startup gate satisfied
  - local session manifest can be handed to the player
- `PLAYING`
  - active playback in progress
- `DEGRADED`
  - hard playback dependencies are satisfied
  - soft dependencies such as subtitles are incomplete or failed
- `STALLED`
  - real playback supply failure at the playhead
- `COMPLETED`
  - VOD playback reached terminal end
- `FAILED`
  - hard dependency failure made playback impossible
- `CLOSED`
  - session was superseded or explicitly torn down

## ManifestPlanner

`ManifestPlanner` converts the source HLS structure into a normalized session model.

This planner must support:

- single-bitrate VOD HLS
- video segment timeline
- independent audio playlists and segments
- subtitle playlists and segments
- init segments
- key resources

### Explicit Input Constraints

- If the entry URL is a master playlist, the planner may accept it only if it resolves to exactly one playable bitrate variant for this phase.
- If the source requires multi-bitrate adaptation, the planner must reject it clearly.
- If the source structure is unsupported, the planner must return an explicit planning error.

### Two-Layer Model

The planner emits:

1. `SessionAsset`
2. `TimelineSlot`

### SessionAsset

Represents one fetchable resource.

Fields:

- `assetId`
- `kind`
  - `manifest`
  - `video_segment`
  - `audio_segment`
  - `subtitle_segment`
  - `init_segment`
  - `key`
- `trackId`
- `url`
- `durationMs`
- `sequence`
- `blocking`
- `requiredForStartup`
- `localPath`
- `downloadState`

### Asset Blocking Rules

Hard dependencies (`blocking = true`):

- video
- audio
- init
- key

Soft dependencies (`blocking = false`):

- subtitle

Subtitles may fail without stopping playback, but the session must record degraded playback state.

### TimelineSlot

Represents a single playback time range and everything required to play that range.

Fields:

- `slotIndex`
- `startMs`
- `endMs`
- `videoAssetId`
- `audioAssetIds`
- `subtitleAssetIds`
- `prerequisiteAssetIds`

For this phase:

- one slot maps to one video segment range
- audio and subtitle resources aligned to that time range are attached to the same slot
- init and key assets are attached as prerequisites rather than standalone slots

### Slot Playability States

- `READY`
- `DEGRADED`
- `BLOCKED`

Rules:

- `READY`
  - video ready
  - audio ready
  - init ready
  - key ready
- `DEGRADED`
  - all hard dependencies ready
  - subtitle missing or failed
- `BLOCKED`
  - any hard dependency missing, incomplete, or failed

This slot model becomes the basis for playback diagnostics and the admin health view.

## SessionDownloader

`SessionDownloader` is the primary supply engine for the current playback session.

It replaces the current request-driven prefetch model.

Responsibilities:

- maintain prioritized download queues
- download startup-critical resources first
- continue background full-session caching after startup
- react to playhead movement and seek events
- retry failures according to policy
- emit resource-level timing and failure data

### Download Priorities

Priority order:

1. init assets
2. key assets
3. startup audio window
4. startup subtitle window
5. startup video window
6. near-playhead forward window
7. remaining tail of the VOD session

### Startup Gate

Playback must not begin immediately.

The player starts only after a startup gate passes.

Default startup gate content:

- init assets ready
- current key assets ready
- first N seconds of video ready
- first N seconds of audio ready
- first M seconds of subtitles ready or explicitly downgraded
- first K timeline slots not `BLOCKED`

Recommended defaults:

- video/audio startup window: 12 to 20 seconds
- subtitle startup window: 4 to 8 seconds

### Full Cache Strategy

After startup:

- downloader keeps the near-playhead window highest priority
- downloader continues sequential full-session caching in the background
- downloader records whether every resource became locally available before the player required it

### Seek Handling

When seek occurs:

- update target playhead slot
- discard old forward-priority queue
- rebuild a new forward-priority queue from the new slot
- keep already completed assets
- cancel non-critical in-flight tasks that are now far behind the playhead if cancellation is supported safely

## SessionLocalServer

`SessionLocalServer` serves a local resource view for the active playback session.

It is not a plain forward proxy.

Responsibilities:

- expose one session-local manifest URL to ExoPlayer
- rewrite referenced asset URLs to session-local asset routes
- serve only current session resources
- distinguish between:
  - asset ready locally
  - asset pending download
  - asset failed

### Local Routes

Suggested route shape:

- `/session/{sessionId}/manifest.m3u8`
- `/session/{sessionId}/asset/{assetId}`

### Serving Rules

When the player requests a resource:

1. If local file is ready:
   - return local file immediately
2. If asset is expected imminently:
   - allow a bounded wait
   - if wait exceeds threshold, treat as supply failure
3. If asset failed:
   - return explicit failure
   - update diagnostics

This layer is what turns the proxy into a real playback supply layer rather than a forwarding layer.

## PlaybackTelemetryBridge

`PlaybackTelemetryBridge` makes ExoPlayer the primary truth source for playback state.

Sources:

- `Player.Listener`
- `AnalyticsListener`
- if needed, session-local data source wrappers for local hit attribution

### Collected Signals

- `currentPosition`
- `bufferedPosition`
- `playbackState`
- `isLoading`
- load started
- load completed
- load canceled
- load error
- resource URI
- bytes loaded
- load duration
- track type
- data type
- local vs network-local-server resolution details

### Derived Playback Truth

The telemetry bridge derives:

- `playHeadSlotIndex`
- `bufferHeadSlotIndex`
- `currentlyLoadingAssetId`
- `currentLoadTrackType`
- `currentLoadSource`
- `stallReason`

### Stall Definition

Stall detection must use real playback evidence.

A true stall is when:

- `currentPosition` stops advancing
- `bufferedPosition - currentPosition` falls below threshold
- the player remains loading or buffering
- the current or next required slot is not playable or its critical asset load is blocked

This replaces proxy-request inference as the top-level bottleneck source.

## Diagnostics Model

Diagnostics must be rebuilt around session, slot, and asset truth.

### Session-Level Diagnostics

- active session status
- current playhead slot
- current buffer edge slot
- continuous playable window duration
- current stall reason
- startup gate state

### Slot-Level Diagnostics

- slot status (`READY`, `DEGRADED`, `BLOCKED`)
- blocking dependency types
- whether each required resource is local-ready

### Asset-Level Diagnostics

- download state
- elapsed time
- retries
- failure reason
- local availability
- bytes
- source timing

### Primary Bottleneck Priority

Top-level bottleneck selection should prefer:

1. hard dependency blocking current or next slot
2. player buffer window exhausted
3. critical asset load timeout/failure
4. soft dependency degradation
5. no clear issue

Legacy heuristics such as simple proxy prefetch lead count must not outrank real session and player telemetry.

## Admin UI Direction

The current admin health view must evolve from segment state to slot state.

### Main Health Graph

Each cell represents a `TimelineSlot`.

Color/state semantics:

- blue: current playhead slot
- green: slot fully playable
- yellow: slot degraded but playable
- red: slot blocked
- gray: not ready or evicted
- buffer marker: real player buffer edge

### Detail Expansion

Selecting a slot shows:

- video asset state
- audio asset state
- subtitle asset state
- init state
- key state
- download timings
- exact blocking reason

The graph should visualize actual playability, not only video cache presence.

## Cache Model

The current URL-oriented cache is insufficient for the new model.

The redesign needs a session-scoped cache store:

- one local root per playback session
- assets addressed by session asset identity, not only raw URL
- clear ownership of:
  - asset metadata
  - local file path
  - readiness state
  - eviction state

### Cleanup Rules

- New session creation clears old session cache immediately.
- Old session closure removes its local files and metadata.
- Cleanup does not depend on graceful stop.
- Session-local cache ownership must be explicit so that diagnostics and serving refer to the same storage truth.

## Interaction With Existing Code

### Likely to Keep

- outer Android TV activity shell
- management UI shell structure
- HTTP server foundation
- proxy configuration storage
- some HLS URL rewriting and parsing utilities

### Must Be Reworked

- `LocalHlsProxy`
  - from request-forwarding core into session orchestrator + local serving coordinator
- `VodPrefetchSession`
  - replace with session downloader built around full dependency timeline
- `HlsSegmentCache`
  - replace or evolve into session asset store
- current segment-only diagnostics
  - replace with session/slot/asset diagnostics

## Implementation Shape

Even though delivery is intended as a full VOD redesign, the code should be structured in modular steps inside one implementation effort:

1. introduce session manager
2. introduce planner and timeline model
3. introduce session asset cache/store
4. introduce downloader
5. introduce local session server
6. switch player entry to session-local manifest
7. connect ExoPlayer telemetry
8. rebuild diagnostics and cache health view around slots/assets
9. retire or narrow old segment-prefetch path

## Risks

1. Increased coupling between playback and local serving
   - acceptable and necessary for truthful session behavior
2. More complex audio/subtitle alignment
   - required to avoid continued false-positive playability
3. Large code change surface
   - requires firm module boundaries
4. Temporary migration confusion if old and new flows share mutable state
   - avoid long-lived mixed ownership

## Success Criteria

The redesign is complete only if the following are true:

1. A new VOD session always clears the previous session state and cache.
2. ExoPlayer consumes the current session's local manifest and session-local assets.
3. Seek causes downloader reprioritization around the new playhead.
4. Diagnostics can distinguish:
   - video starvation
   - audio starvation
   - init/key blockage
   - subtitle degradation
   - general load timeout
5. Admin health graph reflects slot-level real playability.
6. Player playhead, player buffer edge, session slot readiness, and diagnostics all come from one coherent session model.
7. Repeated segment-duration stalling can be traced to a concrete missing or late playback dependency rather than inferred indirectly from proxy request patterns.

## Recommended First Validation Scenarios

After implementation, validate at least:

1. simple VOD with muxed video/audio
2. VOD with independent audio playlist
3. VOD with subtitles
4. seek forward during startup
5. seek forward during steady playback
6. session replacement after abnormal prior playback termination
7. playback under slow upstream where startup gate should delay start instead of allowing periodic stall

## Decision Summary

This redesign explicitly chooses:

- session truth over request inference
- slot-level playability over segment-only cache visualization
- player telemetry as primary playback truth
- immediate session cleanup on new play request
- full dependency modeling for VOD playback

That is the minimum architecture required to turn the proxy layer into a real playback orchestration layer and solve the current class of "cached but still stalls" failures from the root.
