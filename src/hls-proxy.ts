export function resolvePlayableUri(uri: string, proxyBaseUrl: string): string {
  if (!isLikelyHlsManifest(uri)) {
    return uri;
  }

  return `${proxyBaseUrl}/proxy/hls.m3u8?u=${encodeProxyUrl(uri)}`;
}

export function rewriteHlsManifest(
  manifest: string,
  manifestUrl: string,
  proxyBaseUrl: string,
): string {
  return manifest
    .split(/\r?\n/)
    .map((line) => rewriteManifestLine(line, manifestUrl, proxyBaseUrl))
    .join("\n");
}

export function stripPngWrapperFromSegment(segment: Buffer): Buffer {
  const tsOffset = findMpegTsOffset(segment);
  return tsOffset > 0 ? segment.subarray(tsOffset) : segment;
}

export function isLikelyHlsManifest(uri: string): boolean {
  return /\.m3u8(?:$|[/?#&=;%])/i.test(uri);
}

function rewriteManifestLine(line: string, manifestUrl: string, proxyBaseUrl: string): string {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith("#")) {
    return line;
  }

  const segmentUrl = new URL(trimmed, manifestUrl).toString();
  return `${proxyBaseUrl}/proxy/segment.ts?u=${encodeProxyUrl(segmentUrl)}`;
}

export function encodeProxyUrl(url: string): string {
  return Buffer.from(url, "utf8").toString("base64url");
}

export function decodeProxyUrl(encoded: string): string {
  return Buffer.from(encoded, "base64url").toString("utf8");
}

function findMpegTsOffset(segment: Buffer): number {
  for (let offset = 0; offset < segment.length - 376; offset += 1) {
    if (
      segment[offset] === 0x47 &&
      segment[offset + 188] === 0x47 &&
      segment[offset + 376] === 0x47
    ) {
      return offset;
    }
  }

  return 0;
}
