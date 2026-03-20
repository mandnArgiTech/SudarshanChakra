import { NavLink } from 'react-router-dom';
import { clsx } from 'clsx';
import { X } from 'lucide-react';
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
  Droplets,
  Zap,
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
  { to: '/water', icon: Droplets, label: 'Water' },
  { to: '/water/motors', icon: Zap, label: 'Pumps' },
  { to: '/settings', icon: Settings, label: 'Settings' },
];

export interface SidebarProps {
  /** Mobile drawer open state; ignored at md+ (tablet/desktop sidebars always visible). */
  open?: boolean;
  onClose?: () => void;
}

export default function Sidebar({ open = false, onClose }: SidebarProps) {
  const { data: nodes } = useNodes();

  const closeIfMobile = () => {
    if (typeof window !== 'undefined' && window.innerWidth < 768) {
      onClose?.();
    }
  };

  return (
    <>
      {open && (
        <button
          type="button"
          aria-label="Close menu"
          className="fixed inset-0 z-30 bg-black/50 md:hidden"
          onClick={onClose}
        />
      )}

      <aside
        data-open={open}
        className={clsx(
          'group/aside flex flex-col flex-shrink-0 h-screen bg-sc-surface border-r border-sc-border',
          'fixed inset-y-0 left-0 z-40',
          /* Phone: drawer */
          'max-md:w-[min(280px,85vw)] max-md:transition-transform max-md:duration-200 max-md:ease-out',
          'max-md:-translate-x-full max-md:data-[open=true]:translate-x-0',
          /* Tablet: 56px rail, expand to 220px on hover */
          'md:translate-x-0 md:w-14 md:overflow-x-hidden md:hover:w-[220px] md:transition-[width] md:duration-200 md:ease-out',
          /* Desktop */
          'lg:static lg:z-auto lg:w-[220px] lg:overflow-visible lg:hover:w-[220px]',
        )}
      >
        <div className="px-5 pt-5 pb-6 border-b border-sc-border flex items-start justify-between gap-2 shrink-0 md:px-2 lg:px-5">
          <div className="min-w-0 flex-1">
            <div
              className={clsx(
                'hidden max-md:flex max-md:flex-col md:max-lg:hidden md:max-lg:group-hover/aside:flex md:max-lg:group-hover/aside:flex-col lg:flex lg:flex-col',
              )}
            >
              <div className="text-sc-accent text-lg font-extrabold font-mono tracking-wider">
                SUDARSHAN
              </div>
              <div className="text-sc-text-muted text-[10px] font-mono tracking-[3px] mt-0.5">
                CHAKRA
              </div>
            </div>
            <div
              className={clsx(
                'hidden md:max-lg:flex lg:hidden items-center justify-center py-2',
                'group-hover/aside:hidden',
              )}
            >
              <span className="text-sc-accent font-black text-xl font-mono tracking-tight">SC</span>
            </div>
          </div>
          <button
            type="button"
            className="md:hidden p-2 rounded-lg text-sc-text-dim hover:bg-white/10 -mr-2 -mt-1 shrink-0"
            aria-label="Close sidebar"
            onClick={onClose}
          >
            <X size={20} />
          </button>
        </div>

        <nav className="flex-1 py-3 px-2 overflow-y-auto overscroll-y-contain touch-pan-y">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              onClick={closeIfMobile}
              title={item.label}
              className={({ isActive }) =>
                clsx(
                  'flex items-center gap-3 px-3 py-2.5 rounded-lg mb-0.5 transition-all duration-150 group min-h-[44px]',
                  'md:max-lg:justify-center md:max-lg:group-hover/aside:justify-start',
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
                      'shrink-0',
                      isActive ? 'text-sc-accent' : 'text-sc-text-muted group-hover:text-sc-text-dim',
                    )}
                  />
                  <span
                    className={clsx(
                      'text-sm whitespace-nowrap overflow-hidden',
                      'md:max-lg:max-w-0 md:max-lg:opacity-0 md:max-lg:group-hover/aside:max-w-[200px] md:max-lg:group-hover/aside:opacity-100',
                      'md:max-lg:transition-all md:max-lg:duration-200',
                      isActive ? 'text-sc-text font-semibold' : 'text-sc-text-dim',
                    )}
                  >
                    {item.label}
                  </span>
                  {item.badge && (
                    <span
                      className={clsx(
                        'ml-auto bg-sc-critical text-white rounded-full px-1.5 py-px text-[11px] font-bold font-mono min-w-[20px] text-center shrink-0',
                        'md:max-lg:hidden md:max-lg:group-hover/aside:inline-block',
                      )}
                    >
                      7
                    </span>
                  )}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div
          className={clsx(
            'p-3 border-t border-sc-border',
            'md:max-lg:opacity-0 md:max-lg:group-hover/aside:opacity-100 md:max-lg:transition-opacity md:max-lg:duration-200',
            'md:max-lg:h-0 md:max-lg:overflow-hidden md:max-lg:group-hover/aside:h-auto md:max-lg:group-hover/aside:overflow-visible md:max-lg:p-0 md:max-lg:group-hover/aside:p-3',
          )}
        >
          {(nodes ?? [
            { id: 'a', displayName: 'Edge Node A', status: 'online' },
            { id: 'b', displayName: 'Edge Node B', status: 'online' },
          ]).map((node) => (
            <div key={node.id} className="flex items-center gap-2 px-3 py-1.5">
              <div
                className={clsx(
                  'w-1.5 h-1.5 rounded-full shrink-0',
                  node.status === 'online' ? 'bg-sc-success' : 'bg-sc-text-muted',
                )}
              />
              <span className="text-sc-text-dim text-xs truncate">{node.displayName}</span>
            </div>
          ))}
        </div>
      </aside>
    </>
  );
}
