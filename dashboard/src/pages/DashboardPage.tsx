import { Activity, Camera, BarChart3, Users } from 'lucide-react';
import { useAlerts } from '@/api/alerts';
import { useNodes } from '@/api/devices';
import { useTriggerSiren } from '@/api/siren';
import { useAlertWebSocket } from '@/hooks/useAlertWebSocket';
import AlertCard from '@/components/AlertCard';
import NodeStatusCard from '@/components/NodeStatusCard';
import SirenButton from '@/components/SirenButton';
import type { Alert, EdgeNode } from '@/types';

const fallbackAlerts: Alert[] = [
  { id: 'a1', nodeId: 'node-a', cameraId: 'CAM-03', zoneId: 'z1', zoneName: 'Pond Danger Zone', zoneType: 'danger', priority: 'critical', detectionClass: 'child', confidence: 0.92, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'new', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 120000).toISOString() },
  { id: 'a2', nodeId: 'node-a', cameraId: 'CAM-03', zoneId: 'z1', zoneName: 'Pond Danger Zone', zoneType: 'danger', priority: 'critical', detectionClass: 'person', confidence: 0.88, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'new', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 130000).toISOString() },
  { id: 'a3', nodeId: 'node-a', cameraId: 'CAM-01', zoneId: 'z2', zoneName: 'Farm Perimeter', zoneType: 'perimeter', priority: 'high', detectionClass: 'person', confidence: 0.85, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'new', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 480000).toISOString() },
  { id: 'a4', nodeId: 'node-b', cameraId: 'CAM-05', zoneId: 'z3', zoneName: 'Snake Zone Alpha', zoneType: 'hazard', priority: 'high', detectionClass: 'snake', confidence: 0.79, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'acknowledged', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 840000).toISOString() },
  { id: 'a5', nodeId: 'node-b', cameraId: 'CAM-07', zoneId: 'z4', zoneName: 'Pasture Area', zoneType: 'monitoring', priority: 'warning', detectionClass: 'cow', confidence: 0.91, bbox: null, snapshotUrl: null, thumbnailUrl: null, workerSuppressed: false, status: 'acknowledged', acknowledgedBy: null, acknowledgedAt: null, resolvedBy: null, resolvedAt: null, notes: null, metadata: null, createdAt: new Date(Date.now() - 1320000).toISOString() },
];

const fallbackNodes: EdgeNode[] = [
  { id: 'node-a', farmId: 'f1', displayName: 'Edge Node A', vpnIp: '10.8.0.10', localIp: '192.168.1.10', status: 'online', lastHeartbeat: new Date().toISOString(), hardwareInfo: JSON.stringify({ gpu_util: '34%' }), config: null, createdAt: '', updatedAt: '' },
  { id: 'node-b', farmId: 'f1', displayName: 'Edge Node B', vpnIp: '10.8.0.11', localIp: '192.168.1.11', status: 'online', lastHeartbeat: new Date().toISOString(), hardwareInfo: JSON.stringify({ gpu_util: '28%' }), config: null, createdAt: '', updatedAt: '' },
];

export default function DashboardPage() {
  const { data: alertsPage } = useAlerts({ size: 5 });
  const { data: nodes } = useNodes();
  const triggerSiren = useTriggerSiren();
  const { connected, liveAlerts } = useAlertWebSocket();

  const alerts = liveAlerts.length > 0 ? liveAlerts.slice(0, 5) : (alertsPage?.content ?? fallbackAlerts);
  const nodeList = nodes ?? fallbackNodes;

  const stats = [
    { label: 'Active Alerts', value: String(alertsPage?.totalElements ?? 7), color: '#ef4444', sub: '2 critical', icon: Activity },
    { label: 'Cameras Online', value: '8/8', color: '#22c55e', sub: 'All operational', icon: Camera },
    { label: 'Detections Today', value: '143', color: '#3b82f6', sub: '23 escalated', icon: BarChart3 },
    { label: 'Workers On-Site', value: '3', color: '#f59e0b', sub: 'Tags active', icon: Users },
  ];

  return (
    <div>
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4 mb-6">
        {stats.map((s) => (
          <div
            key={s.label}
            className="bg-sc-surface border border-sc-border rounded-xl p-5"
            style={{ borderTop: `3px solid ${s.color}` }}
          >
            <div className="flex items-center justify-between mb-1">
              <span className="text-sc-text-dim text-xs uppercase tracking-[1.5px] font-mono">
                {s.label}
              </span>
              <s.icon size={16} style={{ color: s.color }} />
            </div>
            <div
              className="text-4xl font-extrabold font-mono my-1"
              style={{ color: s.color }}
            >
              {s.value}
            </div>
            <div className="text-sc-text-muted text-xs">{s.sub}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[2fr_1fr] gap-4">
        <div className="bg-sc-surface border border-sc-border rounded-xl p-5">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-sc-text text-base font-semibold">Live Alert Feed</h3>
            <div className="flex items-center gap-1.5">
              <div
                className={`w-2 h-2 rounded-full ${connected ? 'bg-sc-critical animate-pulse-dot' : 'bg-sc-text-muted'}`}
              />
              <span className="text-sc-text-dim text-xs font-mono">
                {connected ? 'LIVE' : 'POLLING'}
              </span>
            </div>
          </div>
          {alerts.map((alert) => (
            <AlertCard key={alert.id} alert={alert} />
          ))}
        </div>

        <div className="flex flex-col gap-4">
          <div className="bg-sc-surface border border-sc-border rounded-xl p-5">
            <h3 className="text-sc-text text-base font-semibold mb-4">Node Status</h3>
            <div className="space-y-2">
              {nodeList.map((node) => (
                <NodeStatusCard key={node.id} node={node} />
              ))}
            </div>
          </div>

          <div className="bg-sc-surface border border-sc-border rounded-xl p-5">
            <h3 className="text-sc-text text-base font-semibold mb-4">Quick Siren Control</h3>
            <SirenButton
              label="TRIGGER SIREN — ALL NODES"
              onClick={() => triggerSiren.mutate({ nodeId: 'all' })}
              loading={triggerSiren.isPending}
            />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-2">
              <SirenButton
                variant="secondary"
                label="Node A"
                onClick={() => triggerSiren.mutate({ nodeId: 'node-a' })}
              />
              <SirenButton
                variant="secondary"
                label="Node B"
                onClick={() => triggerSiren.mutate({ nodeId: 'node-b' })}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
