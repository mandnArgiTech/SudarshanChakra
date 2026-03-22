import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Sidebar from './Sidebar';

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    token: 't',
    user: {
      id: '1',
      username: 'u',
      email: '',
      role: 'admin' as const,
      farmId: 'f1',
      active: true,
    },
    login: vi.fn(),
    logout: vi.fn(),
    hasModule: () => true,
  }),
}));

vi.mock('@/api/devices', () => ({
  useNodes: () => ({ data: [] }),
}));

const qc = new QueryClient();

describe('Sidebar', () => {
  it('renders nav labels', () => {
    render(
      <QueryClientProvider client={qc}>
        <BrowserRouter>
          <Sidebar open onClose={() => {}} />
        </BrowserRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Alerts')).toBeInTheDocument();
  });

  it('water link', () => {
    render(
      <QueryClientProvider client={qc}>
        <BrowserRouter>
          <Sidebar open onClose={() => {}} />
        </BrowserRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Water')).toBeInTheDocument();
  });

  it('cameras link', () => {
    render(
      <QueryClientProvider client={qc}>
        <BrowserRouter>
          <Sidebar open onClose={() => {}} />
        </BrowserRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Cameras')).toBeInTheDocument();
  });

  it('devices link', () => {
    render(
      <QueryClientProvider client={qc}>
        <BrowserRouter>
          <Sidebar open onClose={() => {}} />
        </BrowserRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Devices')).toBeInTheDocument();
  });

  it('settings link', () => {
    render(
      <QueryClientProvider client={qc}>
        <BrowserRouter>
          <Sidebar open onClose={() => {}} />
        </BrowserRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Settings')).toBeInTheDocument();
  });
});
