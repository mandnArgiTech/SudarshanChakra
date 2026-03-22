import { useCallback, useEffect, useRef, useState } from 'react';
import { Save, Trash2, Undo2, X } from 'lucide-react';

interface Point {
  x: number;
  y: number;
}

interface ZoneDrawerProps {
  cameraId: string;
  snapshotUrl: string;
  onSave: (zone: ZonePayload) => Promise<void>;
  onCancel: () => void;
}

export interface ZonePayload {
  id: string;
  name: string;
  type: string;
  priority: string;
  target_classes: string[];
  polygon: number[][];
  camera_id: string;
}

const ZONE_TYPES = [
  { value: 'intrusion', label: 'Intrusion Detection' },
  { value: 'zero_tolerance', label: 'Zero Tolerance (Pond/Danger)' },
  { value: 'livestock_containment', label: 'Livestock Containment' },
  { value: 'hazard', label: 'Hazard Zone (Snake/Fire)' },
];

const PRIORITIES = ['critical', 'high', 'warning'];

export default function ZoneDrawer({ cameraId, snapshotUrl, onSave, onCancel }: ZoneDrawerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);
  const [points, setPoints] = useState<Point[]>([]);
  const [name, setName] = useState('');
  const [type, setType] = useState('intrusion');
  const [priority, setPriority] = useState('high');
  const [targets, setTargets] = useState('person');
  const [saving, setSaving] = useState(false);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    const img = imgRef.current;
    if (!canvas || !img) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const container = canvas.parentElement;
    if (container) {
      canvas.width = container.clientWidth;
      canvas.height = container.clientHeight;
    }

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const scale = Math.min(canvas.width / img.naturalWidth, canvas.height / img.naturalHeight);
    const w = img.naturalWidth * scale;
    const h = img.naturalHeight * scale;
    const ox = (canvas.width - w) / 2;
    const oy = (canvas.height - h) / 2;
    ctx.drawImage(img, ox, oy, w, h);

    if (points.length > 0) {
      ctx.beginPath();
      ctx.moveTo(points[0].x, points[0].y);
      for (let i = 1; i < points.length; i++) {
        ctx.lineTo(points[i].x, points[i].y);
      }
      if (points.length > 2) ctx.closePath();
      ctx.fillStyle = 'rgba(245, 158, 11, 0.2)';
      ctx.fill();
      ctx.strokeStyle = '#f59e0b';
      ctx.lineWidth = 2;
      ctx.stroke();

      points.forEach((p, i) => {
        ctx.beginPath();
        ctx.arc(p.x, p.y, 5, 0, Math.PI * 2);
        ctx.fillStyle = '#f59e0b';
        ctx.fill();
        ctx.strokeStyle = '#0a0e17';
        ctx.lineWidth = 2;
        ctx.stroke();
        ctx.fillStyle = '#f59e0b';
        ctx.font = '11px monospace';
        ctx.fillText(String(i + 1), p.x + 8, p.y - 8);
      });
    }
  }, [points]);

  useEffect(() => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      imgRef.current = img;
      draw();
    };
    img.src = snapshotUrl;
  }, [snapshotUrl, draw]);

  useEffect(() => {
    draw();
    const onResize = () => draw();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [draw]);

  const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = Math.round(e.clientX - rect.left);
    const y = Math.round(e.clientY - rect.top);
    setPoints((prev) => [...prev, { x, y }]);
  };

  const toImageCoords = (): number[][] => {
    const canvas = canvasRef.current;
    const img = imgRef.current;
    if (!canvas || !img) return points.map((p) => [p.x, p.y]);
    const scale = Math.min(canvas.width / img.naturalWidth, canvas.height / img.naturalHeight);
    const ox = (canvas.width - img.naturalWidth * scale) / 2;
    const oy = (canvas.height - img.naturalHeight * scale) / 2;
    return points.map((p) => [
      Math.round((p.x - ox) / scale),
      Math.round((p.y - oy) / scale),
    ]);
  };

  const handleSave = async () => {
    if (!name.trim() || points.length < 3) return;
    setSaving(true);
    try {
      const zoneId = 'zone-' + name.toLowerCase().replace(/[^a-z0-9]/g, '-');
      await onSave({
        id: zoneId,
        name: name.trim(),
        type,
        priority,
        target_classes: targets.split(',').map((s) => s.trim()).filter(Boolean),
        polygon: toImageCoords(),
        camera_id: cameraId,
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-3">
      <div className="relative w-full aspect-video rounded-xl border border-sc-border overflow-hidden bg-zinc-900">
        <canvas
          ref={canvasRef}
          className="absolute inset-0 w-full h-full cursor-crosshair"
          onClick={handleCanvasClick}
        />
      </div>

      <p className="text-center text-xs text-sc-accent font-semibold">
        {points.length} points — click on image to add vertices (min 3)
      </p>

      <div className="flex gap-2 justify-center">
        <button
          type="button"
          onClick={() => setPoints((p) => p.slice(0, -1))}
          disabled={points.length === 0}
          className="flex items-center gap-1 px-3 py-1.5 text-xs rounded-lg bg-sc-surface-alt border border-sc-border text-sc-text-muted hover:text-sc-text disabled:opacity-40"
        >
          <Undo2 size={14} /> Undo
        </button>
        <button
          type="button"
          onClick={() => setPoints([])}
          disabled={points.length === 0}
          className="flex items-center gap-1 px-3 py-1.5 text-xs rounded-lg bg-sc-surface-alt border border-sc-border text-sc-text-muted hover:text-sc-text disabled:opacity-40"
        >
          <Trash2 size={14} /> Clear
        </button>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="text-xs text-sc-text-muted block mb-1">Zone Name</label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g., Cow Containment"
            className="w-full px-3 py-2 text-sm bg-sc-surface-alt border border-sc-border rounded-lg text-sc-text"
          />
        </div>
        <div>
          <label className="text-xs text-sc-text-muted block mb-1">Type</label>
          <select
            value={type}
            onChange={(e) => setType(e.target.value)}
            className="w-full px-3 py-2 text-sm bg-sc-surface-alt border border-sc-border rounded-lg text-sc-text"
          >
            {ZONE_TYPES.map((t) => (
              <option key={t.value} value={t.value}>{t.label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="text-xs text-sc-text-muted block mb-1">Priority</label>
          <select
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
            className="w-full px-3 py-2 text-sm bg-sc-surface-alt border border-sc-border rounded-lg text-sc-text"
          >
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="text-xs text-sc-text-muted block mb-1">Target Classes</label>
          <input
            type="text"
            value={targets}
            onChange={(e) => setTargets(e.target.value)}
            placeholder="person, cow"
            className="w-full px-3 py-2 text-sm bg-sc-surface-alt border border-sc-border rounded-lg text-sc-text"
          />
        </div>
      </div>

      <div className="flex gap-2 justify-end">
        <button
          type="button"
          onClick={onCancel}
          className="flex items-center gap-1 px-4 py-2 text-sm rounded-lg border border-sc-border text-sc-text-muted hover:text-sc-text"
        >
          <X size={14} /> Cancel
        </button>
        <button
          type="button"
          onClick={handleSave}
          disabled={saving || points.length < 3 || !name.trim()}
          className="flex items-center gap-1 px-4 py-2 text-sm rounded-lg bg-sc-accent/10 border border-sc-accent/30 text-sc-accent hover:bg-sc-accent/20 disabled:opacity-50"
        >
          <Save size={14} /> {saving ? 'Saving...' : 'Save Zone'}
        </button>
      </div>
    </div>
  );
}
