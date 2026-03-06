import { useState } from 'react';
import { Siren } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { useTriggerSiren, useStopSiren, useSirenHistory } from '@/api/siren';
import SirenButton from '@/components/SirenButton';
import type { SirenAction } from '@/types';

const fallbackHistory: SirenAction[] = [
  { id: 'h1', triggeredBy: null, triggeredBySystem: true, targetNode: 'node-a', action: 'trigger', alertId: null, acknowledged: true, acknowledgedAt: null, sirenUrl: null, createdAt: new Date(Date.now() - 3600000 * 6).toISOString() },
  { id: 'h2', triggeredBy: null, triggeredBySystem: false, targetNode: 'all', action: 'trigger', alertId: null, acknowledged: true, acknowledgedAt: null, sirenUrl: null, createdAt: new Date(Date.now() - 3600000 * 24).toISOString() },
  { id: 'h3', triggeredBy: null, triggeredBySystem: true, targetNode: 'node-b', action: 'trigger', alertId: null, acknowledged: false, acknowledgedAt: null, sirenUrl: null, createdAt: new Date(Date.now() - 3600000 * 48).toISOString() },
];

export default function SirenPage() {
  const [sirenActive, setSirenActive] = useState(false);
  const triggerSiren = useTriggerSiren();
  const stopSiren = useStopSiren();
  const { data: historyPage } = useSirenHistory();

  const history = historyPage?.content ?? fallbackHistory;

  function handleToggle() {
    if (sirenActive) {
      stopSiren.mutate({ nodeId: 'all' }, { onSuccess: () => setSirenActive(false) });
    } else {
      triggerSiren.mutate({ nodeId: 'all' }, { onSuccess: () => setSirenActive(true) });
    }
  }

  function handleNodeSiren(nodeId: string) {
    triggerSiren.mutate({ nodeId });
  }

  return (
    <div>
      <div
        className={`rounded-2xl p-8 text-center mb-6 border-2 transition-all ${
          sirenActive
            ? 'bg-sc-critical/10 border-sc-critical'
            : 'bg-sc-surface border-sc-border'
        }`}
      >
        <div className="text-6xl mb-4">
          <Siren
            size={64}
            className={`mx-auto ${sirenActive ? 'text-sc-critical animate-pulse' : 'text-sc-text-muted'}`}
          />
        </div>
        <div
          className={`text-2xl font-extrabold uppercase tracking-[4px] font-mono mb-2 ${
            sirenActive ? 'text-sc-critical' : 'text-sc-text-dim'
          }`}
        >
          {sirenActive ? 'SIREN ACTIVE' : 'SIREN STANDBY'}
        </div>
        <p className="text-sc-text-muted text-[13px] mb-6">
          {sirenActive
            ? 'All farm sirens are currently sounding'
            : 'Press to activate emergency siren on selected nodes'}
        </p>

        <div className="flex gap-3 justify-center">
          <SirenButton
            active={sirenActive}
            label={sirenActive ? 'STOP SIREN' : 'TRIGGER ALL'}
            onClick={handleToggle}
            loading={triggerSiren.isPending || stopSiren.isPending}
          />
        </div>

        <div className="flex gap-3 justify-center mt-4">
          <SirenButton
            variant="secondary"
            label="Node A Only"
            onClick={() => handleNodeSiren('node-a')}
          />
          <SirenButton
            variant="secondary"
            label="Node B Only"
            onClick={() => handleNodeSiren('node-b')}
          />
        </div>
      </div>

      <div className="bg-sc-surface border border-sc-border rounded-xl p-5">
        <h3 className="text-sc-text text-base font-semibold mb-4">Siren Activation History</h3>
        {history.map((h) => (
          <div
            key={h.id}
            className="flex items-center gap-4 px-4 py-3 rounded-lg mb-1.5 bg-sc-surface-alt border border-sc-border"
          >
            <Siren size={20} className="text-sc-critical flex-shrink-0" />
            <div className="flex-1">
              <div className="text-sc-text text-sm">
                Siren {h.action} — Node {h.targetNode}
              </div>
              <div className="text-sc-text-muted text-xs font-mono">
                {formatDistanceToNow(new Date(h.createdAt), { addSuffix: true })} ·{' '}
                By: {h.triggeredBySystem ? 'System (Auto)' : 'Manual'} ·{' '}
                {h.acknowledged ? 'Acknowledged' : 'Pending'}
              </div>
            </div>
          </div>
        ))}

        {history.length === 0 && (
          <div className="text-center text-sc-text-muted py-8 text-sm">
            No siren activations recorded
          </div>
        )}
      </div>
    </div>
  );
}
