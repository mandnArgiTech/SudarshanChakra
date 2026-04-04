import type { AppUsageRecord } from '@/api/mdm';

const APP_COLORS: Record<string, { color: string; label: string }> = {
  'com.sudarshanchakra': { color: '#22c55e', label: 'SC' },
  'com.whatsapp': { color: '#25D366', label: 'WhatsApp' },
  'com.google.android.youtube': { color: '#FF0000', label: 'YouTube' },
  'com.google.android.apps.maps': { color: '#3b82f6', label: 'Maps' },
  'com.google.android.dialer': { color: '#f59e0b', label: 'Phone' },
  'com.android.dialer': { color: '#f59e0b', label: 'Phone' },
};

const OTHER_COLOR = '#1a2235';

interface DayBreakdown {
  date: string;
  totalHours: number;
  segments: { pct: number; color: string; label: string }[];
}

function buildDays(records: AppUsageRecord[]): DayBreakdown[] {
  const byDate = new Map<string, AppUsageRecord[]>();
  for (const r of records) {
    const list = byDate.get(r.date) ?? [];
    list.push(r);
    byDate.set(r.date, list);
  }

  const days: DayBreakdown[] = [];
  for (const [date, recs] of byDate) {
    const totalSec = recs.reduce((s, r) => s + r.foregroundTimeSec, 0);
    if (totalSec === 0) continue;

    const grouped = new Map<string, number>();
    let otherSec = 0;
    for (const r of recs) {
      if (APP_COLORS[r.packageName]) {
        grouped.set(r.packageName, (grouped.get(r.packageName) ?? 0) + r.foregroundTimeSec);
      } else {
        otherSec += r.foregroundTimeSec;
      }
    }

    const segments: DayBreakdown['segments'] = [];
    for (const [pkg, sec] of grouped) {
      const info = APP_COLORS[pkg];
      segments.push({ pct: (sec / totalSec) * 100, color: info.color, label: info.label });
    }
    if (otherSec > 0) {
      segments.push({ pct: (otherSec / totalSec) * 100, color: OTHER_COLOR, label: 'Other' });
    }

    days.push({ date, totalHours: +(totalSec / 3600).toFixed(1), segments });
  }

  return days.sort((a, b) => b.date.localeCompare(a.date));
}

const legendItems = [
  ...Object.values(APP_COLORS).filter(
    (v, i, arr) => arr.findIndex((x) => x.label === v.label) === i,
  ),
  { color: OTHER_COLOR, label: 'Other' },
];

interface ScreenTimeChartProps {
  records: AppUsageRecord[];
}

export default function ScreenTimeChart({ records }: ScreenTimeChartProps) {
  const days = buildDays(records);

  if (days.length === 0) {
    return (
      <div className="rounded-xl border border-sc-border bg-sc-surface p-4">
        <div className="text-sm font-semibold text-sc-text mb-3">Screen time</div>
        <div className="text-sc-text-muted text-xs py-6 text-center">No usage data yet</div>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-sc-border bg-sc-surface p-4">
      <div className="text-sm font-semibold text-sc-text mb-3">
        Screen time ({days.length} day{days.length !== 1 ? 's' : ''})
      </div>

      {days.map((d) => (
        <div key={d.date} className="mb-2.5">
          <div className="flex justify-between text-xs text-sc-text-dim mb-1">
            <span>{d.date}</span>
            <span>{d.totalHours}h total</span>
          </div>
          <div className="flex h-5 rounded-md overflow-hidden">
            {d.segments.map((seg, i) => (
              <div
                key={i}
                style={{ width: `${seg.pct}%`, backgroundColor: seg.color }}
                title={`${seg.label} ${seg.pct.toFixed(0)}%`}
              />
            ))}
          </div>
        </div>
      ))}

      <div className="flex flex-wrap gap-3 mt-2 text-[11px] text-sc-text-dim">
        {legendItems.map((item) => (
          <span key={item.label} className="flex items-center gap-1">
            <span
              className="inline-block w-2 h-2 rounded-sm"
              style={{ backgroundColor: item.color }}
            />
            {item.label}
          </span>
        ))}
      </div>
    </div>
  );
}
