import { formatDistanceToNow } from 'date-fns';
import { priorityColor, statusColor } from '@/utils/priorities';
import type { Alert } from '@/types';

interface AlertTableProps {
  alerts: Alert[];
  onAcknowledge?: (id: string) => void;
  onResolve?: (id: string) => void;
}

export default function AlertTable({ alerts, onAcknowledge, onResolve }: AlertTableProps) {
  return (
    <div className="bg-sc-surface border border-sc-border rounded-xl overflow-hidden">
      <div className="grid grid-cols-[40px_100px_1fr_120px_80px_100px_100px_120px] px-4 py-3 bg-sc-surface-alt border-b border-sc-border">
        {['', 'Priority', 'Detection', 'Camera', 'Conf', 'Status', 'Time', 'Actions'].map(
          (h) => (
            <div
              key={h}
              className="text-sc-text-muted text-[11px] uppercase tracking-[1.5px] font-mono"
            >
              {h}
            </div>
          ),
        )}
      </div>

      {alerts.map((alert) => {
        const pColor = priorityColor(alert.priority);
        const sColor = statusColor(alert.status);
        const isNew = alert.status === 'new';

        return (
          <div
            key={alert.id}
            className="grid grid-cols-[40px_100px_1fr_120px_80px_100px_100px_120px] px-4 py-3.5 border-b border-sc-border items-center"
            style={{
              backgroundColor: isNew ? `${pColor}08` : undefined,
            }}
          >
            <div>
              <div
                className="w-2.5 h-2.5 rounded-full"
                style={{
                  backgroundColor: pColor,
                  boxShadow: isNew ? `0 0 6px ${pColor}66` : 'none',
                }}
              />
            </div>

            <div
              className="text-xs font-bold uppercase font-mono"
              style={{ color: pColor }}
            >
              {alert.priority}
            </div>

            <div>
              <div className="text-sc-text text-sm font-semibold">
                {alert.detectionClass} — {alert.zoneName}
              </div>
              <div className="text-sc-text-muted text-[11px]">{alert.nodeId}</div>
            </div>

            <div className="text-sc-text-dim text-[13px] font-mono">{alert.cameraId}</div>

            <div className="text-sc-text text-[13px] font-mono">
              {(alert.confidence * 100).toFixed(0)}%
            </div>

            <div>
              <span
                className="inline-block px-2.5 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide font-mono"
                style={{
                  backgroundColor: `${sColor}22`,
                  color: sColor,
                  border: `1px solid ${sColor}44`,
                }}
              >
                {alert.status.replace('_', ' ')}
              </span>
            </div>

            <div className="text-sc-text-muted text-xs">
              {formatDistanceToNow(new Date(alert.createdAt), { addSuffix: true })}
            </div>

            <div className="flex gap-1.5">
              {alert.status === 'new' && onAcknowledge && (
                <button
                  onClick={() => onAcknowledge(alert.id)}
                  className="px-2.5 py-1 rounded border border-sc-accent/30 text-sc-accent text-[11px] font-mono hover:bg-sc-accent/10 transition-colors"
                >
                  ACK
                </button>
              )}
              {(alert.status === 'new' || alert.status === 'acknowledged') && onResolve && (
                <button
                  onClick={() => onResolve(alert.id)}
                  className="px-2.5 py-1 rounded border border-sc-text-muted/30 text-sc-text-dim text-[11px] font-mono hover:bg-white/5 transition-colors"
                >
                  Resolve
                </button>
              )}
            </div>
          </div>
        );
      })}

      {alerts.length === 0 && (
        <div className="text-center text-sc-text-muted py-12 text-sm">No alerts found</div>
      )}
    </div>
  );
}
