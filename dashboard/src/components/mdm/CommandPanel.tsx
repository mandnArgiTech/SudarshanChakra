import { clsx } from 'clsx';
import { format } from 'date-fns';
import type { MdmDevice, MdmCommandRecord } from '@/api/mdm';

function parsePolicies(raw: MdmDevice['policies']): Record<string, boolean> {
  if (!raw) return {};
  if (typeof raw === 'object') return raw as Record<string, boolean>;
  try {
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

const policyLabels: Record<string, string> = {
  status_bar_disabled: 'Status bar disabled',
  safe_boot_blocked: 'Safe boot blocked',
  factory_reset_blocked: 'Factory reset blocked',
  wifi_config_locked: 'Wi-Fi config locked',
  mobile_data_forced: 'Mobile data forced',
  location_config_locked: 'Location config locked',
  camera_disabled: 'Camera disabled',
};

function statusColor(status: string): string {
  switch (status.toLowerCase()) {
    case 'executed':
    case 'applied':
    case 'success':
      return 'bg-sc-success/20 text-sc-success border-sc-success/40';
    case 'pending':
    case 'delivered':
      return 'bg-sc-warning/20 text-sc-warning border-sc-warning/40';
    case 'failed':
      return 'bg-sc-critical/20 text-sc-critical border-sc-critical/40';
    default:
      return 'bg-sc-surface-alt text-sc-text-dim border-sc-border';
  }
}

function formatCmdTime(iso: string): string {
  try {
    return format(new Date(iso), 'MMM d HH:mm');
  } catch {
    return iso;
  }
}

interface CommandPanelProps {
  device: MdmDevice;
  commands: MdmCommandRecord[];
}

export default function CommandPanel({ device, commands }: CommandPanelProps) {
  const policies = parsePolicies(device.policies);
  const policyEntries = Object.entries(policies);

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
      {/* Policies */}
      <div className="rounded-xl border border-sc-border bg-sc-surface p-4">
        <div className="text-sm font-semibold text-sc-text mb-3">Policies</div>
        {policyEntries.length === 0 ? (
          <div className="text-sc-text-muted text-xs py-3 text-center">No policies configured</div>
        ) : (
          policyEntries.map(([key, val]) => (
            <div
              key={key}
              className="flex justify-between py-1 border-b border-sc-border text-xs last:border-0"
            >
              <span className="text-sc-text-dim">{policyLabels[key] ?? key}</span>
              <span
                className={clsx(
                  'px-2 py-0.5 rounded text-[11px] font-semibold border',
                  val
                    ? 'bg-sc-success/20 text-sc-success border-sc-success/40'
                    : 'bg-sc-surface-alt text-sc-text-muted border-sc-border',
                )}
              >
                {val ? 'ON' : 'OFF'}
              </span>
            </div>
          ))
        )}
        {device.whitelistedApps?.length > 0 && (
          <div className="mt-2 text-[11px] text-sc-text-muted">
            Whitelisted: {device.whitelistedApps.join(', ')}
          </div>
        )}
      </div>

      {/* Command history */}
      <div className="rounded-xl border border-sc-border bg-sc-surface p-4">
        <div className="text-sm font-semibold text-sc-text mb-3">Command history</div>
        {commands.length === 0 ? (
          <div className="text-sc-text-muted text-xs py-3 text-center">No commands issued</div>
        ) : (
          commands.slice(0, 20).map((cmd) => (
            <div
              key={cmd.id}
              className="flex justify-between py-1 border-b border-sc-border text-xs last:border-0"
            >
              <span className="text-sc-text-muted">{formatCmdTime(cmd.issuedAt)}</span>
              <span className="text-sc-text-dim">{cmd.command}</span>
              <span
                className={clsx(
                  'px-2 py-0.5 rounded text-[11px] font-semibold border',
                  statusColor(cmd.status),
                )}
              >
                {cmd.status}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
