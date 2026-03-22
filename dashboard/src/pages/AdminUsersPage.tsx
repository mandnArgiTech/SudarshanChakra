import { useQuery } from '@tanstack/react-query';
import { Users, Loader2 } from 'lucide-react';
import { fetchUsersList } from '@/api/saas';

export default function AdminUsersPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['users-admin'], queryFn: fetchUsersList });

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <Users className="text-sc-accent" size={28} />
        <div>
          <h1 className="text-xl font-bold text-sc-text">User management</h1>
          <p className="text-sc-text-muted text-sm">Roles and tenant access</p>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-sc-text-dim">
          <Loader2 className="animate-spin" size={20} />
          Loading users…
        </div>
      )}
      {error && (
        <div className="rounded-lg border border-sc-critical/40 bg-sc-critical/10 text-sc-critical text-sm p-4">
          Could not load users.
        </div>
      )}
      {data && (
        <div className="overflow-x-auto rounded-xl border border-sc-border">
          <table className="w-full text-sm text-left">
            <thead className="bg-sc-surface-alt text-sc-text-muted uppercase text-xs">
              <tr>
                <th className="px-4 py-3">User</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Farm</th>
                <th className="px-4 py-3">Active</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-sc-border">
              {data.map((u) => (
                <tr key={u.id} className="bg-sc-surface hover:bg-white/5">
                  <td className="px-4 py-3 text-sc-text">
                    <div className="font-medium">{u.displayName || u.username}</div>
                    <div className="text-xs text-sc-text-muted font-mono">{u.username}</div>
                  </td>
                  <td className="px-4 py-3 text-sc-text-dim">{u.role}</td>
                  <td className="px-4 py-3 font-mono text-xs text-sc-text-muted">{u.farmId}</td>
                  <td className="px-4 py-3">{u.active ? 'Yes' : 'No'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
