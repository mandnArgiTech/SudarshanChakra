import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import ZonesPage from './ZonesPage';

vi.mock('@/api/devices', () => ({
  useZones: () => ({ data: undefined }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('ZonesPage', () => {
  it('renders zone cards with names from fallback', () => {
    wrap(<ZonesPage />);
    expect(screen.getByText('Pond Danger Zone')).toBeInTheDocument();
    expect(screen.getByText('Snake Zone Alpha')).toBeInTheDocument();
  });

  it('renders Create zone control', () => {
    wrap(<ZonesPage />);
    expect(screen.getByRole('button', { name: /create zone/i })).toBeInTheDocument();
  });

  it('groups zones under camera headings', () => {
    wrap(<ZonesPage />);
    expect(screen.getByText('CAM-03')).toBeInTheDocument();
    expect(screen.getByText('(2 zones)')).toBeInTheDocument();
  });

  it('shows zone type and dedup metadata', () => {
    wrap(<ZonesPage />);
    expect(screen.getAllByText('Type:').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Dedup:').length).toBeGreaterThanOrEqual(1);
  });
});
