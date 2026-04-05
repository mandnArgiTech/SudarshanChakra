/**
 * Main nav route order for module-gated pages. Keep in sync with
 * `Sidebar.tsx` `mainNavItems` (`to` + `module`).
 */
export const MAIN_NAV_MODULE_ORDER: { path: string; module: string | null }[] = [
  { path: '/', module: 'alerts' },
  { path: '/alerts', module: 'alerts' },
  { path: '/cameras', module: 'cameras' },
  { path: '/zones', module: 'zones' },
  { path: '/devices', module: 'devices' },
  { path: '/siren', module: 'sirens' },
  { path: '/workers', module: 'workers' },
  { path: '/analytics', module: 'analytics' },
  { path: '/water', module: 'water' },
  { path: '/water/motors', module: 'pumps' },
  { path: '/mdm', module: 'mdm' },
  { path: '/settings', module: null },
];

/**
 * First sidebar-equivalent path the user may open. Used when `ModuleRoute`
 * denies access so we never redirect to `/` (which may require `alerts` and loop).
 */
export function getFirstAccessibleNavPath(
  hasModule: (moduleId: string | null) => boolean,
): string {
  for (const { path, module: m } of MAIN_NAV_MODULE_ORDER) {
    if (hasModule(m)) return path;
  }
  return '/settings';
}
