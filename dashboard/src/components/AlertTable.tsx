import { formatDistanceToNow } from 'date-fns';
import { priorityColor, statusColor } from '@/utils/priorities';
import type { Alert } from '@/types';

interface AlertTableProps {
  alerts: Alert[];
  onAcknowledge?: (id: string) => void;
  onResolve?: (id: string) => void;
}

function AlertRowDesktop({
  alert,
  onAcknowledge,
  onResolve,
}: {
  alert: Alert;
  onAcknowledge?: (id: string) => void;
  onResolve?: (id: string) => void;
}) {
  const pColor = priorityColor(alert.priority);
  const sColor = statusColor(alert.status);
  const isNew = alert.status === 'new';

  return (
    <div
      className="hidden lg:grid grid-cols-[40px_100px_1fr_120px_80px_100px_100px_120px] px-4 py-3.5 border-b border-sc-border items-center"
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

      <div className="text-xs font-bold uppercase font-mono" style={{ color: pColor }}>
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

      <div className="flex gap-1.5 flex-wrap">
        {alert.status === 'new' && onAcknowledge && (
          <button
            type="button"
            onClick={() => onAcknowledge(alert.id)}
            className="px-2.5 py-1 rounded border border-sc-accent/30 text-sc-accent text-[11px] font-mono hover:bg-sc-accent/10 transition-colors min-h-[44px] lg:min-h-0 touch-manipulation"
          >
            ACK
          </button>
        )}
        {(alert.status === 'new' || alert.status === 'acknowledged') && onResolve && (
          <button
            type="button"
            onClick={() => onResolve(alert.id)}
            className="px-2.5 py-1 rounded border border-sc-text-muted/30 text-sc-text-dim text-[11px] font-mono hover:bg-white/5 transition-colors min-h-[44px] lg:min-h-0 touch-manipulation"
          >
            Resolve
          </button>
        )}
      </div>
    </div>
  );
}

function AlertCardMobile({
  alert,
  onAcknowledge,
  onResolve,
}: {
  alert: Alert;
  onAcknowledge?: (id: string) => void;
  onResolve?: (id: string) => void;
}) {
  const pColor = priorityColor(alert.priority);
  const sColor = statusColor(alert.status);
  const isNew = alert.status === 'new';

  return (
    <div
      className={`lg:hidden rounded-xl border border-sc-border p-4 space-y-3 touch-manipulation ${!isNew ? 'bg-sc-surface' : ''}`}
      style={
        isNew
          ? {
              backgroundColor: `${pColor}0d`,
            }
          : undefined
      }
    >
      <div className="flex items-center gap-2">
        <div
          className="w-2.5 h-2.5 rounded-full flex-shrink-0"
          style={{
            backgroundColor: pColor,
            boxShadow: isNew ? `0 0 6px ${pColor}66` : 'none',
          }}
        />
        <span className="text-xs font-bold uppercase font-mono" style={{ color: pColor }}>
          {alert.priority}
        </span>
        <span
          className="ml-auto inline-block px-2.5 py-0.5 rounded-full text-[10px] font-semibold uppercase font-mono"
          style={{
            backgroundColor: `${sColor}22`,
            color: sColor,
            border: `1px solid ${sColor}44`,
          }}
        >
          {alert.status.replace('_', ' ')}
        </span>
      </div>
      <div>
        <div className="text-sc-text font-semibold">
          {alert.detectionClass} — {alert.zoneName}
        </div>
        <div className="text-sc-text-muted text-xs font-mono mt-1">
          {alert.nodeId} · {alert.cameraId} · {(alert.confidence * 100).toFixed(0)}% conf
        </div>
      </div>
      <div className="text-sc-text-muted text-xs">
        {formatDistanceToNow(new Date(alert.createdAt), { addSuffix: true })}
      </div>
      <div className="flex gap-2 flex-wrap">
        {alert.status === 'new' && onAcknowledge && (
          <button
            type="button"
            onClick={() => onAcknowledge(alert.id)}
            className="flex-1 min-h-[48px] rounded-lg border border-sc-accent/40 text-sc-accent font-mono text-sm font-semibold active:scale-[0.98] transition-transform"
          >
            Acknowledge
          </button>
        )}
        {(alert.status === 'new' || alert.status === 'acknowledged') && onResolve && (
          <button
            type="button"
            onClick={() => onResolve(alert.id)}
            className="flex-1 min-h-[48px] rounded-lg border border-sc-border text-sc-text-dim font-mono text-sm active:scale-[0.98] transition-transform"
          >
            Resolve
          </button>
        )}
      </div>
    </div>
  );
}

export default function AlertTable({ alerts, onAcknowledge, onResolve }: AlertTableProps) {
  return (
    <div className="space-y-3 lg:space-y-0">
      <div className="hidden lg:block bg-sc-surface border border-sc-border rounded-xl overflow-hidden">
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

        {alerts.map((alert) => (
          <AlertRowDesktop
            key={alert.id}
            alert={alert}
            onAcknowledge={onAcknowledge}
            onResolve={onResolve}
          />
        ))}

        {alerts.length === 0 && (
          <div className="text-center text-sc-text-muted py-12 text-sm">No alerts found</div>
        )}
      </div>

      <div className="lg:hidden space-y-3">
        {alerts.map((alert) => (
          <AlertCardMobile
            key={alert.id}
            alert={alert}
            onAcknowledge={onAcknowledge}
            onResolve={onResolve}
          />
        ))}
        {alerts.length === 0 && (
          <div className="text-center text-sc-text-muted py-12 text-sm border border-sc-border rounded-xl">
            No alerts found
          </div>
        )}
      </div>
    </div>
  );
}
