import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import DevicesPage from './DevicesPage';

vi.mock('@/api/devices', () => ({
  useNodes: () => ({ data: undefined }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('DevicesPage', () => {
  it('renders edge node cards from fallback', () => {
    wrap(<DevicesPage />);
    expect(screen.getByText('Edge Node A')).toBeInTheDocument();
    expect(screen.getByText('Edge Node B')).toBeInTheDocument();
  });

  it('shows online status for nodes', () => {
    wrap(<DevicesPage />);
    const statuses = screen.getAllByText('online');
    expect(statuses.length).toBeGreaterThanOrEqual(2);
  });

  it('renders VPN IP labels', () => {
    wrap(<DevicesPage />);
    expect(screen.getAllByText('VPN IP').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('10.8.0.10')).toBeInTheDocument();
  });
});
