import {
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';

const alertsOverTime = [
  { time: '00:00', critical: 0, high: 2, warning: 5 },
  { time: '04:00', critical: 1, high: 3, warning: 4 },
  { time: '08:00', critical: 3, high: 5, warning: 8 },
  { time: '12:00', critical: 2, high: 7, warning: 12 },
  { time: '16:00', critical: 4, high: 6, warning: 9 },
  { time: '20:00', critical: 2, high: 4, warning: 7 },
  { time: '23:59', critical: 1, high: 3, warning: 6 },
];

const alertsByZone = [
  { zone: 'Pond', count: 24 },
  { zone: 'Perimeter', count: 18 },
  { zone: 'Snake Zone', count: 12 },
  { zone: 'Storage', count: 8 },
  { zone: 'Pasture', count: 15 },
  { zone: 'Cattle Pen', count: 10 },
];

const alertsByClass = [
  { name: 'person', value: 35, color: '#ef4444' },
  { name: 'child', value: 12, color: '#f97316' },
  { name: 'snake', value: 18, color: '#eab308' },
  { name: 'cow', value: 22, color: '#22c55e' },
  { name: 'fire', value: 5, color: '#f59e0b' },
  { name: 'scorpion', value: 8, color: '#3b82f6' },
];

const tooltipStyle = {
  backgroundColor: '#111827',
  border: '1px solid #1e293b',
  borderRadius: '8px',
  color: '#f1f5f9',
  fontSize: '12px',
  fontFamily: '"JetBrains Mono", monospace',
};

export default function AnalyticsPage() {
  return (
    <div className="space-y-6">
      <div className="bg-sc-surface border border-sc-border rounded-xl p-6">
        <h3 className="text-sc-text text-base font-semibold mb-4">Alerts Over Time (24h)</h3>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={alertsOverTime}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="time" stroke="#64748b" tick={{ fontSize: 11, fontFamily: '"JetBrains Mono"' }} />
            <YAxis stroke="#64748b" tick={{ fontSize: 11, fontFamily: '"JetBrains Mono"' }} />
            <Tooltip contentStyle={tooltipStyle} />
            <Legend wrapperStyle={{ fontSize: '12px', fontFamily: '"JetBrains Mono"' }} />
            <Line type="monotone" dataKey="critical" stroke="#ef4444" strokeWidth={2} dot={{ fill: '#ef4444', r: 3 }} />
            <Line type="monotone" dataKey="high" stroke="#f97316" strokeWidth={2} dot={{ fill: '#f97316', r: 3 }} />
            <Line type="monotone" dataKey="warning" stroke="#eab308" strokeWidth={2} dot={{ fill: '#eab308', r: 3 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-sc-surface border border-sc-border rounded-xl p-6">
          <h3 className="text-sc-text text-base font-semibold mb-4">Alerts by Zone</h3>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={alertsByZone}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="zone" stroke="#64748b" tick={{ fontSize: 10, fontFamily: '"JetBrains Mono"' }} />
              <YAxis stroke="#64748b" tick={{ fontSize: 11, fontFamily: '"JetBrains Mono"' }} />
              <Tooltip contentStyle={tooltipStyle} />
              <Bar dataKey="count" fill="#f59e0b" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-sc-surface border border-sc-border rounded-xl p-6">
          <h3 className="text-sc-text text-base font-semibold mb-4">Detection Classes</h3>
          <ResponsiveContainer width="100%" height={280}>
            <PieChart>
              <Pie
                data={alertsByClass}
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={100}
                paddingAngle={3}
                dataKey="value"
                label={({ name, percent }) =>
                  `${name} ${(percent * 100).toFixed(0)}%`
                }
                labelLine={{ stroke: '#64748b' }}
              >
                {alertsByClass.map((entry) => (
                  <Cell key={entry.name} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip contentStyle={tooltipStyle} />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="bg-sc-surface border border-sc-border rounded-xl p-6">
        <h3 className="text-sc-text text-base font-semibold mb-4">Summary Statistics</h3>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
          {[
            { label: 'Total Alerts (24h)', value: '143', color: 'text-sc-critical' },
            { label: 'Avg Response Time', value: '2.4 min', color: 'text-sc-accent' },
            { label: 'False Positive Rate', value: '8.2%', color: 'text-sc-warning' },
            { label: 'Resolved Rate', value: '91.3%', color: 'text-sc-success' },
          ].map((stat) => (
            <div
              key={stat.label}
              className="bg-sc-surface-alt rounded-lg p-4 border border-sc-border text-center"
            >
              <div className="text-sc-text-muted text-[10px] uppercase tracking-wider font-mono mb-2">
                {stat.label}
              </div>
              <div className={`text-2xl font-extrabold font-mono ${stat.color}`}>
                {stat.value}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
