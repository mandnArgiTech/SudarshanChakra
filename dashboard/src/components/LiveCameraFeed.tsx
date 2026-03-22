import { useState } from 'react';
import { Camera as CameraIcon } from 'lucide-react';
import { getEdgeSnapshotBase } from '@/lib/edgeSnapshot';

interface LiveCameraFeedProps {
  cameraId: string;
  className?: string;
  showPlaceholder?: boolean;
}

/**
 * MJPEG live stream component. Uses `<img>` with the edge Flask
 * `/api/live/{cam}` endpoint which returns `multipart/x-mixed-replace`.
 * Falls back to a placeholder icon if the edge base URL is not set
 * or the stream fails.
 */
export default function LiveCameraFeed({ cameraId, className = '', showPlaceholder = true }: LiveCameraFeedProps) {
  const edgeBase = getEdgeSnapshotBase();
  const [failed, setFailed] = useState(false);

  if (!edgeBase || failed) {
    if (!showPlaceholder) return null;
    return (
      <div className={`flex items-center justify-center bg-sc-surface-alt text-sc-text-muted/20 ${className}`}>
        <CameraIcon size={48} />
      </div>
    );
  }

  const url = `${edgeBase}/api/live/${encodeURIComponent(cameraId)}`;

  return (
    <img
      src={url}
      alt={`Live feed from ${cameraId}`}
      className={`object-contain bg-black ${className}`}
      onError={() => setFailed(true)}
    />
  );
}
