import { clsx } from 'clsx';
import { format } from 'date-fns';
import type { CallLogRecord } from '@/api/mdm';

const typeColors: Record<string, string> = {
  incoming: 'bg-sc-success/20 text-sc-success border-sc-success/40',
  outgoing: 'bg-sc-info/20 text-sc-info border-sc-info/40',
  missed: 'bg-sc-critical/20 text-sc-critical border-sc-critical/40',
  rejected: 'bg-sc-high/20 text-sc-high border-sc-high/40',
};

function formatDuration(sec: number): string {
  if (sec <= 0) return '\u2014';
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

function formatCallTime(iso: string): string {
  try {
    return format(new Date(iso), 'MMM d, h:mm a');
  } catch {
    return iso;
  }
}

interface CallLogTableProps {
  records: CallLogRecord[];
  limit?: number;
}

export default function CallLogTable({ records, limit = 50 }: CallLogTableProps) {
  const display = records.slice(0, limit);

  if (display.length === 0) {
    return (
      <div className="rounded-xl border border-sc-border bg-sc-surface p-4">
        <div className="text-sm font-semibold text-sc-text mb-3">Call log</div>
        <div className="text-sc-text-muted text-xs py-6 text-center">No call records yet</div>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-sc-border bg-sc-surface p-4">
      <div className="text-sm font-semibold text-sc-text mb-3">Call log</div>
      <div className="grid gap-1">
        {display.map((c, i) => (
          <div
            key={i}
            className={clsx(
              'grid grid-cols-[minmax(120px,1fr)_80px_100px_70px_1fr] items-center px-2 py-1.5 rounded-md text-xs',
              i % 2 === 0 ? 'bg-sc-surface-alt' : 'bg-transparent',
            )}
          >
            <span className="text-sc-text-muted">{formatCallTime(c.callTimestamp)}</span>
            <span
              className={clsx(
                'inline-block px-2 py-0.5 rounded text-[11px] font-semibold border text-center',
                typeColors[c.callType] ?? 'bg-sc-surface-alt text-sc-text-dim border-sc-border',
              )}
            >
              {c.callType}
            </span>
            <span className="text-sc-text-dim font-mono">{c.phoneNumberMasked}</span>
            <span className="text-sc-text-dim">{formatDuration(c.durationSec)}</span>
            <span className="text-sc-text truncate">{c.contactName || '\u2014'}</span>
          </div>
        ))}
      </div>
      {records.length > limit && (
        <div className="text-center text-xs text-sc-text-muted mt-2">
          Showing {limit} of {records.length} calls
        </div>
      )}
    </div>
  );
}
