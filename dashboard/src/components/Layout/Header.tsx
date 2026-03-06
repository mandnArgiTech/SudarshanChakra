import { useLocation } from 'react-router-dom';
import { Search, Bell } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';

const pageTitles: Record<string, string> = {
  '/': 'Dashboard',
  '/alerts': 'Alert Management',
  '/cameras': 'Camera Grid',
  '/zones': 'Virtual Fence Zones',
  '/devices': 'Edge Devices',
  '/siren': 'Siren Control',
  '/workers': 'Worker Tags',
  '/analytics': 'Analytics',
  '/settings': 'Settings',
};

export default function Header() {
  const location = useLocation();
  const { user, logout } = useAuth();
  const title = pageTitles[location.pathname] || 'Dashboard';

  return (
    <header className="flex justify-between items-center px-6 py-3.5 border-b border-sc-border bg-sc-surface flex-shrink-0">
      <h2 className="text-xl font-bold text-sc-text">{title}</h2>

      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2 px-4 py-1.5 bg-sc-surface-alt rounded-lg border border-sc-border text-sc-text-muted text-sm min-w-[200px]">
          <Search size={14} />
          <span>Search alerts, zones...</span>
        </div>

        <div className="relative cursor-pointer">
          <Bell size={20} className="text-sc-text-dim" />
          <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-sc-critical flex items-center justify-center">
            <span className="text-white text-[10px] font-bold">3</span>
          </div>
        </div>

        <button
          onClick={logout}
          className="w-8 h-8 rounded-full bg-sc-accent/20 flex items-center justify-center text-sc-accent font-bold text-sm cursor-pointer hover:bg-sc-accent/30 transition-colors"
          title="Logout"
        >
          {user?.username?.charAt(0).toUpperCase() || 'U'}
        </button>
      </div>
    </header>
  );
}
