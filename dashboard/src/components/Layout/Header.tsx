import { useLocation } from 'react-router-dom';
import { Search, Bell, Menu } from 'lucide-react';
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
  '/water': 'Water Levels',
  '/settings': 'Settings',
};

export interface HeaderProps {
  onMenuToggle?: () => void;
}

export default function Header({ onMenuToggle }: HeaderProps) {
  const location = useLocation();
  const { user, logout } = useAuth();
  const title = pageTitles[location.pathname] || 'Dashboard';

  return (
    <header className="flex justify-between items-center gap-3 px-4 sm:px-6 py-3.5 border-b border-sc-border bg-sc-surface flex-shrink-0">
      <div className="flex items-center gap-3 min-w-0">
        <button
          type="button"
          className="md:hidden flex items-center justify-center min-w-[48px] min-h-[48px] -ml-2 rounded-lg text-sc-text hover:bg-white/5 touch-manipulation active:scale-95 transition-transform"
          aria-label="Open menu"
          onClick={onMenuToggle}
        >
          <Menu size={22} />
        </button>
        <h2 className="text-lg sm:text-xl font-bold text-sc-text truncate">{title}</h2>
      </div>

      <div className="flex items-center gap-2 sm:gap-4 flex-shrink-0">
        <div className="hidden sm:flex items-center gap-2 px-4 py-1.5 bg-sc-surface-alt rounded-lg border border-sc-border text-sc-text-muted text-sm min-w-[200px]">
          <Search size={14} />
          <span>Search alerts, zones...</span>
        </div>

        <div className="relative cursor-pointer min-w-[44px] min-h-[44px] flex items-center justify-center">
          <Bell size={20} className="text-sc-text-dim" />
          <div className="absolute top-1 right-1 w-4 h-4 rounded-full bg-sc-critical flex items-center justify-center">
            <span className="text-white text-[10px] font-bold">3</span>
          </div>
        </div>

        <button
          type="button"
          onClick={logout}
          className="min-w-[44px] min-h-[44px] rounded-full bg-sc-accent/20 flex items-center justify-center text-sc-accent font-bold text-sm cursor-pointer hover:bg-sc-accent/30 transition-colors touch-manipulation"
          title="Logout"
        >
          {user?.username?.charAt(0).toUpperCase() || 'U'}
        </button>
      </div>
    </header>
  );
}
