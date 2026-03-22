/** Base URL of edge Flask GUI (e.g. http://192.168.1.50:5000). No trailing slash. */
export function getEdgeSnapshotBase(): string {
  const raw = import.meta.env.VITE_EDGE_SNAPSHOT_BASE as string | undefined;
  return raw?.replace(/\/$/, '').trim() ?? '';
}

export function edgeSnapshotUrl(cameraId: string, cacheBust: number): string {
  const base = getEdgeSnapshotBase();
  if (!base) return '';
  return `${base}/api/snapshot/${encodeURIComponent(cameraId)}?t=${cacheBust}`;
}

/** MP4 URL for an alert evidence clip (edge ``/api/clips/{alertId}.mp4``). */
export function edgeAlertClipUrl(alertId: string): string {
  const direct = getEdgeSnapshotBase();
  if (direct) {
    return `${direct}/api/clips/${encodeURIComponent(alertId)}.mp4`;
  }
  return `/edge/api/clips/${encodeURIComponent(alertId)}.mp4`;
}

/** True when alert ``metadata`` JSON includes a non-empty ``clip_path`` (edge saved a clip). */
export function alertHasClipEvidence(metadata: string | null): boolean {
  if (!metadata?.trim()) return false;
  try {
    const o = JSON.parse(metadata) as Record<string, unknown>;
    const cp = o.clip_path;
    return typeof cp === 'string' && cp.trim().length > 0;
  } catch {
    return false;
  }
}

/**
 * Hide RTSP userinfo in UI (still stored server-side).
 * Uses the last `@` before the host so passwords containing `@` (e.g. Tapo) still mask correctly.
 */
export function maskRtspUrl(url: string): string {
  if (!url?.trim()) return '—';
  const u = url.trim();
  if (!/^rtsp:\/\//i.test(u)) return u;
  const at = u.lastIndexOf('@');
  const afterScheme = 'rtsp://'.length;
  if (at <= afterScheme) return u;
  return `rtsp://***${u.slice(at)}`;
}
