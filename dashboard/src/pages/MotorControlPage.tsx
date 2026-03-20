import { clsx } from 'clsx';
import { Droplets, RefreshCw } from 'lucide-react';
import { useWaterMotors, useMotorCommand } from '@/api/water';
import SirenButton from '@/components/SirenButton';

const fallbackMotors = [
  {
    id: 'demo-m1',
    farmId: 'f1',
    displayName: 'Main pump (demo)',
    deviceTag: 'farm/pump1',
    location: 'farm',
    controlType: 'relay',
    state: 'stopped',
    mode: 'auto',
    runSeconds: 0,
    status: 'unknown',
    autoMode: true,
    pumpOnPercent: 20,
    pumpOffPercent: 85,
  },
];

export default function MotorControlPage() {
  const { data: apiMotors, isError } = useWaterMotors();
  const cmd = useMotorCommand();
  const motors = !isError && apiMotors && apiMotors.length > 0 ? apiMotors : fallbackMotors;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-sc-text flex items-center gap-2">
          <Droplets className="text-sc-accent" size={28} />
          Pump &amp; motor control
        </h1>
        <p className="text-sc-text-muted text-sm mt-1 max-w-2xl">
          Send commands to device-service; MQTT publishes to each motor&apos;s{' '}
          <code className="text-xs bg-sc-surface-alt px-1 rounded">deviceTag/motor/command</code>. Matches
          Android MotorControlScreen flows.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {motors.map((m) => (
          <div
            key={m.id}
            className="bg-sc-surface border border-sc-border rounded-xl p-5 space-y-4"
          >
            <div className="flex justify-between items-start gap-2">
              <div>
                <h2 className="text-sc-text font-semibold text-lg">{m.displayName}</h2>
                <p className="text-sc-text-dim text-xs font-mono mt-0.5">{m.id}</p>
                {m.deviceTag && (
                  <p className="text-sc-text-muted text-[11px] font-mono mt-1">tag: {m.deviceTag}</p>
                )}
              </div>
              <span
                className={clsx(
                  'text-xs font-mono uppercase px-2 py-1 rounded border',
                  m.state === 'running'
                    ? 'border-sc-success/40 text-sc-success bg-sc-success/10'
                    : 'border-sc-border text-sc-text-dim',
                )}
              >
                {m.state} · {m.mode}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs font-mono text-sc-text-muted">
              <div>
                Auto: <span className="text-sc-text">{m.autoMode ? 'on' : 'off'}</span>
              </div>
              <div>
                Run: <span className="text-sc-text">{m.runSeconds}s</span>
              </div>
              <div>
                On &lt; {m.pumpOnPercent}%
              </div>
              <div>
                Off ≥ {m.pumpOffPercent}%
              </div>
            </div>

            <div className="flex flex-col sm:flex-row gap-2">
              <SirenButton
                variant="secondary"
                label="Pump ON"
                loading={cmd.isPending}
                onClick={() => cmd.mutate({ motorId: m.id, command: 'pump_on' })}
              />
              <SirenButton
                variant="secondary"
                label="Pump OFF"
                loading={cmd.isPending}
                onClick={() => cmd.mutate({ motorId: m.id, command: 'pump_off' })}
              />
              <SirenButton
                variant="secondary"
                label="Auto"
                loading={cmd.isPending}
                onClick={() => cmd.mutate({ motorId: m.id, command: 'pump_auto' })}
              />
            </div>
          </div>
        ))}
      </div>

      {isError && (
        <p className="text-sc-warning text-sm flex items-center gap-2">
          <RefreshCw size={14} /> API unavailable — showing demo motors. Start device-service + gateway.
        </p>
      )}
    </div>
  );
}
