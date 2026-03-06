import { clsx } from 'clsx';
import { Camera as CameraIcon } from 'lucide-react';
import { useCameras } from '@/api/devices';
import type { Camera } from '@/types';

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

export default function CamerasPage() {
  const { data: cameras } = useCameras();
  const cameraList = cameras ?? fallbackCameras;

  return (
    <div className="grid grid-cols-4 gap-3">
      {cameraList.map((cam) => {
        const isAlert = cam.status === 'alert';
        const isOffline = cam.status === 'offline';

        return (
          <div
            key={cam.id}
            className={clsx(
              'bg-sc-surface rounded-xl overflow-hidden border cursor-pointer hover:border-sc-accent/30 transition-colors',
              isAlert ? 'border-sc-critical/40' : 'border-sc-border',
            )}
          >
            <div
              className={clsx(
                'h-[140px] flex items-center justify-center relative',
                isOffline
                  ? 'bg-sc-surface-alt'
                  : 'bg-gradient-to-br from-sc-surface-alt to-[#1a2a3a]',
              )}
            >
              <CameraIcon size={40} className="text-sc-text-muted/20" />

              {isAlert && (
                <div className="absolute top-2 right-2 px-2 py-0.5 bg-sc-critical rounded text-[10px] text-white font-bold font-mono">
                  ALERT
                </div>
              )}

              <div className="absolute top-2 left-2 px-2 py-0.5 bg-black/60 rounded text-[10px] text-sc-text-dim font-mono">
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
                {cam.id} · Node {cam.nodeId.replace('node-', '').toUpperCase()}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
