import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import SirenPage from './SirenPage';

const triggerMutate = vi.fn((_vars: unknown, opts?: { onSuccess?: () => void }) => {
  opts?.onSuccess?.();
});
const stopMutate = vi.fn((_vars: unknown, opts?: { onSuccess?: () => void }) => {
  opts?.onSuccess?.();
});

vi.mock('@/api/siren', () => ({
  useTriggerSiren: () => ({ mutate: triggerMutate, isPending: false }),
  useStopSiren: () => ({ mutate: stopMutate, isPending: false }),
  useSirenHistory: () => ({ data: undefined }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('SirenPage', () => {
  beforeEach(() => {
    qc.clear();
    triggerMutate.mockClear();
    stopMutate.mockClear();
  });

  it('renders standby state and trigger button', () => {
    wrap(<SirenPage />);
    expect(screen.getByText('SIREN STANDBY')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /trigger all/i })).toBeInTheDocument();
  });

  it('renders siren history section', () => {
    wrap(<SirenPage />);
    expect(screen.getByText('Siren Activation History')).toBeInTheDocument();
    expect(screen.getAllByText(/Siren trigger/).length).toBeGreaterThanOrEqual(1);
  });

  it('toggles to active when trigger succeeds', () => {
    wrap(<SirenPage />);
    fireEvent.click(screen.getByRole('button', { name: /trigger all/i }));
    expect(screen.getByText('SIREN ACTIVE')).toBeInTheDocument();
    expect(triggerMutate).toHaveBeenCalled();
  });

  it('node-only trigger buttons call mutate', () => {
    wrap(<SirenPage />);
    fireEvent.click(screen.getByRole('button', { name: /node a only/i }));
    expect(triggerMutate).toHaveBeenCalledWith({ nodeId: 'node-a' });
  });
});
