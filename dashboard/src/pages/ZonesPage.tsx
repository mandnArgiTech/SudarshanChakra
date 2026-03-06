import { clsx } from 'clsx';
import { Hexagon, Shield, Eye } from 'lucide-react';
import { useZones } from '@/api/devices';
import type { Zone } from '@/types';

const fallbackZones: Zone[] = [
  { id: 'z-pond-danger', cameraId: 'CAM-03', name: 'Pond Danger Zone', zoneType: 'danger', priority: 'critical', targetClasses: ['child', 'person'], polygon: '[]', color: '#ef4444', enabled: true, suppressWithWorkerTag: true, dedupWindowSeconds: 30, createdAt: '', updatedAt: '' },
  { id: 'z-pond-warning', cameraId: 'CAM-03', name: 'Pond Warning Zone', zoneType: 'warning', priority: 'high', targetClasses: ['child'], polygon: '[]', color: '#f97316', enabled: true, suppressWithWorkerTag: true, dedupWindowSeconds: 60, createdAt: '', updatedAt: '' },
  { id: 'z-perimeter-front', cameraId: 'CAM-01', name: 'Farm Perimeter — Front', zoneType: 'perimeter', priority: 'high', targetClasses: ['person'], polygon: '[]', color: '#f97316', enabled: true, suppressWithWorkerTag: true, dedupWindowSeconds: 30, createdAt: '', updatedAt: '' },
  { id: 'z-storage', cameraId: 'CAM-02', name: 'Storage Shed', zoneType: 'security', priority: 'high', targetClasses: ['person', 'fire'], polygon: '[]', color: '#f97316', enabled: true, suppressWithWorkerTag: false, dedupWindowSeconds: 30, createdAt: '', updatedAt: '' },
  { id: 'z-perimeter-east', cameraId: 'CAM-04', name: 'East Perimeter', zoneType: 'perimeter', priority: 'warning', targetClasses: ['person', 'scorpion'], polygon: '[]', color: '#eab308', enabled: true, suppressWithWorkerTag: true, dedupWindowSeconds: 60, createdAt: '', updatedAt: '' },
  { id: 'z-snake', cameraId: 'CAM-05', name: 'Snake Zone Alpha', zoneType: 'hazard', priority: 'high', targetClasses: ['snake'], polygon: '[]', color: '#f97316', enabled: true, suppressWithWorkerTag: false, dedupWindowSeconds: 30, createdAt: '', updatedAt: '' },
  { id: 'z-cattle', cameraId: 'CAM-06', name: 'Cattle Pen Zone', zoneType: 'monitoring', priority: 'warning', targetClasses: ['cow', 'person'], polygon: '[]', color: '#eab308', enabled: true, suppressWithWorkerTag: true, dedupWindowSeconds: 120, createdAt: '', updatedAt: '' },
  { id: 'z-pasture', cameraId: 'CAM-07', name: 'Pasture Area', zoneType: 'monitoring', priority: 'warning', targetClasses: ['cow'], polygon: '[]', color: '#eab308', enabled: true, suppressWithWorkerTag: true, dedupWindowSeconds: 120, createdAt: '', updatedAt: '' },
];

const priorityColorMap: Record<string, string> = {
  critical: 'text-sc-critical border-sc-critical/30 bg-sc-critical/10',
  high: 'text-sc-high border-sc-high/30 bg-sc-high/10',
  warning: 'text-sc-warning border-sc-warning/30 bg-sc-warning/10',
};

export default function ZonesPage() {
  const { data: zones } = useZones();
  const zoneList = zones ?? fallbackZones;

  const grouped = zoneList.reduce<Record<string, Zone[]>>((acc, z) => {
    (acc[z.cameraId] = acc[z.cameraId] || []).push(z);
    return acc;
  }, {});

  return (
    <div className="space-y-6">
      {Object.entries(grouped).map(([cameraId, cameraZones]) => (
        <div key={cameraId}>
          <div className="flex items-center gap-2 mb-3">
            <Eye size={16} className="text-sc-text-muted" />
            <h3 className="text-sc-text-dim text-sm font-mono uppercase tracking-wider">
              {cameraId}
            </h3>
            <span className="text-sc-text-muted text-xs">
              ({cameraZones.length} zone{cameraZones.length !== 1 ? 's' : ''})
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {cameraZones.map((zone) => (
              <div
                key={zone.id}
                className="bg-sc-surface border border-sc-border rounded-xl p-4 hover:border-sc-accent/30 transition-colors"
              >
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <Hexagon size={18} style={{ color: zone.color }} />
                    <span className="text-sc-text text-sm font-semibold">{zone.name}</span>
                  </div>
                  <span
                    className={clsx(
                      'px-2 py-0.5 rounded-full text-[10px] font-bold uppercase font-mono border',
                      priorityColorMap[zone.priority] || 'text-sc-text-muted',
                    )}
                  >
                    {zone.priority}
                  </span>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sc-text-muted text-xs w-20">Type:</span>
                    <span className="text-sc-text-dim text-xs font-mono uppercase">{zone.zoneType}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-sc-text-muted text-xs w-20">Targets:</span>
                    <div className="flex flex-wrap gap-1">
                      {zone.targetClasses.map((cls) => (
                        <span
                          key={cls}
                          className="px-1.5 py-0.5 rounded bg-sc-surface-alt text-sc-text-dim text-[10px] font-mono border border-sc-border"
                        >
                          {cls}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-sc-text-muted text-xs w-20">Dedup:</span>
                    <span className="text-sc-text-dim text-xs font-mono">{zone.dedupWindowSeconds}s</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-sc-text-muted text-xs w-20">Worker:</span>
                    <div className="flex items-center gap-1">
                      <Shield size={12} className={zone.suppressWithWorkerTag ? 'text-sc-success' : 'text-sc-text-muted'} />
                      <span className={clsx('text-xs font-mono', zone.suppressWithWorkerTag ? 'text-sc-success' : 'text-sc-text-muted')}>
                        {zone.suppressWithWorkerTag ? 'Suppresses' : 'No suppress'}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
