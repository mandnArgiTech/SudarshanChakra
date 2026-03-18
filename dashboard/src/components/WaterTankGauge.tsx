import { Area, AreaChart, ResponsiveContainer, XAxis, YAxis } from 'recharts';

export interface WaterTank {
  id: string;
  name: string;
  levelPct: number;
  history?: { t: string; v: number }[];
}

export default function WaterTankGauge({ tank }: { tank: WaterTank }) {
  const data = tank.history?.length
    ? tank.history
    : [{ t: 'now', v: tank.levelPct }];
  return (
    <div className="rounded-xl border border-sc-border bg-sc-surface p-4">
      <h3 className="text-sc-text font-semibold mb-2">{tank.name}</h3>
      <div className="text-3xl font-mono text-cyan-400 mb-4">{tank.levelPct.toFixed(0)}%</div>
      <div className="h-32">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data}>
            <XAxis dataKey="t" hide />
            <YAxis domain={[0, 100]} hide />
            <Area type="monotone" dataKey="v" stroke="#22d3ee" fill="#22d3ee33" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
