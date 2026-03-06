import { clsx } from 'clsx';
import { formatDistanceToNow } from 'date-fns';
import { priorityColor, statusColor } from '@/utils/priorities';
import type { Alert } from '@/types';

interface AlertCardProps {
  alert: Alert;
  onClick?: () => void;
}

export default function AlertCard({ alert, onClick }: AlertCardProps) {
  const pColor = priorityColor(alert.priority);
  const sColor = statusColor(alert.status);
  const isNew = alert.status === 'new';

  return (
    <div
      onClick={onClick}
      className={clsx(
        'flex items-center gap-3 px-4 py-3 rounded-lg mb-1.5 cursor-pointer transition-colors border',
        isNew ? 'border-opacity-20 bg-opacity-5' : 'border-sc-border bg-transparent hover:bg-white/5',
      )}
      style={{
        backgroundColor: isNew ? `${pColor}11` : undefined,
        borderColor: isNew ? `${pColor}33` : undefined,
      }}
    >
      <div
        className="w-2.5 h-2.5 rounded-full flex-shrink-0"
        style={{
          backgroundColor: pColor,
          boxShadow: isNew ? `0 0 8px ${pColor}88` : 'none',
        }}
      />

      <div className="flex-1 min-w-0">
        <div className="text-sc-text text-sm font-semibold truncate">
          {alert.detectionClass.toUpperCase()} detected — {alert.zoneName}
        </div>
        <div className="text-sc-text-muted text-xs font-mono truncate">
          {alert.cameraId} · {alert.nodeId} · {(alert.confidence * 100).toFixed(0)}% ·{' '}
          {formatDistanceToNow(new Date(alert.createdAt), { addSuffix: true })}
        </div>
      </div>

      <div
        className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold uppercase tracking-wide font-mono flex-shrink-0"
        style={{
          backgroundColor: `${sColor}22`,
          color: sColor,
          border: `1px solid ${sColor}44`,
        }}
      >
        {alert.status.replace('_', ' ')}
      </div>
    </div>
  );
}
