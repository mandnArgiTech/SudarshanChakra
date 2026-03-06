import { Cpu, Wifi, WifiOff } from 'lucide-react';
import { clsx } from 'clsx';
import { formatDistanceToNow } from 'date-fns';
import { useNodes } from '@/api/devices';
import type { EdgeNode } from '@/types';

const fallbackNodes: EdgeNode[] = [
  { id: 'node-a', farmId: 'f1', displayName: 'Edge Node A', vpnIp: '10.8.0.10', localIp: '192.168.1.10', status: 'online', lastHeartbeat: new Date().toISOString(), hardwareInfo: JSON.stringify({ gpu: 'NVIDIA Jetson Orin Nano', gpu_util: '34%', gpu_temp: '52°C', cpu_util: '45%', ram_used: '3.2 GB / 8 GB', disk: '18 GB / 64 GB' }), config: null, createdAt: new Date(Date.now() - 86400000 * 30).toISOString(), updatedAt: new Date().toISOString() },
  { id: 'node-b', farmId: 'f1', displayName: 'Edge Node B', vpnIp: '10.8.0.11', localIp: '192.168.1.11', status: 'online', lastHeartbeat: new Date().toISOString(), hardwareInfo: JSON.stringify({ gpu: 'NVIDIA Jetson Orin Nano', gpu_util: '28%', gpu_temp: '48°C', cpu_util: '38%', ram_used: '2.8 GB / 8 GB', disk: '22 GB / 64 GB' }), config: null, createdAt: new Date(Date.now() - 86400000 * 30).toISOString(), updatedAt: new Date().toISOString() },
];

export default function DevicesPage() {
  const { data: nodes } = useNodes();
  const nodeList = nodes ?? fallbackNodes;

  return (
    <div className="space-y-4">
      {nodeList.map((node) => {
        const hw = node.hardwareInfo ? JSON.parse(node.hardwareInfo) : {};
        const isOnline = node.status === 'online';

        return (
          <div
            key={node.id}
            className="bg-sc-surface border border-sc-border rounded-xl p-6"
          >
            <div className="flex items-start justify-between mb-4">
              <div className="flex items-center gap-3">
                <div
                  className={clsx(
                    'w-10 h-10 rounded-lg flex items-center justify-center',
                    isOnline ? 'bg-sc-success/10' : 'bg-sc-text-muted/10',
                  )}
                >
                  <Cpu size={20} className={isOnline ? 'text-sc-success' : 'text-sc-text-muted'} />
                </div>
                <div>
                  <h3 className="text-sc-text text-lg font-semibold">{node.displayName}</h3>
                  <div className="text-sc-text-muted text-xs font-mono">
                    {node.id} · {hw.gpu || 'Unknown GPU'}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {isOnline ? (
                  <Wifi size={16} className="text-sc-success" />
                ) : (
                  <WifiOff size={16} className="text-sc-text-muted" />
                )}
                <span
                  className={clsx(
                    'text-xs font-mono uppercase font-semibold',
                    isOnline ? 'text-sc-success' : 'text-sc-text-muted',
                  )}
                >
                  {node.status}
                </span>
              </div>
            </div>

            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {[
                { label: 'VPN IP', value: node.vpnIp },
                { label: 'Local IP', value: node.localIp },
                { label: 'Last Heartbeat', value: node.lastHeartbeat ? formatDistanceToNow(new Date(node.lastHeartbeat), { addSuffix: true }) : 'N/A' },
                { label: 'Uptime Since', value: node.createdAt ? formatDistanceToNow(new Date(node.createdAt), { addSuffix: true }) : 'N/A' },
              ].map((item) => (
                <div key={item.label} className="bg-sc-surface-alt rounded-lg p-3 border border-sc-border">
                  <div className="text-sc-text-muted text-[10px] uppercase tracking-wider font-mono mb-1">
                    {item.label}
                  </div>
                  <div className="text-sc-text text-sm font-mono">{item.value}</div>
                </div>
              ))}
            </div>

            {Object.keys(hw).length > 0 && (
              <div className="mt-4 grid grid-cols-2 lg:grid-cols-5 gap-3">
                {[
                  { label: 'GPU Util', value: hw.gpu_util, color: 'text-sc-accent' },
                  { label: 'GPU Temp', value: hw.gpu_temp, color: 'text-sc-high' },
                  { label: 'CPU Util', value: hw.cpu_util, color: 'text-sc-info' },
                  { label: 'RAM', value: hw.ram_used, color: 'text-sc-warning' },
                  { label: 'Disk', value: hw.disk, color: 'text-sc-success' },
                ]
                  .filter((m) => m.value)
                  .map((metric) => (
                    <div
                      key={metric.label}
                      className="bg-sc-bg rounded-lg p-3 border border-sc-border"
                    >
                      <div className="text-sc-text-muted text-[10px] uppercase tracking-wider font-mono mb-1">
                        {metric.label}
                      </div>
                      <div className={`text-sm font-mono font-semibold ${metric.color}`}>
                        {metric.value}
                      </div>
                    </div>
                  ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
