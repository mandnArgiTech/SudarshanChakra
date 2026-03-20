import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import AlertsPage from './AlertsPage';

const ackMutate = vi.fn();
const resolveMutate = vi.fn();

vi.mock('@/api/alerts', () => ({
  useAlerts: () => ({ data: undefined }),
  useAcknowledgeAlert: () => ({ mutate: ackMutate }),
  useResolveAlert: () => ({ mutate: resolveMutate }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('AlertsPage', () => {
  beforeEach(() => {
    qc.clear();
    ackMutate.mockClear();
    resolveMutate.mockClear();
  });

  it('renders AlertTable with fallback alert content', () => {
    wrap(<AlertsPage />);
    expect(screen.getAllByText(/Pond Danger Zone/).length).toBeGreaterThanOrEqual(1);
  });

  it('renders filter controls', () => {
    wrap(<AlertsPage />);
    expect(screen.getByRole('button', { name: /^all$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /critical/i })).toBeInTheDocument();
  });

  it('switches priority filter', () => {
    wrap(<AlertsPage />);
    fireEvent.click(screen.getByRole('button', { name: /critical/i }));
    expect(screen.getByRole('button', { name: /critical/i })).toHaveClass('border-sc-accent');
  });
});
