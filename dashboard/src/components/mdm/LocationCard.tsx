import { useEffect, useState } from 'react';
import { formatDistanceToNow } from 'date-fns';
import type { MdmDevice } from '@/api/mdm';
import { useSendCommand } from '@/api/mdm';
import 'leaflet/dist/leaflet.css';

const INTERVAL_OPTIONS = [
  { value: 30, label: '30 sec' },
  { value: 60, label: '1 min' },
  { value: 120, label: '2 min' },
  { value: 300, label: '5 min' },
  { value: 600, label: '10 min' },
];

interface LocationCardProps {
  device: MdmDevice;
}

export default function LocationCard({ device }: LocationCardProps) {
  const { lat, lng } = {
    lat: device.lastLatitude,
    lng: device.lastLongitude,
  };
  const hasLocation = lat != null && lng != null;
  const [interval, setInterval_] = useState(device.locationIntervalSec ?? 60);
  const sendCommand = useSendCommand();
  const [MapComponent, setMapComponent] = useState<React.ComponentType<{ lat: number; lng: number }> | null>(null);

  useEffect(() => {
    if (!hasLocation) return;
    import('./LeafletMap').then((mod) => setMapComponent(() => mod.default)).catch(() => {});
  }, [hasLocation]);

  function updateInterval(val: number) {
    setInterval_(val);
    sendCommand.mutate({
      deviceId: device.id,
      command: 'SET_POLICY',
      payload: { location_interval_sec: val },
    });
  }

  const lastSeenAgo = device.lastLocationAt
    ? formatDistanceToNow(new Date(device.lastLocationAt), { addSuffix: true })
    : null;

  return (
    <div className="rounded-xl border border-sc-border bg-sc-surface p-4">
      <div className="flex justify-between items-center mb-3">
        <h3 className="text-sm font-semibold text-sc-text">Location</h3>
        <div className="flex items-center gap-2 text-xs text-sc-text-dim">
          <span>Tracking every</span>
          <select
            value={interval}
            onChange={(e) => updateInterval(+e.target.value)}
            className="bg-sc-surface-alt border border-sc-border rounded px-2 py-1 text-sc-text text-xs"
          >
            {INTERVAL_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {hasLocation ? (
        <>
          <div className="w-full h-48 rounded-lg bg-sc-surface-alt border border-sc-border overflow-hidden">
            {MapComponent ? (
              <MapComponent lat={lat!} lng={lng!} />
            ) : (
              <div className="flex items-center justify-center h-full text-sc-text-muted text-xs">
                {lat!.toFixed(4)}&deg;N, {lng!.toFixed(4)}&deg;E
              </div>
            )}
          </div>
          <div className="flex justify-between mt-2 text-xs text-sc-text-dim">
            <span>
              Last: {lat!.toFixed(4)}&deg;N, {lng!.toFixed(4)}&deg;E
              {lastSeenAgo && <> &middot; {lastSeenAgo}</>}
            </span>
          </div>
        </>
      ) : (
        <div className="w-full h-48 rounded-lg bg-sc-surface-alt border border-sc-border flex items-center justify-center">
          <span className="text-sc-text-muted text-xs">No location data available</span>
        </div>
      )}
    </div>
  );
}
