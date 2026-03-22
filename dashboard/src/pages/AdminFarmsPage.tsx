import { useQuery } from '@tanstack/react-query';
import { Building2, Loader2 } from 'lucide-react';
import { fetchFarms } from '@/api/saas';

export default function AdminFarmsPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['farms'], queryFn: fetchFarms });

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <Building2 className="text-sc-accent" size={28} />
        <div>
          <h1 className="text-xl font-bold text-sc-text">Farm management</h1>
          <p className="text-sc-text-muted text-sm">Super admin — all tenants</p>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-sc-text-dim">
          <Loader2 className="animate-spin" size={20} />
          Loading farms…
        </div>
      )}
      {error && (
        <div className="rounded-lg border border-sc-critical/40 bg-sc-critical/10 text-sc-critical text-sm p-4">
          Could not load farms. Ensure you are signed in as super_admin and the API is reachable.
        </div>
      )}
      {data && (
        <div className="space-y-3">
          {data.map((f) => (
            <div
              key={f.id}
              className="rounded-xl border border-sc-border bg-sc-surface p-4 flex flex-wrap justify-between gap-3"
            >
              <div>
                <div className="font-semibold text-sc-text">{f.name}</div>
                <div className="text-xs text-sc-text-muted font-mono">{f.slug}</div>
              </div>
              <div className="flex flex-wrap gap-2 text-xs">
                <span className="px-2 py-1 rounded bg-sc-surface-alt border border-sc-border text-sc-text-dim">
                  {f.subscriptionPlan}
                </span>
                <span className="px-2 py-1 rounded bg-sc-surface-alt border border-sc-border text-sc-text-dim">
                  {f.status}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
