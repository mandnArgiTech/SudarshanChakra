import { clsx } from 'clsx';
import { Users, Radio, Signal, SignalZero } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { useTags } from '@/api/devices';
import type { WorkerTag } from '@/types';

const fallbackTags: WorkerTag[] = [
  { tagId: 'TAG-001', workerName: 'Ravi Kumar', farmId: 'f1', role: 'Supervisor', phone: '+91 98765 43210', active: true, lastSeen: new Date(Date.now() - 60000).toISOString(), lastRssi: -45, lastNode: 'node-a', createdAt: '', updatedAt: '' },
  { tagId: 'TAG-002', workerName: 'Suresh Babu', farmId: 'f1', role: 'Farm Hand', phone: '+91 98765 43211', active: true, lastSeen: new Date(Date.now() - 120000).toISOString(), lastRssi: -62, lastNode: 'node-b', createdAt: '', updatedAt: '' },
  { tagId: 'TAG-003', workerName: 'Priya Sharma', farmId: 'f1', role: 'Veterinarian', phone: '+91 98765 43212', active: true, lastSeen: new Date(Date.now() - 300000).toISOString(), lastRssi: -58, lastNode: 'node-a', createdAt: '', updatedAt: '' },
  { tagId: 'TAG-004', workerName: 'Anil Reddy', farmId: 'f1', role: 'Security', phone: '+91 98765 43213', active: false, lastSeen: new Date(Date.now() - 86400000 * 2).toISOString(), lastRssi: null, lastNode: null, createdAt: '', updatedAt: '' },
];

function rssiToStrength(rssi: number | null): { label: string; color: string } {
  if (rssi === null) return { label: 'No signal', color: 'text-sc-text-muted' };
  if (rssi > -50) return { label: 'Excellent', color: 'text-sc-success' };
  if (rssi > -70) return { label: 'Good', color: 'text-sc-accent' };
  if (rssi > -85) return { label: 'Fair', color: 'text-sc-warning' };
  return { label: 'Weak', color: 'text-sc-critical' };
}

export default function WorkersPage() {
  const { data: tags } = useTags();
  const tagList = tags ?? fallbackTags;

  const activeTags = tagList.filter((t) => t.active);
  const inactiveTags = tagList.filter((t) => !t.active);

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-sc-success/10 border border-sc-success/30">
          <Users size={16} className="text-sc-success" />
          <span className="text-sc-success text-sm font-mono font-semibold">
            {activeTags.length} Active
          </span>
        </div>
        <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-sc-text-muted/10 border border-sc-text-muted/30">
          <Users size={16} className="text-sc-text-muted" />
          <span className="text-sc-text-muted text-sm font-mono font-semibold">
            {inactiveTags.length} Inactive
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {tagList.map((tag) => {
          const signal = rssiToStrength(tag.lastRssi);

          return (
            <div
              key={tag.tagId}
              className={clsx(
                'bg-sc-surface border rounded-xl p-5',
                tag.active ? 'border-sc-border' : 'border-sc-border/50 opacity-60',
              )}
            >
              <div className="flex items-start justify-between mb-3">
                <div>
                  <h3 className="text-sc-text text-base font-semibold">{tag.workerName}</h3>
                  <div className="text-sc-text-muted text-xs font-mono mt-0.5">
                    {tag.tagId} · {tag.role || 'Unassigned'}
                  </div>
                </div>
                <div
                  className={clsx(
                    'flex items-center gap-1.5 px-2 py-1 rounded-full text-xs font-mono font-semibold',
                    tag.active
                      ? 'bg-sc-success/10 text-sc-success border border-sc-success/30'
                      : 'bg-sc-text-muted/10 text-sc-text-muted border border-sc-text-muted/30',
                  )}
                >
                  <Radio size={12} />
                  {tag.active ? 'Active' : 'Inactive'}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="bg-sc-surface-alt rounded-lg p-3 border border-sc-border">
                  <div className="text-sc-text-muted text-[10px] uppercase tracking-wider font-mono mb-1">
                    Signal
                  </div>
                  <div className="flex items-center gap-1.5">
                    {tag.lastRssi !== null ? (
                      <Signal size={14} className={signal.color} />
                    ) : (
                      <SignalZero size={14} className="text-sc-text-muted" />
                    )}
                    <span className={`text-sm font-mono font-semibold ${signal.color}`}>
                      {tag.lastRssi !== null ? `${tag.lastRssi} dBm` : 'N/A'}
                    </span>
                  </div>
                  <div className={`text-[10px] font-mono ${signal.color}`}>{signal.label}</div>
                </div>

                <div className="bg-sc-surface-alt rounded-lg p-3 border border-sc-border">
                  <div className="text-sc-text-muted text-[10px] uppercase tracking-wider font-mono mb-1">
                    Last Seen
                  </div>
                  <div className="text-sc-text text-sm font-mono">
                    {tag.lastSeen
                      ? formatDistanceToNow(new Date(tag.lastSeen), { addSuffix: true })
                      : 'Never'}
                  </div>
                  <div className="text-sc-text-muted text-[10px] font-mono">
                    {tag.lastNode ? `Node ${tag.lastNode.replace('node-', '').toUpperCase()}` : '—'}
                  </div>
                </div>
              </div>

              {tag.phone && (
                <div className="mt-3 text-sc-text-dim text-xs font-mono">
                  📱 {tag.phone}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
