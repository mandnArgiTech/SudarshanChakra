import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import CamerasPage from './CamerasPage';

vi.mock('@/api/devices', () => ({
  useCameras: () => ({ data: undefined }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('CamerasPage', () => {
  it('renders camera grid from fallback data', () => {
    wrap(<CamerasPage />);
    expect(screen.getByText('Front Gate')).toBeInTheDocument();
    expect(screen.getByText('Pond Area')).toBeInTheDocument();
    expect(screen.getByText('ALERT')).toBeInTheDocument();
  });

  it('shows camera ids in footer line', () => {
    wrap(<CamerasPage />);
    expect(screen.getByText(/CAM-01/)).toBeInTheDocument();
  });
});
