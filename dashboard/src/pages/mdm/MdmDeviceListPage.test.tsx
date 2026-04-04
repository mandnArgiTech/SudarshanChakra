import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import MdmDeviceListPage from './MdmDeviceListPage';

vi.mock('@/api/mdm', () => ({
  useMdmDevices: () => ({ data: undefined, isLoading: false, error: null }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('MdmDeviceListPage', () => {
  it('renders device list from mock fallback', () => {
    wrap(<MdmDeviceListPage />);
    expect(screen.getByText("Ramesh's Phone")).toBeInTheDocument();
    expect(screen.getByText("Suresh's Phone")).toBeInTheDocument();
    expect(screen.getByText("Priya's Phone")).toBeInTheDocument();
  });

  it('shows status badges', () => {
    wrap(<MdmDeviceListPage />);
    const activeBadges = screen.getAllByText('active');
    expect(activeBadges.length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('pending')).toBeInTheDocument();
  });

  it('shows kiosk badge for device-owner devices', () => {
    wrap(<MdmDeviceListPage />);
    const kioskBadges = screen.getAllByText('Kiosk ON');
    expect(kioskBadges.length).toBeGreaterThanOrEqual(1);
  });
});
