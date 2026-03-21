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
