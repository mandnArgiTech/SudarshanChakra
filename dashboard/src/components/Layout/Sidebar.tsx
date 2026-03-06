import { NavLink } from 'react-router-dom';
import { clsx } from 'clsx';
import {
  LayoutDashboard,
  AlertTriangle,
  Camera,
  Hexagon,
  Cpu,
  Siren,
  Users,
  BarChart3,
  Settings,
} from 'lucide-react';
import { useNodes } from '@/api/devices';

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/alerts', icon: AlertTriangle, label: 'Alerts', badge: true },
  { to: '/cameras', icon: Camera, label: 'Cameras' },
  { to: '/zones', icon: Hexagon, label: 'Zones' },
  { to: '/devices', icon: Cpu, label: 'Devices' },
  { to: '/siren', icon: Siren, label: 'Siren' },
  { to: '/workers', icon: Users, label: 'Workers' },
  { to: '/analytics', icon: BarChart3, label: 'Analytics' },
  { to: '/settings', icon: Settings, label: 'Settings' },
];

export default function Sidebar() {
  const { data: nodes } = useNodes();

  return (
    <aside className="w-[220px] bg-sc-surface border-r border-sc-border flex flex-col flex-shrink-0 h-screen">
      <div className="px-5 pt-5 pb-6 border-b border-sc-border">
        <div className="text-sc-accent text-lg font-extrabold font-mono tracking-wider">
          SUDARSHAN
        </div>
        <div className="text-sc-text-muted text-[10px] font-mono tracking-[3px] mt-0.5">
          CHAKRA
        </div>
      </div>

      <nav className="flex-1 py-3 px-2 overflow-y-auto">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg mb-0.5 transition-all duration-150 group',
                isActive
                  ? 'bg-sc-accent/10 border-l-[3px] border-sc-accent'
                  : 'border-l-[3px] border-transparent hover:bg-white/5',
              )
            }
          >
            {({ isActive }) => (
              <>
                <item.icon
                  size={18}
                  className={clsx(
                    isActive ? 'text-sc-accent' : 'text-sc-text-muted group-hover:text-sc-text-dim',
                  )}
                />
                <span
                  className={clsx(
                    'text-sm',
                    isActive ? 'text-sc-text font-semibold' : 'text-sc-text-dim',
                  )}
                >
                  {item.label}
                </span>
                {item.badge && (
                  <span className="ml-auto bg-sc-critical text-white rounded-full px-1.5 py-px text-[11px] font-bold font-mono min-w-[20px] text-center">
                    7
                  </span>
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="p-3 border-t border-sc-border">
        {(nodes ?? [{ id: 'a', displayName: 'Edge Node A', status: 'online' }, { id: 'b', displayName: 'Edge Node B', status: 'online' }]).map(
          (node) => (
            <div key={node.id} className="flex items-center gap-2 px-3 py-1.5">
              <div
                className={clsx(
                  'w-1.5 h-1.5 rounded-full',
                  node.status === 'online' ? 'bg-sc-success' : 'bg-sc-text-muted',
                )}
              />
              <span className="text-sc-text-dim text-xs">{node.displayName}</span>
            </div>
          ),
        )}
      </div>
    </aside>
  );
}
