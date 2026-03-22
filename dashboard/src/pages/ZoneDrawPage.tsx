import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import ZoneDrawer, { type ZonePayload } from '@/components/ZoneDrawer';
import { getEdgeSnapshotBase, edgeSnapshotUrl } from '@/lib/edgeSnapshot';

export default function ZoneDrawPage() {
  const { cameraId } = useParams<{ cameraId: string }>();
  const navigate = useNavigate();
  const edgeBase = getEdgeSnapshotBase();
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!edgeBase) return;
    const id = setInterval(() => setTick((t) => t + 1), 5000);
    return () => clearInterval(id);
  }, [edgeBase]);

  const snapUrl = cameraId && edgeBase ? edgeSnapshotUrl(cameraId, tick) : '';

  const handleSave = useCallback(
    async (zone: ZonePayload) => {
      if (!edgeBase || !cameraId) return;
      const res = await fetch(`${edgeBase}/api/zones/${encodeURIComponent(cameraId)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(zone),
      });
      const data = await res.json();
      if (data.success) {
        navigate('/cameras');
      } else {
        alert('Error saving zone: ' + (data.error || 'Unknown error'));
      }
    },
    [edgeBase, cameraId, navigate],
  );

  if (!cameraId) {
    return <p className="text-sc-text-muted p-4">No camera selected</p>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate('/cameras')}
          className="p-2 rounded-lg text-sc-text-muted hover:bg-sc-surface-alt hover:text-sc-text"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-xl font-semibold text-sc-text">
          Draw Zone: {cameraId}
        </h1>
      </div>

      {!edgeBase ? (
        <div className="rounded-lg border border-sc-border bg-sc-surface-alt p-4 text-sm text-sc-text-muted">
          Set <code className="text-xs text-sc-text-dim">VITE_EDGE_SNAPSHOT_BASE</code> to draw zones.
        </div>
      ) : (
        <ZoneDrawer
          cameraId={cameraId}
          snapshotUrl={snapUrl}
          onSave={handleSave}
          onCancel={() => navigate('/cameras')}
        />
      )}
    </div>
  );
}
