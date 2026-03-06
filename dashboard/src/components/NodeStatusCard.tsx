import { clsx } from 'clsx';
import type { EdgeNode } from '@/types';

interface NodeStatusCardProps {
  node: EdgeNode;
}

export default function NodeStatusCard({ node }: NodeStatusCardProps) {
  const hwInfo = node.hardwareInfo ? JSON.parse(node.hardwareInfo) : {};

  return (
    <div className="p-3.5 bg-sc-surface-alt rounded-lg border border-sc-border">
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-2">
          <div
            className={clsx(
              'w-2 h-2 rounded-full',
              node.status === 'online' ? 'bg-sc-success' : 'bg-sc-text-muted',
            )}
          />
          <span className="text-sc-text text-sm font-semibold">{node.displayName}</span>
        </div>
        <span className="text-sc-text-muted text-[11px] font-mono">{node.vpnIp}</span>
      </div>
      <div className="flex gap-4 mt-2">
        <span className="text-sc-text-dim text-xs">
          GPU:{' '}
          <span className="text-sc-accent">{hwInfo.gpu_util ?? 'N/A'}</span>
        </span>
        <span className="text-sc-text-dim text-xs">
          Status:{' '}
          <span
            className={clsx(
              node.status === 'online' ? 'text-sc-success' : 'text-sc-critical',
            )}
          >
            {node.status}
          </span>
        </span>
      </div>
    </div>
  );
}
