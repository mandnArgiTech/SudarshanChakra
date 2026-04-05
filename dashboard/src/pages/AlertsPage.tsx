import { useState } from 'react';
import { clsx } from 'clsx';
import { useAlerts, useAcknowledgeAlert, useResolveAlert } from '@/api/alerts';
import AlertTable from '@/components/AlertTable';
import type { Alert } from '@/types';

const fallbackAlerts: Alert[] = [
  {
    id: 'a1',
    nodeId: 'node-a',
    cameraId: 'CAM-03',
    zoneId: 'z1',
    zoneName: 'Pond Danger Zone',
    zoneType: 'danger',
    priority: 'critical',
    detectionClass: 'child',
    confidence: 0.92,
    bbox: null,
    snapshotUrl: null,
    thumbnailUrl: null,
    workerSuppressed: false,
    status: 'new',
    acknowledgedBy: null,
    acknowledgedAt: null,
    resolvedBy: null,
    resolvedAt: null,
    notes: null,
    metadata: JSON.stringify({ clip_path: 'a1.mp4' }),
    createdAt: new Date(Date.now() - 120000).toISOString(),
  },
  { id: 'a2', nodeId: 'node-a', cameraId: 'CAM-03', zoneId: 'z1', zoneName: 'Pond Danger Zone', zoneType: 'danger', priority: 'critical', detectionClass: 'person', confidence: 0.88, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'new', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 130000).toISOString() },
  { id: 'a3', nodeId: 'node-a', cameraId: 'CAM-01', zoneId: 'z2', zoneName: 'Farm Perimeter', zoneType: 'perimeter', priority: 'high', detectionClass: 'person', confidence: 0.85, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'new', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 480000).toISOString() },
  { id: 'a4', nodeId: 'node-b', cameraId: 'CAM-05', zoneId: 'z3', zoneName: 'Snake Zone Alpha', zoneType: 'hazard', priority: 'high', detectionClass: 'snake', confidence: 0.79, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'acknowledged', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 840000).toISOString() },
  { id: 'a5', nodeId: 'node-b', cameraId: 'CAM-07', zoneId: 'z4', zoneName: 'Pasture Area', zoneType: 'monitoring', priority: 'warning', detectionClass: 'cow', confidence: 0.91, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'acknowledged', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 1320000).toISOString() },
  { id: 'a6', nodeId: 'node-a', cameraId: 'CAM-02', zoneId: 'z5', zoneName: 'Storage Shed', zoneType: 'security', priority: 'high', detectionClass: 'fire', confidence: 0.73, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'resolved', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 3600000).toISOString() },
  { id: 'a7', nodeId: 'node-b', cameraId: 'CAM-04', zoneId: 'z2', zoneName: 'Farm Perimeter', zoneType: 'perimeter', priority: 'warning', detectionClass: 'scorpion', confidence: 0.67, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'false_positive', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 7200000).toISOString() },
];

const filters = ['all', 'critical', 'high', 'warning'] as const;

export default function AlertsPage() {
  const [priority, setPriority] = useState<string>('all');
  const { data: alertsPage } = useAlerts({ priority, size: 50 });
  const ackMutation = useAcknowledgeAlert();
  const resolveMutation = useResolveAlert();

  const alerts = alertsPage?.content ?? fallbackAlerts;
  const displayAlerts =
    priority === 'all' ? alerts : alerts.filter((a) => a.priority === priority);

  return (
    <div>
      <div className="flex gap-2 mb-5">
        {filters.map((f) => {
          const count = f === 'all' ? alerts.length : alerts.filter((a) => a.priority === f).length;
          return (
            <button
              key={f}
              onClick={() => setPriority(f)}
              className={clsx(
                'px-5 py-2 rounded-full border text-[13px] font-mono uppercase tracking-wider transition-colors',
                priority === f
                  ? 'border-sc-accent bg-sc-accent/10 text-sc-accent'
                  : 'border-sc-border text-sc-text-dim hover:bg-white/5',
              )}
            >
              {f} {f !== 'all' && `(${count})`}
            </button>
          );
        })}
      </div>

      <AlertTable
        alerts={displayAlerts}
        onAcknowledge={(id) => ackMutation.mutate(id)}
        onResolve={(id) => resolveMutation.mutate(id)}
      />
    </div>
  );
}
