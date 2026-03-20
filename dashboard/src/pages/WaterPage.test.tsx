import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import WaterPage from './WaterPage';

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('WaterPage', () => {
  it('renders water heading', () => {
    wrap(<WaterPage />);
    expect(screen.getByText('Water levels')).toBeInTheDocument();
  });

  it('shows fallback tank after query settles', async () => {
    wrap(<WaterPage />);
    await waitFor(() => {
      expect(screen.getByText(/Main tank/i)).toBeInTheDocument();
    });
  });
});
