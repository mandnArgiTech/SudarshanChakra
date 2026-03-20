import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import MotorControlPage from './MotorControlPage';

vi.mock('@/api/water', () => ({
  useWaterMotors: () => ({ data: undefined, isError: true }),
  useMotorCommand: () => ({ mutate: vi.fn(), isPending: false }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('MotorControlPage', () => {
  it('renders pump control heading', () => {
    wrap(<MotorControlPage />);
    expect(screen.getByText(/Pump & motor control/i)).toBeInTheDocument();
  });

  it('shows demo motor when API errors', () => {
    wrap(<MotorControlPage />);
    expect(screen.getByText(/Main pump \(demo\)/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /pump on/i })).toBeInTheDocument();
  });
});
