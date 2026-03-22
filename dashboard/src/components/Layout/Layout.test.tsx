import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Layout from './Layout';

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    token: 't',
    user: {
      id: '1',
      username: 'u',
      email: 'e@e.com',
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

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

describe('Layout', () => {
  it('renders sidebar nav and outlet content', () => {
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<Layout />}>
              <Route index element={<div>Outlet child</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Outlet child')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument();
  });

  it('renders main landmark with page heading from header', () => {
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<Layout />}>
              <Route index element={<span>Home</span>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });
});
