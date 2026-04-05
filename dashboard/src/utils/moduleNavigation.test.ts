import { describe, it, expect } from 'vitest';
import { getFirstAccessibleNavPath, MAIN_NAV_MODULE_ORDER } from './moduleNavigation';

describe('getFirstAccessibleNavPath', () => {
  it('returns slash when user has alerts', () => {
    const has = (m: string | null) => m === null || m === 'alerts';
    expect(getFirstAccessibleNavPath(has)).toBe('/');
  });

  it('returns water when user lacks alerts but has water', () => {
    const has = (m: string | null) =>
      m === null || m === 'water' || m === 'pumps';
    expect(getFirstAccessibleNavPath(has)).toBe('/water');
  });

  it('returns settings when only null module allowed', () => {
    const has = (m: string | null) => m === null;
    expect(getFirstAccessibleNavPath(has)).toBe('/settings');
  });

  it('order matches sidebar length for drift checks', () => {
    expect(MAIN_NAV_MODULE_ORDER.length).toBe(12);
    expect(MAIN_NAV_MODULE_ORDER.at(-1)?.path).toBe('/settings');
  });
});
