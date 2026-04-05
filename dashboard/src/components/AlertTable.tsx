import { useState } from 'react';
import { formatDistanceToNow } from 'date-fns';
import { ChevronDown, ChevronUp, Film, ImageIcon } from 'lucide-react';
import { priorityColor, statusColor } from '@/utils/priorities';
import { alertHasClipEvidence, edgeAlertClipUrl } from '@/lib/edgeSnapshot';
import type { Alert } from '@/types';

interface AlertTableProps {
  alerts: Alert[];
  onAcknowledge?: (id: string) => void;
  onResolve?: (id: string) => void;
}

function AlertEvidencePanel({ alert, clipUrl }: { alert: Alert; clipUrl: string }) {
  const hasClip = alertHasClipEvidence(alert.metadata);
  return (
    <div className="px-4 py-4 bg-sc-surface-alt/80 space-y-4">
      <div className="text-[11px] uppercase tracking-wider font-mono text-sc-text-muted">Evidence</div>
      <div className="flex flex-col lg:flex-row gap-4 flex-wrap">
        {alert.snapshotUrl && (
          <div className="space-y-2 min-w-0 flex-1 max-w-md">
            <div className="flex items-center gap-2 text-sc-text text-xs font-mono">
              <ImageIcon size={14} className="text-sc-text-muted shrink-0" />
              Snapshot
            </div>
            <a href={alert.snapshotUrl} target="_blank" rel="noopener noreferrer" className="block">
              <img
                src={alert.snapshotUrl}
                alt="Alert snapshot"
                className="rounded-lg border border-sc-border max-h-48 w-auto object-contain bg-black"
              />
            </a>
          </div>
        )}
        {hasClip && (
          <div className="space-y-2 min-w-0 flex-1 max-w-xl">
            <div className="flex items-center gap-2 text-sc-text text-xs font-mono">
              <Film size={14} className="text-sc-accent shrink-0" />
              Alert clip
            </div>
            <video
              controls
              preload="metadata"
              className="w-full rounded-lg border border-sc-border bg-black max-h-56"
              src={clipUrl}
            />
            <a
              href={clipUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sc-accent text-xs font-mono underline"
            >
              Open clip in new tab
            </a>
          </div>
        )}
      </div>
    </div>
  );
}

function AlertRowDesktop({
  alert,
  onAcknowledge,
  onResolve,
  expanded,
  onToggleEvidence,
}: {
  alert: Alert;
  onAcknowledge?: (id: string) => void;
  onResolve?: (id: string) => void;
  expanded: boolean;
  onToggleEvidence: () => void;
}) {
  const pColor = priorityColor(alert.priority);
  const sColor = statusColor(alert.status);
  const isNew = alert.status === 'new';
  const hasClip = alertHasClipEvidence(alert.metadata);
  const hasSnapshot = Boolean(alert.snapshotUrl?.trim());
  const showEvidenceToggle = hasClip || hasSnapshot;
  const clipUrl = edgeAlertClipUrl(alert.id);

  return (
    <div className="border-b border-sc-border last:border-0">
      <div
        className="hidden lg:grid grid-cols-[40px_100px_1fr_120px_80px_100px_100px_100px_120px] px-4 py-3.5 items-center"
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

        <div className="flex items-center gap-1">
          {showEvidenceToggle && (
            <button
              type="button"
              onClick={onToggleEvidence}
              className="flex items-center gap-1 px-2 py-1 rounded border border-sc-border text-sc-text-muted text-[10px] font-mono hover:bg-white/5 min-h-[44px] lg:min-h-0 touch-manipulation"
              aria-expanded={expanded}
            >
              {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
              {hasClip ? 'Clip' : 'Img'}
            </button>
          )}
        </div>

        <div className="flex gap-1.5 flex-wrap justify-end">
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
      {expanded && showEvidenceToggle && (
        <div className="hidden lg:block">
          <AlertEvidencePanel alert={alert} clipUrl={clipUrl} />
        </div>
      )}
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
  const hasClip = alertHasClipEvidence(alert.metadata);
  const hasSnapshot = Boolean(alert.snapshotUrl?.trim());
  const clipUrl = edgeAlertClipUrl(alert.id);
  const [open, setOpen] = useState(false);

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
      {(hasClip || hasSnapshot) && (
        <div className="space-y-2">
          <button
            type="button"
            onClick={() => setOpen((o) => !o)}
            className="w-full flex items-center justify-center gap-2 py-2 rounded-lg border border-sc-border text-sc-text-muted text-sm font-mono"
          >
            {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            Evidence
          </button>
          {open && <AlertEvidencePanel alert={alert} clipUrl={clipUrl} />}
        </div>
      )}
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
  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
    <div className="space-y-3 lg:space-y-0">
      <div className="hidden lg:block bg-sc-surface border border-sc-border rounded-xl overflow-hidden">
        <div className="grid grid-cols-[40px_100px_1fr_120px_80px_100px_100px_100px_120px] px-4 py-3 bg-sc-surface-alt border-b border-sc-border">
          {[
            '',
            'Priority',
            'Detection',
            'Camera',
            'Conf',
            'Status',
            'Time',
            'Evidence',
            'Actions',
          ].map((h) => (
            <div
              key={h}
              className="text-sc-text-muted text-[11px] uppercase tracking-[1.5px] font-mono"
            >
              {h}
            </div>
          ))}
        </div>

        {alerts.map((alert) => (
          <AlertRowDesktop
            key={alert.id}
            alert={alert}
            onAcknowledge={onAcknowledge}
            onResolve={onResolve}
            expanded={expandedId === alert.id}
            onToggleEvidence={() =>
              setExpandedId((id) => (id === alert.id ? null : alert.id))
            }
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
