import { useNavigate } from 'react-router-dom';
import { Smartphone, Loader2 } from 'lucide-react';
import { clsx } from 'clsx';
import { formatDistanceToNow } from 'date-fns';
import { useMdmDevices } from '@/api/mdm';
import type { MdmDevice } from '@/api/mdm';

const statusColor: Record<string, string> = {
  active: 'bg-sc-success',
  pending: 'bg-sc-warning',
  locked: 'bg-sc-critical',
  wiped: 'bg-sc-text-muted',
  decommissioned: 'bg-sc-text-muted',
};

const statusBadge: Record<string, string> = {
  active: 'bg-sc-success/20 text-sc-success border-sc-success/40',
  pending: 'bg-sc-warning/20 text-sc-warning border-sc-warning/40',
  locked: 'bg-sc-critical/20 text-sc-critical border-sc-critical/40',
  wiped: 'bg-sc-text-muted/20 text-sc-text-muted border-sc-text-muted/40',
  decommissioned: 'bg-sc-text-muted/20 text-sc-text-muted border-sc-text-muted/40',
};

function lastSeenText(device: MdmDevice): string {
  if (device.lastHeartbeat) {
    try {
      return formatDistanceToNow(new Date(device.lastHeartbeat), { addSuffix: true });
    } catch {
      return device.lastHeartbeat;
    }
  }
  return 'Never';
}

function isStale(device: MdmDevice): boolean {
  if (!device.lastHeartbeat) return true;
  try {
    return Date.now() - new Date(device.lastHeartbeat).getTime() > 10 * 60 * 1000;
  } catch {
    return true;
  }
}

const mockDevices: MdmDevice[] = [
  {
    id: 'mock-1',
    farmId: '',
    deviceName: "Ramesh's Phone",
    androidId: 'abc123',
    model: 'Samsung A14',
    osVersion: 'Android 14',
    appVersion: '2.1.0',
    isDeviceOwner: true,
    isLockTaskActive: true,
    whitelistedApps: ['SC', 'WhatsApp', 'YouTube', 'Maps', 'Dialer'],
    policies: { status_bar_disabled: true, factory_reset_blocked: true },
    lastHeartbeat: new Date().toISOString(),
    status: 'active',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'mock-2',
    farmId: '',
    deviceName: "Suresh's Phone",
    androidId: 'def456',
    model: 'Redmi Note 12',
    osVersion: 'Android 13',
    appVersion: '2.1.0',
    isDeviceOwner: true,
    isLockTaskActive: true,
    whitelistedApps: [],
    policies: null,
    lastHeartbeat: new Date(Date.now() - 15 * 60_000).toISOString(),
    status: 'active',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'mock-3',
    farmId: '',
    deviceName: "Priya's Phone",
    androidId: 'ghi789',
    model: 'Moto G54',
    osVersion: 'Android 14',
    appVersion: undefined,
    isDeviceOwner: false,
    isLockTaskActive: false,
    whitelistedApps: [],
    policies: null,
    status: 'pending',
    createdAt: new Date().toISOString(),
  },
];

export default function MdmDeviceListPage() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useMdmDevices();
  const devices = data ?? mockDevices;
  const activeCount = devices.filter((d) => d.status === 'active').length;
  const pendingCount = devices.filter((d) => d.status === 'pending').length;

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Smartphone className="text-sc-accent" size={28} />
          <div>
            <h1 className="text-xl font-bold text-sc-text">Device Management</h1>
            <p className="text-sc-text-muted text-sm">
              {devices.length} device{devices.length !== 1 ? 's' : ''} &middot; {activeCount} active
              {pendingCount > 0 && <> &middot; {pendingCount} pending</>}
            </p>
          </div>
        </div>
        <button className="bg-sc-accent text-black px-4 py-2 rounded-lg text-sm font-medium hover:opacity-90 transition-opacity">
          + Register Device
        </button>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-sc-text-dim">
          <Loader2 className="animate-spin" size={20} />
          Loading devices...
        </div>
      )}

      {error && !data && (
        <div className="rounded-lg border border-sc-critical/40 bg-sc-critical/10 text-sc-critical text-sm p-4">
          Could not load MDM devices. Showing mock data.
        </div>
      )}

      {devices.length === 0 && !isLoading && (
        <div className="rounded-xl border border-sc-border bg-sc-surface p-8 text-center">
          <Smartphone className="mx-auto text-sc-text-muted mb-3" size={40} />
          <div className="text-sc-text font-semibold mb-1">No devices registered</div>
          <div className="text-sc-text-muted text-sm">
            Register your first device to start managing it remotely.
          </div>
        </div>
      )}

      <div className="grid gap-2">
        {devices.map((d) => (
          <div
            key={d.id}
            className="rounded-xl border border-sc-border bg-sc-surface p-4 hover:border-sc-accent/30 transition-colors cursor-pointer"
            onClick={() => navigate(`/mdm/devices/${d.id}`)}
          >
            <div className="flex justify-between items-start">
              <div className="flex items-center gap-3">
                <span
                  className={clsx(
                    'w-2.5 h-2.5 rounded-full shrink-0',
                    d.status === 'active' && !isStale(d)
                      ? statusColor.active
                      : d.status === 'active' && isStale(d)
                        ? 'bg-sc-warning'
                        : statusColor[d.status] ?? 'bg-sc-text-muted',
                  )}
                  style={{
                    boxShadow:
                      d.status === 'active' && !isStale(d)
                        ? '0 0 8px rgba(34,197,94,0.4)'
                        : undefined,
                  }}
                />
                <div>
                  <div className="text-[15px] font-semibold text-sc-text">{d.deviceName}</div>
                  <div className="text-xs text-sc-text-dim">
                    {[d.model, d.osVersion, d.appVersion ? `v${d.appVersion}` : null]
                      .filter(Boolean)
                      .join(' \u00b7 ')}
                  </div>
                </div>
              </div>
              <div className="flex gap-1.5 items-center flex-wrap justify-end">
                {d.isDeviceOwner && (
                  <span className="px-2.5 py-0.5 rounded-md text-[11px] font-semibold border bg-sc-accent/20 text-sc-accent border-sc-accent/40">
                    Kiosk ON
                  </span>
                )}
                {d.isLockTaskActive && (
                  <span className="px-2.5 py-0.5 rounded-md text-[11px] font-semibold border bg-sc-info/20 text-sc-info border-sc-info/40">
                    Lock Task
                  </span>
                )}
                <span
                  className={clsx(
                    'px-2.5 py-0.5 rounded-md text-[11px] font-semibold border',
                    statusBadge[d.status] ?? 'bg-sc-surface-alt text-sc-text-dim border-sc-border',
                  )}
                >
                  {d.status}
                </span>
              </div>
            </div>

            {d.status === 'active' && (
              <div className="mt-3 flex justify-between items-center">
                <div className="flex gap-4 text-xs text-sc-text-dim flex-wrap">
                  <span>Last seen: {lastSeenText(d)}</span>
                  {d.lastLatitude != null && d.lastLongitude != null && (
                    <span>
                      {d.lastLatitude.toFixed(2)}&deg;N, {d.lastLongitude.toFixed(2)}&deg;E
                    </span>
                  )}
                </div>
                <div className="flex gap-1">
                  <button
                    className="bg-sc-surface-alt border border-sc-border text-sc-text text-[11px] px-3 py-1.5 rounded-lg hover:bg-white/5 transition-colors"
                    onClick={(e) => {
                      e.stopPropagation();
                      navigate(`/mdm/devices/${d.id}`);
                    }}
                  >
                    View Details
                  </button>
                </div>
              </div>
            )}

            {d.status === 'pending' && (
              <div className="mt-3 p-2.5 bg-sc-warning/10 border border-dashed border-sc-warning/30 rounded-lg text-xs text-sc-warning">
                Not provisioned. Run:{' '}
                <code className="font-mono text-[10px]">
                  adb shell dpm set-device-owner com.sudarshanchakra/.mdm.SudarshanDeviceAdminReceiver
                </code>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
