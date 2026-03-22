import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { Zap } from 'lucide-react';
import WaterTankGauge, { type WaterTank } from '@/components/WaterTankGauge';

async function fetchTanks(): Promise<WaterTank[]> {
  try {
    const { data } = await axios.get('/api/v1/water/tanks', {
      headers: { Authorization: `Bearer ${localStorage.getItem('sc_token')}` },
    });
    const list = Array.isArray(data) ? data : [];
    return list.map(
      (t: {
        id: string;
        displayName?: string;
        name?: string;
        currentLevel?: { percentFilled?: number };
      }) => ({
        id: t.id,
        name: t.displayName ?? t.name ?? t.id,
        levelPct: t.currentLevel?.percentFilled ?? 50,
        history: [],
      }),
    );
  } catch {
    return [
      { id: 'demo', name: 'Main tank (demo)', levelPct: 72, history: [{ t: '24h', v: 72 }, { t: '12h', v: 75 }] },
    ];
  }
}

export default function WaterPage() {
  const { data: tanks = [] } = useQuery({ queryKey: ['water-tanks'], queryFn: fetchTanks });
  return (
    <div className="p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between mb-6">
        <h1 className="text-2xl font-bold text-sc-text">Water levels</h1>
        <Link
          to="/water/motors"
          className="inline-flex items-center gap-2 rounded-lg border border-sc-border bg-sc-surface-alt px-4 py-2 text-sm font-medium text-sc-accent hover:border-sc-accent/40 hover:bg-sc-surface transition-colors shrink-0"
        >
          <Zap size={18} aria-hidden />
          Pump &amp; motor control
        </Link>
      </div>
      <p className="text-sc-text-muted text-sm mb-6 max-w-2xl">
        Tank levels from device-service. Control pumps and motors on the linked page (MQTT commands to edge).
      </p>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {tanks.map((t) => (
          <WaterTankGauge key={t.id} tank={t} />
        ))}
      </div>
    </div>
  );
}
