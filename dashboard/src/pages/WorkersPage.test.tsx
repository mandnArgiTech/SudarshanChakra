import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import WorkersPage from './WorkersPage';

vi.mock('@/api/devices', () => ({
  useTags: () => ({ data: undefined }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('WorkersPage', () => {
  it('renders worker tag list from fallback', () => {
    wrap(<WorkersPage />);
    expect(screen.getByText('Ravi Kumar')).toBeInTheDocument();
    expect(screen.getByText(/TAG-001/)).toBeInTheDocument();
  });

  it('shows active and inactive summary badges', () => {
    wrap(<WorkersPage />);
    expect(screen.getByText('3 Active')).toBeInTheDocument();
    expect(screen.getByText('1 Inactive')).toBeInTheDocument();
  });
});
