import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Loader2, ArrowLeft } from 'lucide-react';
import { clsx } from 'clsx';
import { format, subDays } from 'date-fns';
import {
  useMdmDevice,
  useDeviceUsage,
  useDeviceCalls,
  useDeviceScreenTime,
  useDeviceCommands,
  useSendCommand,
} from '@/api/mdm';
import ScreenTimeChart from '@/components/mdm/ScreenTimeChart';
import CallLogTable from '@/components/mdm/CallLogTable';
import CommandPanel from '@/components/mdm/CommandPanel';
import LocationCard from '@/components/mdm/LocationCard';

const statusBadge: Record<string, string> = {
  active: 'bg-sc-success/20 text-sc-success border-sc-success/40',
  pending: 'bg-sc-warning/20 text-sc-warning border-sc-warning/40',
  locked: 'bg-sc-critical/20 text-sc-critical border-sc-critical/40',
  wiped: 'bg-sc-text-muted/20 text-sc-text-muted border-sc-text-muted/40',
  decommissioned: 'bg-sc-text-muted/20 text-sc-text-muted border-sc-text-muted/40',
};

export default function MdmDeviceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const sendCommand = useSendCommand();

  const today = format(new Date(), 'yyyy-MM-dd');
  const sevenDaysAgo = format(subDays(new Date(), 7), 'yyyy-MM-dd');
  const twoDaysAgo = format(subDays(new Date(), 2), 'yyyy-MM-dd');

  const { data: device, isLoading } = useMdmDevice(id ?? '');
  const { data: usage } = useDeviceUsage(id ?? '', sevenDaysAgo, today);
  const { data: calls } = useDeviceCalls(id ?? '', twoDaysAgo, today);
  const { data: screenTime } = useDeviceScreenTime(id ?? '', sevenDaysAgo, today);
  const { data: commands } = useDeviceCommands(id ?? '');

  const [showWipeConfirm, setShowWipeConfirm] = useState(false);
  const [wipeInput, setWipeInput] = useState('');
  const [showOtaDialog, setShowOtaDialog] = useState(false);
  const [otaUrl, setOtaUrl] = useState('');

  if (isLoading || !device) {
    return (
      <div className="p-6 flex items-center gap-2 text-sc-text-dim">
        <Loader2 className="animate-spin" size={20} />
        Loading device...
      </div>
    );
  }

  function handleCommand(cmd: string, payload?: Record<string, unknown>) {
    sendCommand.mutate({ deviceId: device!.id, command: cmd, payload });
  }

  const infoItems = [
    device.model,
    device.osVersion,
    device.appVersion ? `v${device.appVersion}` : null,
    device.isDeviceOwner ? 'Kiosk ON' : null,
  ].filter(Boolean);

  const lastSeen = device.lastHeartbeat
    ? `Last seen: ${format(new Date(device.lastHeartbeat), 'MMM d, h:mm a')}`
    : 'Never seen';

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-center gap-3 mb-4">
        <button
          className="bg-sc-surface-alt border border-sc-border text-sc-text px-2.5 py-1.5 rounded-lg hover:bg-white/5 transition-colors text-sm"
          onClick={() => navigate('/mdm')}
        >
          <ArrowLeft size={16} />
        </button>
        <div className="flex-1 min-w-0">
          <div className="text-lg font-semibold text-sc-text truncate">{device.deviceName}</div>
          <div className="text-xs text-sc-text-dim">
            {infoItems.join(' \u00b7 ')} &middot; {lastSeen}
          </div>
        </div>
        <span
          className={clsx(
            'px-2.5 py-0.5 rounded-md text-[11px] font-semibold border shrink-0',
            statusBadge[device.status] ?? 'bg-sc-surface-alt text-sc-text-dim border-sc-border',
          )}
        >
          {device.status}
        </span>
      </div>

      {/* Quick actions */}
      <div className="flex flex-wrap gap-2 mb-4">
        <button
          className="bg-sc-success/20 text-sc-success border border-sc-success/40 px-3 py-1.5 rounded-lg text-xs font-medium hover:opacity-80 transition-opacity disabled:opacity-40"
          disabled={sendCommand.isPending}
          onClick={() => handleCommand('SYNC_TELEMETRY')}
        >
          Force Sync
        </button>
        <button
          className="bg-sc-accent text-black px-3 py-1.5 rounded-lg text-xs font-medium hover:opacity-80 transition-opacity"
          onClick={() => setShowOtaDialog(true)}
        >
          Push OTA
        </button>
        <button
          className="bg-sc-surface-alt border border-sc-border text-sc-text px-3 py-1.5 rounded-lg text-xs font-medium hover:bg-white/5 transition-colors disabled:opacity-40"
          disabled={sendCommand.isPending}
          onClick={() => handleCommand('LOCK_SCREEN')}
        >
          Lock Screen
        </button>
        <button
          className="bg-sc-critical/20 text-sc-critical border border-sc-critical/40 px-3 py-1.5 rounded-lg text-xs font-medium hover:opacity-80 transition-opacity"
          onClick={() => setShowWipeConfirm(true)}
        >
          Wipe Device
        </button>
      </div>

      {/* Screen time chart */}
      <div className="mb-3">
        <ScreenTimeChart records={usage ?? []} />
      </div>

      {/* Call log */}
      <div className="mb-3">
        <CallLogTable records={calls ?? []} />
      </div>

      {/* Location */}
      <div className="mb-3">
        <LocationCard device={device} />
      </div>

      {/* Policies + Command history */}
      <CommandPanel device={device} commands={commands ?? []} />

      {/* Screen time summary (from screentime records) */}
      {screenTime && screenTime.length > 0 && (
        <div className="mt-3 rounded-xl border border-sc-border bg-sc-surface p-4">
          <div className="text-sm font-semibold text-sc-text mb-2">Daily unlock stats</div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {screenTime.slice(0, 7).map((st) => (
              <div key={st.date} className="bg-sc-surface-alt rounded-lg p-2 text-center">
                <div className="text-xs text-sc-text-muted">{st.date}</div>
                <div className="text-sm font-semibold text-sc-text">
                  {(st.totalScreenTimeSec / 3600).toFixed(1)}h
                </div>
                <div className="text-[11px] text-sc-text-dim">{st.unlockCount} unlocks</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Wipe confirmation dialog */}
      {showWipeConfirm && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="bg-sc-surface border border-sc-border rounded-xl p-5 max-w-sm w-full">
            <h3 className="text-sc-text font-semibold mb-2">Wipe Device</h3>
            <p className="text-sc-text-dim text-sm mb-3">
              This action is <strong className="text-sc-critical">irreversible</strong>. Type the
              device name <strong className="text-sc-text">{device.deviceName}</strong> to confirm.
            </p>
            <input
              type="text"
              value={wipeInput}
              onChange={(e) => setWipeInput(e.target.value)}
              placeholder="Type device name..."
              className="w-full bg-sc-surface-alt border border-sc-border rounded-lg px-3 py-2 text-sm text-sc-text mb-3"
            />
            <div className="flex gap-2 justify-end">
              <button
                className="bg-sc-surface-alt border border-sc-border text-sc-text px-3 py-1.5 rounded-lg text-sm"
                onClick={() => {
                  setShowWipeConfirm(false);
                  setWipeInput('');
                }}
              >
                Cancel
              </button>
              <button
                className="bg-sc-critical text-white px-3 py-1.5 rounded-lg text-sm font-medium disabled:opacity-40"
                disabled={wipeInput !== device.deviceName || sendCommand.isPending}
                onClick={() => {
                  handleCommand('WIPE_DEVICE');
                  setShowWipeConfirm(false);
                  setWipeInput('');
                }}
              >
                Wipe
              </button>
            </div>
          </div>
        </div>
      )}

      {/* OTA dialog */}
      {showOtaDialog && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="bg-sc-surface border border-sc-border rounded-xl p-5 max-w-sm w-full">
            <h3 className="text-sc-text font-semibold mb-2">Push OTA Update</h3>
            <p className="text-sc-text-dim text-sm mb-3">Enter the APK download URL.</p>
            <input
              type="url"
              value={otaUrl}
              onChange={(e) => setOtaUrl(e.target.value)}
              placeholder="https://example.com/app-v2.2.0.apk"
              className="w-full bg-sc-surface-alt border border-sc-border rounded-lg px-3 py-2 text-sm text-sc-text mb-3"
            />
            <div className="flex gap-2 justify-end">
              <button
                className="bg-sc-surface-alt border border-sc-border text-sc-text px-3 py-1.5 rounded-lg text-sm"
                onClick={() => {
                  setShowOtaDialog(false);
                  setOtaUrl('');
                }}
              >
                Cancel
              </button>
              <button
                className="bg-sc-accent text-black px-3 py-1.5 rounded-lg text-sm font-medium disabled:opacity-40"
                disabled={!otaUrl.trim() || sendCommand.isPending}
                onClick={() => {
                  handleCommand('UPDATE_APP', { apk_url: otaUrl.trim() });
                  setShowOtaDialog(false);
                  setOtaUrl('');
                }}
              >
                Send Update
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
