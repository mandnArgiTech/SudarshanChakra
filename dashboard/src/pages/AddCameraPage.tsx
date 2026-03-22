import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Camera, Save, Wifi } from 'lucide-react';
import { getEdgeSnapshotBase } from '@/lib/edgeSnapshot';

export default function AddCameraPage() {
  const navigate = useNavigate();
  const edgeBase = getEdgeSnapshotBase();

  const [form, setForm] = useState({
    id: '',
    name: '',
    nodeId: '',
    sourceType: 'rtsp',
    rtspUrl: '',
    sourceUrl: '',
    fpsTarget: 2.5,
    resolution: '640x480',
    hasPtz: false,
    recordingEnabled: true,
    onvifHost: '',
    onvifPort: 80,
    onvifUser: '',
    onvifPass: '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [testResult, setTestResult] = useState('');

  const update = (key: string, value: unknown) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleTest = async () => {
    if (!edgeBase) {
      setTestResult('Edge URL not configured');
      return;
    }
    setTestResult('Testing...');
    try {
      const res = await fetch(`${edgeBase}/api/ptz/${encodeURIComponent(form.id)}/capabilities`);
      const data = await res.json();
      setTestResult(data.supported ? `Connected. PTZ: ${data.has_ptz ? 'Yes' : 'No'}` : 'Camera reachable (no PTZ)');
    } catch {
      setTestResult('Connection test failed');
    }
  };

  const handleSave = async () => {
    setSaving(true);
    setError('');
    try {
      const token = localStorage.getItem('sc_token');
      const res = await fetch('/api/v1/cameras', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          id: form.id,
          name: form.name,
          nodeId: form.nodeId,
          rtspUrl: form.sourceType === 'rtsp' ? form.rtspUrl : form.sourceUrl,
          sourceType: form.sourceType,
          sourceUrl: form.sourceType !== 'rtsp' ? form.sourceUrl : null,
          fpsTarget: form.fpsTarget,
          resolution: form.resolution,
          hasPtz: form.hasPtz,
          recordingEnabled: form.recordingEnabled,
          onvifHost: form.onvifHost || null,
          onvifPort: form.onvifPort || 80,
          onvifUser: form.onvifUser || null,
          onvifPass: form.onvifPass || null,
          enabled: true,
        }),
      });
      if (res.ok) {
        navigate('/cameras');
      } else {
        const data = await res.json().catch(() => ({}));
        setError(data.message || `Error ${res.status}`);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const InputField = ({
    label,
    value,
    onChange,
    type = 'text',
    placeholder = '',
    required = false,
  }: {
    label: string;
    value: string | number;
    onChange: (v: string) => void;
    type?: string;
    placeholder?: string;
    required?: boolean;
  }) => (
    <div>
      <label className="text-xs text-sc-text-muted block mb-1">
        {label} {required && <span className="text-sc-critical">*</span>}
      </label>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full px-3 py-2 text-sm bg-sc-surface-alt border border-sc-border rounded-lg text-sc-text"
        required={required}
      />
    </div>
  );

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate('/cameras')}
          className="p-2 rounded-lg text-sc-text-muted hover:bg-sc-surface-alt hover:text-sc-text"
        >
          <ArrowLeft size={20} />
        </button>
        <Camera size={24} className="text-sc-accent" />
        <h1 className="text-xl font-semibold text-sc-text">Add Camera</h1>
      </div>

      <div className="bg-sc-surface border border-sc-border rounded-xl p-6 space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <InputField label="Camera ID" value={form.id} onChange={(v) => update('id', v)} placeholder="cam-05" required />
          <InputField label="Camera Name" value={form.name} onChange={(v) => update('name', v)} placeholder="Barn Camera" required />
          <InputField label="Edge Node ID" value={form.nodeId} onChange={(v) => update('nodeId', v)} placeholder="edge-node-a" required />
          <div>
            <label className="text-xs text-sc-text-muted block mb-1">Source Type</label>
            <select
              value={form.sourceType}
              onChange={(e) => update('sourceType', e.target.value)}
              className="w-full px-3 py-2 text-sm bg-sc-surface-alt border border-sc-border rounded-lg text-sc-text"
            >
              <option value="rtsp">RTSP</option>
              <option value="file">Video File</option>
              <option value="http">HTTP Stream</option>
            </select>
          </div>
        </div>

        {form.sourceType === 'rtsp' ? (
          <InputField label="RTSP URL" value={form.rtspUrl} onChange={(v) => update('rtspUrl', v)}
            placeholder="rtsp://user:pass@192.168.1.201:554/stream2" required />
        ) : (
          <InputField label="Source URL / Path" value={form.sourceUrl} onChange={(v) => update('sourceUrl', v)}
            placeholder={form.sourceType === 'file' ? '/data/uploads/video.mp4' : 'http://server/stream.mjpeg'} required />
        )}

        <div className="grid grid-cols-3 gap-4">
          <InputField label="FPS Target" value={form.fpsTarget} onChange={(v) => update('fpsTarget', parseFloat(v) || 2.5)} type="number" />
          <InputField label="Resolution" value={form.resolution} onChange={(v) => update('resolution', v)} placeholder="640x480" />
          <div className="flex flex-col justify-end gap-2">
            <label className="flex items-center gap-2 text-sm text-sc-text">
              <input type="checkbox" checked={form.hasPtz} onChange={(e) => update('hasPtz', e.target.checked)} />
              Has PTZ
            </label>
            <label className="flex items-center gap-2 text-sm text-sc-text">
              <input type="checkbox" checked={form.recordingEnabled} onChange={(e) => update('recordingEnabled', e.target.checked)} />
              Recording
            </label>
          </div>
        </div>

        {form.hasPtz && (
          <div className="grid grid-cols-2 gap-4 pt-2 border-t border-sc-border">
            <InputField label="ONVIF Host" value={form.onvifHost} onChange={(v) => update('onvifHost', v)} placeholder="192.168.1.201" />
            <InputField label="ONVIF Port" value={form.onvifPort} onChange={(v) => update('onvifPort', parseInt(v) || 80)} type="number" />
            <InputField label="ONVIF Username" value={form.onvifUser} onChange={(v) => update('onvifUser', v)} placeholder="admin" />
            <InputField label="ONVIF Password" value={form.onvifPass} onChange={(v) => update('onvifPass', v)} type="password" />
          </div>
        )}

        {testResult && (
          <p className={`text-xs ${testResult.includes('failed') ? 'text-sc-critical' : 'text-sc-success'}`}>
            {testResult}
          </p>
        )}

        {error && <p className="text-sm text-sc-critical">{error}</p>}

        <div className="flex gap-2 justify-end pt-2">
          <button
            type="button"
            onClick={handleTest}
            className="flex items-center gap-1 px-4 py-2 text-sm rounded-lg border border-sc-border text-sc-text-muted hover:text-sc-text"
          >
            <Wifi size={14} /> Test Connection
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving || !form.id || !form.name || !form.nodeId}
            className="flex items-center gap-1 px-4 py-2 text-sm rounded-lg bg-sc-accent/10 border border-sc-accent/30 text-sc-accent hover:bg-sc-accent/20 disabled:opacity-50"
          >
            <Save size={14} /> {saving ? 'Saving...' : 'Save Camera'}
          </button>
        </div>
      </div>
    </div>
  );
}
