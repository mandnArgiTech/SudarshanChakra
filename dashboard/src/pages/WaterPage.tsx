import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
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
      <h1 className="text-2xl font-bold text-sc-text mb-6">Water levels</h1>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {tanks.map((t) => (
          <WaterTankGauge key={t.id} tank={t} />
        ))}
      </div>
    </div>
  );
}
