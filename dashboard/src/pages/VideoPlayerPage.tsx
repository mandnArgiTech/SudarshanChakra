import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Calendar, HardDrive, Play, RefreshCw } from 'lucide-react';
import { getEdgeSnapshotBase } from '@/lib/edgeSnapshot';

interface RecordingDate {
  date: string;
  hours: { hour: string; segments: string[] }[];
}

export default function VideoPlayerPage() {
  const { cameraId } = useParams<{ cameraId: string }>();
  const navigate = useNavigate();
  const edgeBase = getEdgeSnapshotBase();

  const [dates, setDates] = useState<RecordingDate[]>([]);
  const [selectedDate, setSelectedDate] = useState('');
  const [selectedSegment, setSelectedSegment] = useState('');
  const [videoUrl, setVideoUrl] = useState('');
  const [storageStatus, setStorageStatus] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchRecordings = useCallback(async () => {
    if (!edgeBase || !cameraId) return;
    setLoading(true);
    try {
      const url = `${edgeBase}/api/recordings/${encodeURIComponent(cameraId)}${selectedDate ? `?date=${selectedDate}` : ''}`;
      const res = await fetch(url);
      const data = await res.json();
      setDates(data.dates || []);
    } catch {
      setDates([]);
    } finally {
      setLoading(false);
    }
  }, [edgeBase, cameraId, selectedDate]);

  useEffect(() => {
    fetchRecordings();
  }, [fetchRecordings]);

  useEffect(() => {
    if (!edgeBase) return;
    fetch(`${edgeBase}/api/storage/status`)
      .then((r) => r.json())
      .then(setStorageStatus)
      .catch(() => {});
  }, [edgeBase]);

  const playSegment = (date: string, hour: string, segment: string) => {
    if (!edgeBase || !cameraId) return;
    const url = `${edgeBase}/api/video/${encodeURIComponent(cameraId)}/${date}/${hour}/${segment}`;
    setVideoUrl(url);
    setSelectedSegment(segment);
  };

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
          Recordings: {cameraId}
        </h1>
        <button
          onClick={fetchRecordings}
          className="ml-auto p-2 rounded-lg text-sc-text-muted hover:bg-sc-surface-alt hover:text-sc-text"
          title="Refresh recordings"
        >
          <RefreshCw size={18} />
        </button>
      </div>

      {!edgeBase && (
        <div className="rounded-lg border border-sc-border bg-sc-surface-alt p-4 text-sm text-sc-text-muted">
          Set <code className="text-xs text-sc-text-dim">VITE_EDGE_SNAPSHOT_BASE</code> to access recordings.
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Video player */}
        <div className="lg:col-span-2">
          <div className="relative w-full aspect-video bg-zinc-900 rounded-xl border border-sc-border overflow-hidden">
            {videoUrl ? (
              <video
                key={videoUrl}
                src={videoUrl}
                controls
                autoPlay
                className="w-full h-full object-contain"
              />
            ) : (
              <div className="absolute inset-0 flex flex-col items-center justify-center text-sc-text-muted">
                <Play size={48} className="mb-2 opacity-30" />
                <p className="text-sm">Select a recording segment to play</p>
              </div>
            )}
          </div>

          {selectedSegment && (
            <p className="text-xs text-sc-text-muted mt-2 font-mono">
              Playing: {selectedSegment}
            </p>
          )}
        </div>

        {/* Sidebar: date/segment picker + storage */}
        <div className="space-y-4">
          {/* Date filter */}
          <div className="bg-sc-surface border border-sc-border rounded-xl p-4">
            <h3 className="text-sm font-semibold text-sc-text flex items-center gap-2 mb-3">
              <Calendar size={16} />
              Date Filter
            </h3>
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="w-full px-3 py-2 text-sm bg-sc-surface-alt border border-sc-border rounded-lg text-sc-text"
            />
          </div>

          {/* Recordings tree */}
          <div className="bg-sc-surface border border-sc-border rounded-xl p-4 max-h-[50vh] overflow-y-auto">
            <h3 className="text-sm font-semibold text-sc-text mb-3">Segments</h3>
            {loading && <p className="text-sc-text-muted text-xs">Loading...</p>}
            {!loading && dates.length === 0 && (
              <p className="text-sc-text-muted text-xs">No recordings found</p>
            )}
            {dates.map((d) => (
              <div key={d.date} className="mb-3">
                <p className="text-xs text-sc-accent font-semibold mb-1">{d.date}</p>
                {d.hours.map((h) => (
                  <div key={h.hour} className="ml-2 mb-2">
                    <p className="text-xs text-sc-text-dim font-mono">{h.hour}:00</p>
                    <div className="ml-2 flex flex-wrap gap-1 mt-1">
                      {h.segments.map((seg) => (
                        <button
                          key={seg}
                          onClick={() => playSegment(d.date, h.hour, seg)}
                          className={`px-2 py-1 text-[10px] rounded font-mono transition-colors ${
                            selectedSegment === seg
                              ? 'bg-sc-accent text-black'
                              : 'bg-sc-surface-alt text-sc-text-muted hover:text-sc-text hover:bg-sc-border'
                          }`}
                        >
                          {seg.replace('.mp4', '')}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            ))}
          </div>

          {/* Storage status */}
          {storageStatus && (
            <div className="bg-sc-surface border border-sc-border rounded-xl p-4">
              <h3 className="text-sm font-semibold text-sc-text flex items-center gap-2 mb-2">
                <HardDrive size={16} />
                Storage
              </h3>
              {storageStatus.pools && typeof storageStatus.pools === 'object' ? (
                Object.entries(storageStatus.pools as Record<string, Record<string, number>>).map(
                  ([path, info]) => (
                    <div key={path} className="text-xs text-sc-text-muted space-y-1 mb-2">
                      <p className="font-mono text-sc-text-dim">{path}</p>
                      <div className="w-full bg-sc-surface-alt rounded-full h-2">
                        <div
                          className="bg-sc-accent rounded-full h-2 transition-all"
                          style={{ width: `${Math.min(info.percent || 0, 100)}%` }}
                        />
                      </div>
                      <p>
                        {info.used_gb?.toFixed(1)} / {info.total_gb?.toFixed(1)} GB ({info.percent?.toFixed(1)}%)
                      </p>
                    </div>
                  ),
                )
              ) : (
                <p className="text-xs text-sc-text-muted">Storage data unavailable</p>
              )}
            </div>
          )}

          <Link
            to="/cameras"
            className="block text-center text-sm text-sc-accent hover:underline"
          >
            Back to cameras
          </Link>
        </div>
      </div>
    </div>
  );
}
