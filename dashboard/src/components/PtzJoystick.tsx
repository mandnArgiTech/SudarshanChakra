import { useCallback, useEffect, useState } from 'react';
import { ArrowUp, ArrowDown, ArrowLeft, ArrowRight, ZoomIn, ZoomOut, Save, Target } from 'lucide-react';
import { getEdgeSnapshotBase } from '@/lib/edgeSnapshot';

interface PtzJoystickProps {
  cameraId: string;
}

interface Preset {
  token: string;
  name: string;
}

interface Capabilities {
  supported: boolean;
  has_ptz?: boolean;
  can_pan?: boolean;
  can_tilt?: boolean;
  can_zoom?: boolean;
  device_info?: Record<string, string>;
}

const SPEED = 0.5;

export default function PtzJoystick({ cameraId }: PtzJoystickProps) {
  const edgeBase = getEdgeSnapshotBase();
  const [caps, setCaps] = useState<Capabilities | null>(null);
  const [presets, setPresets] = useState<Preset[]>([]);
  const [newPresetName, setNewPresetName] = useState('');
  const [moving, setMoving] = useState(false);

  const ptzUrl = useCallback(
    (path: string) => `${edgeBase}/api/ptz/${encodeURIComponent(cameraId)}/${path}`,
    [edgeBase, cameraId],
  );

  useEffect(() => {
    if (!edgeBase) return;
    fetch(ptzUrl('capabilities'))
      .then((r) => r.json())
      .then(setCaps)
      .catch(() => setCaps({ supported: false }));

    fetch(ptzUrl('presets'))
      .then((r) => r.json())
      .then(setPresets)
      .catch(() => setPresets([]));
  }, [edgeBase, ptzUrl]);

  const move = useCallback(
    async (pan: number, tilt: number, zoom: number) => {
      setMoving(true);
      try {
        await fetch(ptzUrl('move'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ pan, tilt, zoom }),
        });
      } catch { /* ignore */ }
    },
    [ptzUrl],
  );

  const stop = useCallback(async () => {
    setMoving(false);
    try {
      await fetch(ptzUrl('stop'), { method: 'POST' });
    } catch { /* ignore */ }
  }, [ptzUrl]);

  const gotoPreset = useCallback(
    async (token: string) => {
      try {
        await fetch(ptzUrl('preset/goto'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ token }),
        });
      } catch { /* ignore */ }
    },
    [ptzUrl],
  );

  const savePreset = useCallback(async () => {
    if (!newPresetName.trim()) return;
    try {
      await fetch(ptzUrl('preset/save'), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newPresetName.trim() }),
      });
      setNewPresetName('');
      const r = await fetch(ptzUrl('presets'));
      setPresets(await r.json());
    } catch { /* ignore */ }
  }, [ptzUrl, newPresetName]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement) return;
      switch (e.key) {
        case 'ArrowUp': move(0, SPEED, 0); break;
        case 'ArrowDown': move(0, -SPEED, 0); break;
        case 'ArrowLeft': move(-SPEED, 0, 0); break;
        case 'ArrowRight': move(SPEED, 0, 0); break;
        case '+': move(0, 0, SPEED); break;
        case '-': move(0, 0, -SPEED); break;
      }
    };
    const onKeyUp = (e: KeyboardEvent) => {
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', '+', '-'].includes(e.key)) {
        stop();
      }
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('keyup', onKeyUp);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('keyup', onKeyUp);
    };
  }, [move, stop]);

  if (!edgeBase) {
    return (
      <div className="text-sc-text-muted text-sm p-4">
        Set <code className="text-xs">VITE_EDGE_SNAPSHOT_BASE</code> to enable PTZ control.
      </div>
    );
  }

  if (caps && !caps.supported) {
    return (
      <div className="text-sc-text-muted text-sm p-4">
        PTZ not available for this camera.
      </div>
    );
  }

  const dirBtn = (
    IconCmp: typeof ArrowUp,
    label: string,
    pan = 0,
    tilt = 0,
    zoom = 0,
  ) => (
    <button
      type="button"
      key={label}
      className="w-12 h-12 flex items-center justify-center rounded-lg bg-sc-surface-alt border border-sc-border text-sc-text hover:bg-sc-accent/10 hover:border-sc-accent/40 active:bg-sc-accent/20 transition-colors"
      onPointerDown={() => move(pan, tilt, zoom)}
      onPointerUp={stop}
      onPointerLeave={stop}
      aria-label={label}
    >
      <IconCmp size={20} />
    </button>
  );

  return (
    <div className="space-y-4">
      {/* Directional pad */}
      <div className="flex flex-col items-center gap-1">
        {dirBtn(ArrowUp, 'Tilt up', 0, SPEED)}
        <div className="flex gap-1">
          {dirBtn(ArrowLeft, 'Pan left', -SPEED)}
          <div className="w-12 h-12 flex items-center justify-center rounded-lg bg-sc-surface border border-sc-border">
            <Target size={16} className="text-sc-text-muted" />
          </div>
          {dirBtn(ArrowRight, 'Pan right', SPEED)}
        </div>
        {dirBtn(ArrowDown, 'Tilt down', 0, -SPEED)}
      </div>

      {/* Zoom */}
      <div className="flex items-center justify-center gap-2">
        {dirBtn(ZoomOut, 'Zoom out', 0, 0, -SPEED)}
        <span className="text-xs text-sc-text-muted">Zoom</span>
        {dirBtn(ZoomIn, 'Zoom in', 0, 0, SPEED)}
      </div>

      {moving && (
        <p className="text-center text-xs text-sc-accent animate-pulse">Moving...</p>
      )}

      {/* Device info */}
      {caps?.device_info && (
        <div className="text-[11px] text-sc-text-muted space-y-0.5">
          {caps.device_info.model && <p>Model: {caps.device_info.model}</p>}
          {caps.device_info.manufacturer && <p>Manufacturer: {caps.device_info.manufacturer}</p>}
        </div>
      )}

      {/* Presets */}
      {presets.length > 0 && (
        <div>
          <h4 className="text-xs font-semibold text-sc-text mb-1">Presets</h4>
          <div className="flex flex-wrap gap-1">
            {presets.map((p) => (
              <button
                key={p.token}
                type="button"
                onClick={() => gotoPreset(p.token)}
                className="px-2 py-1 text-[11px] rounded bg-sc-surface-alt border border-sc-border text-sc-text-muted hover:text-sc-accent hover:border-sc-accent/30 transition-colors"
              >
                {p.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Save preset */}
      <div className="flex gap-1">
        <input
          type="text"
          value={newPresetName}
          onChange={(e) => setNewPresetName(e.target.value)}
          placeholder="Preset name..."
          className="flex-1 px-2 py-1.5 text-xs bg-sc-surface-alt border border-sc-border rounded-lg text-sc-text"
        />
        <button
          type="button"
          onClick={savePreset}
          disabled={!newPresetName.trim()}
          className="flex items-center gap-1 px-2 py-1.5 text-xs rounded-lg bg-sc-accent/10 border border-sc-accent/30 text-sc-accent hover:bg-sc-accent/20 disabled:opacity-50 transition-colors"
        >
          <Save size={12} /> Save
        </button>
      </div>

      <p className="text-[10px] text-sc-text-muted">
        Keyboard: Arrow keys to pan/tilt, +/- to zoom. Hold to move, release to stop.
      </p>
    </div>
  );
}
