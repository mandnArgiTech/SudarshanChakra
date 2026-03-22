import { useQuery } from '@tanstack/react-query';
import { ScrollText, Loader2 } from 'lucide-react';
import { fetchAuditPage } from '@/api/saas';

export default function AdminAuditPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['audit', 0],
    queryFn: () => fetchAuditPage(0, 100),
  });

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <ScrollText className="text-sc-accent" size={28} />
        <div>
          <h1 className="text-xl font-bold text-sc-text">Audit log</h1>
          <p className="text-sc-text-muted text-sm">Recent security and admin events</p>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-sc-text-dim">
          <Loader2 className="animate-spin" size={20} />
          Loading audit…
        </div>
      )}
      {error && (
        <div className="rounded-lg border border-sc-critical/40 bg-sc-critical/10 text-sc-critical text-sm p-4">
          Could not load audit log.
        </div>
      )}
      {data?.content && (
        <div className="space-y-2">
          {data.content.map((row) => (
            <div
              key={row.id}
              className="rounded-lg border border-sc-border bg-sc-surface px-4 py-3 text-sm"
            >
              <div className="flex flex-wrap justify-between gap-2">
                <span className="font-mono text-sc-accent">{row.action}</span>
                <span className="text-xs text-sc-text-muted">
                  {new Date(row.createdAt).toLocaleString()}
                </span>
              </div>
              <div className="text-sc-text-dim text-xs mt-1">
                {[row.entityType, row.entityId].filter(Boolean).join(' · ') || '—'}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
