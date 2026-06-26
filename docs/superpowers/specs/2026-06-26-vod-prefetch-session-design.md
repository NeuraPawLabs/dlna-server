# VOD Prefetch Session Design

## Goal

Reduce playback stalls for slow VOD HLS sources by extending the local HLS proxy from small burst prefetching to sustained, ordered background preloading with disk-backed caching.

The target behavior is:

- playback still goes through the existing local proxy
- VOD playback starts normally without waiting for a full download
- after playback starts, the proxy keeps downloading remaining segments in the background
- cached content is capped at `2 GB`
- when the cache reaches the limit, eviction favors smooth forward playback rather than preserving old content

## Scope

This design applies only to VOD HLS playback.

In scope:

- build a persistent VOD prefetch session per active manifest
- parse the full segment list for VOD manifests
- schedule sustained background segment downloads to the end of the asset
- allow prefetch concurrency to be configured from the management page
- keep cache behavior aware of playback position
- prefer player-critical requests over background prefetch work
- expose enough logs and state for manual verification

Out of scope:

- live HLS playlist refresh and rolling-window tracking
- DASH or non-HLS playback
- full offline download UX
- multi-title download management
- DRM-aware caching

## Current State

The current proxy already provides:

- local manifest and segment proxying through `LocalHlsProxy`
- manifest rewriting to local `/proxy/hls.m3u8` and `/proxy/segment.ts`
- disk-backed segment cache through `HlsSegmentCache`
- one-shot prefetch of a small number of segments after a manifest fetch

The current implementation does not provide:

- sustained prefetch to the end of a VOD asset
- queue state for the entire manifest
- playback-position-aware eviction
- priority handling between player requests and background downloads

## Recommended Approach

Extend the existing `LocalHlsProxy + HlsSegmentCache` design with a dedicated `VodPrefetchSession`.

This is preferable to simply raising the current prefetch count because a larger one-shot prefetch still stops early and does not adapt to long videos. It is also preferable to building a full offline downloader because playback should begin immediately and continue using the existing local proxy path.

## Architecture

### `LocalHlsProxy`

`LocalHlsProxy` remains the coordinator and request entry point.

New responsibilities:

- detect whether the fetched manifest is suitable for VOD prefetching
- create or replace the active `VodPrefetchSession`
- route player segment requests through cache-aware session state
- notify the active session which segment index is currently being requested by playback

Existing responsibilities stay unchanged:

- fetch upstream manifest bytes
- rewrite manifest lines to local proxy URLs
- fetch and normalize segments
- serve the local control page

Additional configuration responsibility:

- apply management-page updates to the active prefetch concurrency setting

### `VodPrefetchSession`

`VodPrefetchSession` is a new stateful component owned by `LocalHlsProxy`.

Responsibilities:

- store the active `manifestUrl`
- store the ordered `segmentUrls`
- map segment URL to segment index
- track `currentPlayIndex`
- track `nextPrefetchIndex`
- track in-flight prefetch tasks
- track failed indexes and retry counts
- run bounded background downloads until the end of the manifest
- support cancellation when playback switches to a different VOD

This component should have a small, explicit API:

- `start()`
- `cancel()`
- `updatePlaybackIndex(index: Int)`
- `onSegmentRequested(url: String)`
- `stats()` for logs and diagnostics

### `HlsSegmentCache`

`HlsSegmentCache` remains the storage layer but needs playback-aware eviction support.

Responsibilities kept:

- deduplicate concurrent fetches
- store segment bytes on disk
- expose cache statistics

New requirements:

- preserve segment metadata required for ordered eviction
- support eviction decisions based on segment index relative to current playback position instead of plain file recency alone

### Prefetch Settings Storage

Prefetch settings should be stored with the existing local management settings rather than introduced as a separate storage system.

Responsibilities:

- persist the configured prefetch concurrency value
- provide a default value when no value has been saved yet
- expose the current value to both the management page and `VodPrefetchSession`

## Manifest Eligibility

This implementation should only activate sustained prefetch for manifests that look like VOD.

Recommended detection:

- treat manifests containing `#EXT-X-ENDLIST` as VOD
- require a stable full segment list from the fetched manifest

If these conditions are not met, fall back to the current lightweight prefetch path rather than trying to force VOD behavior onto a live stream.

## Data Flow

1. Player requests local `/proxy/hls.m3u8`.
2. `LocalHlsProxy` fetches the upstream manifest.
3. The manifest is parsed into an ordered segment list.
4. If the manifest is VOD, `LocalHlsProxy` creates or refreshes a `VodPrefetchSession`.
5. The manifest is rewritten so all segment URIs point back to `/proxy/segment.ts`.
6. The rewritten manifest is returned to the player immediately.
7. The session starts background prefetch scheduling from index `0`.
8. The session keeps dispatching downloads in segment order until it reaches the end of the list.
9. When the player requests a segment:
   - if cached, return it immediately
   - if already in flight, wait for that result
   - if not started, issue an immediate player-priority fetch and advance playback position tracking
10. When the user changes prefetch concurrency from the management page, the new value is clamped and applied to the active session immediately.
11. When the user switches to another VOD URL, the old session is canceled and a new one is created.

## Download Scheduling

The prefetch design is not single-threaded, but it is still ordered.

Scheduling rules:

- dispatch order stays aligned with segment index order
- completion order may be out of order
- background concurrency is limited
- player-needed segments have higher priority than background prefetch segments

Recommended settings:

- background prefetch concurrency: `3`
- allowed configured concurrency range: `1..6`
- retries per failed segment: `2`

Behavior:

- the session maintains at most the configured number of background in-flight downloads
- it schedules the next available segment indexes in ascending order
- if segment `5` finishes before segment `4`, both are still valid cache fills
- if playback requests segment `8` before background prefetch reaches it, the request is promoted and fetched immediately
- if playback requests a segment already being prefetched, no duplicate download is issued
- if the management page sets a new concurrency value, the running session adjusts its in-flight ceiling without requiring playback restart

This keeps the pipeline full on slow links without overloading the source or starving the player.

Configuration handling:

- the management page exposes a numeric prefetch concurrency control
- the backend clamps submitted values into `1..6`
- the stored default remains `3`
- invalid or missing values fall back to `3`

## Cache Policy

Cache size is capped at `2 GB`.

The eviction strategy should optimize for forward playback smoothness instead of historical retention.

Eviction priority:

1. keep segments after the current playback position
2. keep a small neighborhood around the current playback position
3. evict old played segments first
4. if absolutely necessary, evict far-future segments that are furthest from the current play point

This differs from plain LRU because LRU does not understand playback order and may retain recently touched historical segments that no longer matter.

Recommended metadata per cached segment:

- upstream URL
- segment index
- manifest identity
- last access time
- played/not-yet-played classification derived from `currentPlayIndex`

If the cache limit is reached:

- first remove segments with `index < currentPlayIndex`
- if more space is still required, remove the lowest-priority future segments furthest from the play head

## Failure Handling

The background prefetcher must not destabilize the foreground playback path.

Rules:

- background prefetch failures are logged and retried up to `2` times
- after final retry failure, the session skips that segment and continues prefetching later indexes
- if playback eventually reaches a skipped segment, the foreground request attempts a direct fetch again
- canceled sessions stop issuing new background work immediately
- already completed cache files may remain until normal eviction removes them

When the upstream source is very slow:

- player-priority requests may temporarily consume a concurrency slot
- background downloads should resume once priority work drains

## Logging And Observability

Add targeted logs so device testing can show whether the prefetcher is actually helping.

Suggested log events:

- session created with manifest URL and segment count
- session created with configured prefetch concurrency
- background prefetch started
- prefetched segment index and URL
- player-priority fetch triggered for a not-yet-prefetched segment
- prefetch concurrency updated from the management page
- cache eviction with segment index and reason
- prefetch retry and final skip
- session canceled and replaced
- session completed at end of manifest

Optional future control-page stats:

- active VOD manifest
- total segment count
- completed segment count
- current play index
- next prefetch index
- active background downloads
- configured prefetch concurrency

These metrics are useful but not required for the first implementation.

## Testing

### Unit Tests

Add focused tests for:

- VOD manifest detection
- ordered session creation from a manifest
- bounded concurrency scheduling
- concurrency reconfiguration while a session is active
- player-priority request promotion
- retry and skip behavior
- playback-aware eviction ordering

### Integration Tests

Use a fake slow upstream source and verify:

- background prefetch continues beyond the first few segments
- prefetched segments are served from cache without duplicate upstream fetches
- player requests can wait on or preempt in-flight background work correctly
- management-page concurrency changes alter the number of active background downloads without restarting playback
- session cancellation stops further prefetch for the old manifest

### Manual Verification

On device:

- play a slow VOD HLS source
- change prefetch concurrency from the management page during playback
- confirm cache stats continue growing beyond the first few segments
- confirm the session reacts to the new concurrency value without restarting playback
- confirm logs show sustained prefetch activity toward the end of the asset
- observe lower stall frequency compared with the current lightweight prefetch behavior

## Implementation Notes

Prefer a narrowly scoped implementation:

- keep ExoPlayer integration unchanged
- keep non-VOD behavior on the existing lightweight path
- add one new session component rather than spreading queue state across multiple classes

The first implementation should solve one problem clearly: VOD playback from slow sources should improve because the proxy keeps downloading ahead in the background until the asset ends, constrained by a `2 GB` playback-aware cache.
