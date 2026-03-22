import { useCallback, useEffect, useState } from 'react';
import { Camera as CameraIcon, Loader2 } from 'lucide-react';
import { getEdgeSnapshotBase } from '@/lib/edgeSnapshot';

interface LiveCameraFeedProps {
  cameraId: string;
  className?: string;
  showPlaceholder?: boolean;
}

const RECONNECT_MS = 12_000;

/**
 * MJPEG live stream component. Uses `<img>` with the edge Flask
 * `/api/live/{cam}` endpoint which returns `multipart/x-mixed-replace`.
 * Reconnects periodically and on error; shows a short loading state.
 */
export default function LiveCameraFeed({
  cameraId,
  className = '',
  showPlaceholder = true,
}: LiveCameraFeedProps) {
  const edgeBase = getEdgeSnapshotBase();
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [streamKey, setStreamKey] = useState(0);

  const bumpStream = useCallback(() => {
    setLoading(true);
    setStreamKey((k) => k + 1);
  }, []);

  useEffect(() => {
    if (!edgeBase || failed) return undefined;
    const t = window.setInterval(bumpStream, RECONNECT_MS);
    return () => window.clearInterval(t);
  }, [edgeBase, failed, bumpStream]);

  if (!edgeBase || failed) {
    if (!showPlaceholder) return null;
    return (
      <div className={`flex items-center justify-center bg-sc-surface-alt text-sc-text-muted/20 ${className}`}>
        <CameraIcon size={48} />
      </div>
    );
  }

  const url = `${edgeBase}/api/live/${encodeURIComponent(cameraId)}?k=${streamKey}`;

  return (
    <div className={`relative bg-black ${className}`}>
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center bg-sc-surface-alt/90 z-10">
          <Loader2 className="animate-spin text-sc-accent" size={36} aria-hidden />
          <span className="sr-only">Loading live feed</span>
        </div>
      )}
      <img
        src={url}
        alt={`Live feed from ${cameraId}`}
        className="object-contain w-full h-full min-h-[120px]"
        onLoad={() => setLoading(false)}
        onError={() => {
          setLoading(false);
          setFailed(true);
          window.setTimeout(() => {
            setFailed(false);
            bumpStream();
          }, 3000);
        }}
      />
    </div>
  );
}
