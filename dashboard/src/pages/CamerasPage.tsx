import { useCallback, useEffect, useState } from 'react';
import { clsx } from 'clsx';
import { Camera as CameraIcon, ExternalLink, X } from 'lucide-react';
import { useCameras } from '@/api/devices';
import type { Camera } from '@/types';
import { edgeSnapshotUrl, getEdgeSnapshotBase, maskRtspUrl } from '@/lib/edgeSnapshot';

const fallbackCameras: Camera[] = [
  { id: 'CAM-01', nodeId: 'node-a', name: 'Front Gate', rtspUrl: '', model: null, locationDescription: null, fpsTarget: 2.3, resolution: '640x480', enabled: true, status: 'active', createdAt: '' },
  { id: 'CAM-02', nodeId: 'node-a', name: 'Storage Shed', rtspUrl: '', model: null, locationDescription: null, fpsTarget: 2.3, resolution: '640x480', enabled: true, status: 'active', createdAt: '' },
  { id: 'CAM-03', nodeId: 'node-a', name: 'Pond Area', rtspUrl: '', model: null, locationDescription: null, fpsTarget: 2.3, resolution: '640x480', enabled: true, status: 'alert', createdAt: '' },
  { id: 'CAM-04', nodeId: 'node-a', name: 'East Perimeter', rtspUrl: '', model: null, locationDescription: null, fpsTarget: 2.3, resolution: '640x480', enabled: true, status: 'active', createdAt: '' },
  { id: 'CAM-05', nodeId: 'node-b', name: 'Snake Zone', rtspUrl: '', model: null, locationDescription: null, fpsTarget: 2.3, resolution: '640x480', enabled: true, status: 'active', createdAt: '' },
  { id: 'CAM-06', nodeId: 'node-b', name: 'Cattle Pen', rtspUrl: '', model: null, locationDescription: null, fpsTarget: 2.3, resolution: '640x480', enabled: true, status: 'active', createdAt: '' },
  { id: 'CAM-07', nodeId: 'node-b', name: 'Pasture NW', rtspUrl: '', model: null, locationDescription: null, fpsTarget: 2.3, resolution: '640x480', enabled: true, status: 'active', createdAt: '' },
  { id: 'CAM-08', nodeId: 'node-b', name: 'Farmhouse', rtspUrl: '', model: null, locationDescription: null, fpsTarget: 2.3, resolution: '640x480', enabled: false, status: 'offline', createdAt: '' },
];

const SNAPSHOT_REFRESH_MS = 3000;

/**
 * Fills a `position: relative` aspect box. Do not wrap in `flex items-center` — that can zero-out
 * `%` heights and leave only the black background visible.
 */
function ModalLiveSnapshot({
  url,
  cameraId,
  edgeBase,
  label,
}: {
  url: string;
  cameraId: string;
  edgeBase: string;
  label: string;
}) {
  const [failed, setFailed] = useState(false);

  if (failed) {
    return (
      <div className="absolute inset-0 flex items-center justify-center bg-zinc-900 p-4">
        <p className="text-sc-text-muted text-sm text-center">
          Snapshot failed to load. Open this URL in the browser (same machine as the dashboard) to
          verify the edge is up:{' '}
          <code className="block mt-2 text-xs text-sc-text-dim break-all">{url}</code>
          <span className="block mt-2 text-xs">
            Expected: Flask on <code className="text-sc-text-dim">{edgeBase}</code>, camera id{' '}
            <code className="text-sc-text-dim">{cameraId}</code> must match edge{' '}
            <code className="text-sc-text-dim">cameras.json</code>.
          </span>
        </p>
      </div>
    );
  }

  return (
    <img
      src={url}
      alt={`Snapshot from ${label}`}
      className="absolute inset-0 h-full w-full object-contain bg-zinc-900"
      decoding="async"
      onError={() => setFailed(true)}
    />
  );
}

function CameraThumbnail({
  snapUrl,
  isOffline,
}: {
  snapUrl: string;
  isOffline: boolean;
}) {
  const [imgFailed, setImgFailed] = useState(false);
  const showLive = Boolean(snapUrl) && !isOffline && !imgFailed;

  return (
    <>
      {showLive ? (
        <img
          src={snapUrl}
          alt=""
          className="absolute inset-0 h-full w-full object-cover z-10"
          loading="lazy"
          onError={() => setImgFailed(true)}
        />
      ) : null}
      <CameraIcon
        size={40}
        className={clsx(
          'relative z-[1]',
          showLive ? 'opacity-0 pointer-events-none' : 'text-sc-text-muted/20',
        )}
        aria-hidden
      />
    </>
  );
}

export default function CamerasPage() {
  const { data: cameras } = useCameras();
  const cameraList = cameras ?? fallbackCameras;
  const edgeBase = getEdgeSnapshotBase();
  const hasEdgePreview = Boolean(edgeBase);

  const [selected, setSelected] = useState<Camera | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!hasEdgePreview) return;
    const id = window.setInterval(() => setTick((t) => t + 1), SNAPSHOT_REFRESH_MS);
    return () => window.clearInterval(id);
  }, [hasEdgePreview]);

  const closeModal = useCallback(() => setSelected(null), []);

  useEffect(() => {
    if (!selected) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeModal();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selected, closeModal]);

  return (
    <>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
        {cameraList.map((cam) => {
          const isAlert = cam.status === 'alert';
          const isOffline = cam.status === 'offline';
          const snap = hasEdgePreview ? edgeSnapshotUrl(cam.id, tick) : '';

          return (
            <div
              key={cam.id}
              role="button"
              tabIndex={0}
              aria-label={`Open details for ${cam.name}`}
              onClick={() => setSelected(cam)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  setSelected(cam);
                }
              }}
              className={clsx(
                'bg-sc-surface rounded-xl overflow-hidden border cursor-pointer hover:border-sc-accent/30 transition-colors text-left',
                isAlert ? 'border-sc-critical/40' : 'border-sc-border',
              )}
            >
              <div
                className={clsx(
                  'h-[140px] flex items-center justify-center relative overflow-hidden',
                  isOffline
                    ? 'bg-sc-surface-alt'
                    : 'bg-gradient-to-br from-sc-surface-alt to-[#1a2a3a]',
                )}
              >
                {/* key: new tick remounts thumbnail so failed loads retry when URL updates */}
                <CameraThumbnail key={`${cam.id}-${tick}`} snapUrl={snap} isOffline={isOffline} />

                {isAlert && (
                  <div className="absolute top-2 right-2 z-[2] px-2 py-0.5 bg-sc-critical rounded text-[10px] text-white font-bold font-mono">
                    ALERT
                  </div>
                )}

                <div className="absolute top-2 left-2 z-[2] px-2 py-0.5 bg-black/60 rounded text-[10px] text-sc-text-dim font-mono">
                  {cam.fpsTarget} FPS
                </div>
              </div>

              <div className="px-4 py-3">
                <div className="flex justify-between items-center">
                  <span className="text-sc-text text-sm font-semibold">{cam.name}</span>
                  <div
                    className={clsx(
                      'w-2 h-2 rounded-full',
                      isOffline
                        ? 'bg-sc-text-muted'
                        : isAlert
                          ? 'bg-sc-critical'
                          : 'bg-sc-success',
                    )}
                  />
                </div>
                <div className="text-sc-text-muted text-[11px] font-mono mt-1">
                  {cam.id} · {cam.nodeId}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {!hasEdgePreview && (
        <p className="mt-4 text-sc-text-muted text-sm max-w-2xl">
          <strong className="text-sc-text-dim">Live view:</strong> Browsers cannot play RTSP directly. When the edge
          pipeline is running, set{' '}
          <code className="text-xs bg-sc-surface-alt px-1 rounded">VITE_EDGE_SNAPSHOT_BASE=http://&lt;edge-ip&gt;:5000</code>{' '}
          in <code className="text-xs bg-sc-surface-alt px-1 rounded">.env</code> and restart Vite — thumbnails use
          the edge Flask <code className="text-xs">/api/snapshot/&lt;camera_id&gt;</code> feed (same IDs as in
          device-service).
        </p>
      )}

      {selected && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70"
          role="presentation"
          onClick={closeModal}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="camera-detail-title"
            className="bg-sc-surface border border-sc-border rounded-xl max-w-lg w-full max-h-[90vh] overflow-y-auto shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-2 p-4 border-b border-sc-border">
              <h2 id="camera-detail-title" className="text-lg font-semibold text-sc-text pr-2">
                {selected.name}
              </h2>
              <button
                type="button"
                onClick={closeModal}
                className="p-1 rounded-lg text-sc-text-muted hover:bg-sc-surface-alt hover:text-sc-text"
                aria-label="Close"
              >
                <X size={22} />
              </button>
            </div>

            <div className="p-4 space-y-4">
              {hasEdgePreview ? (
                <div className="space-y-2">
                  <div className="relative w-full aspect-video min-h-[200px] max-h-[min(65vh,520px)] overflow-hidden rounded-lg border border-sc-border bg-zinc-900">
                    <ModalLiveSnapshot
                      key={edgeSnapshotUrl(selected.id, tick)}
                      url={edgeSnapshotUrl(selected.id, tick)}
                      cameraId={selected.id}
                      edgeBase={edgeBase}
                      label={selected.name}
                    />
                  </div>
                  <p className="text-[11px] text-sc-text-muted leading-relaxed">
                    <strong className="text-sc-text-dim">Still dark?</strong> The edge returns a dark
                    &quot;Waiting for…&quot; image until the inference loop publishes frames — or the
                    camera <code className="text-sc-text-dim">id</code> here must match{' '}
                    <code className="text-sc-text-dim">edge/config/cameras.json</code>. Test:{' '}
                    <a
                      href={edgeSnapshotUrl(selected.id, tick)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sc-accent hover:underline break-all"
                    >
                      {edgeSnapshotUrl(selected.id, tick)}
                    </a>
                  </p>
                </div>
              ) : (
                <div
                  className="rounded-lg border border-sc-border bg-sc-surface-alt p-4 text-sm text-sc-text-muted"
                  data-testid="no-rtsp-explainer"
                >
                  <p className="mb-2">
                    <strong className="text-sc-text">No edge preview URL configured.</strong> RTSP streams are not
                    playable inside the browser. SudarshanChakra serves JPEG snapshots from the edge node (Flask GUI,
                    port 5000) while inference is running.
                  </p>
                  <p>
                    Add <code className="text-xs text-sc-text-dim">VITE_EDGE_SNAPSHOT_BASE</code> pointing at that
                    edge (reachable from your browser — same LAN or VPN), then restart the dashboard dev server.
                  </p>
                </div>
              )}

              <dl className="text-sm space-y-2">
                <div>
                  <dt className="text-sc-text-muted text-xs uppercase tracking-wide">Camera ID</dt>
                  <dd className="font-mono text-sc-text">{selected.id}</dd>
                </div>
                <div>
                  <dt className="text-sc-text-muted text-xs uppercase tracking-wide">Node</dt>
                  <dd className="font-mono text-sc-text">{selected.nodeId}</dd>
                </div>
                {selected.model ? (
                  <div>
                    <dt className="text-sc-text-muted text-xs uppercase tracking-wide">Model</dt>
                    <dd className="text-sc-text">{selected.model}</dd>
                  </div>
                ) : null}
                <div>
                  <dt className="text-sc-text-muted text-xs uppercase tracking-wide">RTSP (masked)</dt>
                  <dd className="font-mono text-xs text-sc-text-dim break-all">{maskRtspUrl(selected.rtspUrl)}</dd>
                </div>
              </dl>

              {hasEdgePreview ? (
                <a
                  href={`${edgeBase}/cameras`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 text-sm text-sc-accent hover:underline"
                >
                  <ExternalLink size={16} />
                  Open edge camera grid (Flask)
                </a>
              ) : null}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
