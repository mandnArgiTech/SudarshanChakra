import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import DashboardPage from './DashboardPage';

vi.mock('@/hooks/useAlertWebSocket', () => ({
  useAlertWebSocket: () => ({ connected: false, liveAlerts: [] }),
}));

vi.mock('@/api/alerts', () => ({
  useAlerts: () => ({
    data: {
      content: [],
      totalElements: 7,
      totalPages: 1,
      size: 5,
      number: 0,
      first: true,
      last: true,
    },
  }),
}));

vi.mock('@/api/devices', () => ({
  useNodes: () => ({ data: undefined }),
}));

vi.mock('@/api/siren', () => ({
  useTriggerSiren: () => ({ mutate: vi.fn(), isPending: false }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('DashboardPage', () => {
  beforeEach(() => {
    qc.clear();
  });

  it('renders stat card labels', () => {
    wrap(<DashboardPage />);
    expect(screen.getByText('Active Alerts')).toBeInTheDocument();
    expect(screen.getByText('Cameras Online')).toBeInTheDocument();
    expect(screen.getByText('Detections Today')).toBeInTheDocument();
    expect(screen.getByText('Workers On-Site')).toBeInTheDocument();
  });

  it('renders Live Alert Feed section', () => {
    wrap(<DashboardPage />);
    expect(screen.getByText('Live Alert Feed')).toBeInTheDocument();
    expect(screen.getByText(/POLLING|LIVE/)).toBeInTheDocument();
  });

  it('renders Node Status and Quick Siren Control', () => {
    wrap(<DashboardPage />);
    expect(screen.getByText('Node Status')).toBeInTheDocument();
    expect(screen.getByText('Quick Siren Control')).toBeInTheDocument();
    expect(screen.getByText(/TRIGGER SIREN/)).toBeInTheDocument();
  });
});
