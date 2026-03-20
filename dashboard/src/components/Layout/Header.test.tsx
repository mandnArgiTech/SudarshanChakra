import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Header from './Header';
import { AuthProvider } from '@/hooks/useAuth';

const logout = vi.fn();

vi.mock('@/hooks/useAuth', async () => {
  const actual = await vi.importActual<typeof import('@/hooks/useAuth')>('@/hooks/useAuth');
  return {
    ...actual,
    useAuth: () => ({
      token: 't',
      user: {
        id: '1',
        username: 'farmer',
        email: 'f@farm.io',
        role: 'admin' as const,
        farmId: 'f1',
        active: true,
      },
      login: vi.fn(),
      logout,
    }),
  };
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="*" element={<Header />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Header', () => {
  beforeEach(() => {
    logout.mockClear();
  });

  it('shows page title for dashboard route', () => {
    renderAt('/');
    expect(screen.getByRole('heading', { level: 2, name: 'Dashboard' })).toBeInTheDocument();
  });

  it('shows search bar on sm+ (hidden on xs in markup)', () => {
    renderAt('/alerts');
    expect(screen.getByText(/Search alerts/i)).toBeInTheDocument();
  });

  it('shows notification bell with badge', () => {
    renderAt('/');
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('calls logout when avatar clicked', () => {
    renderAt('/');
    fireEvent.click(screen.getByTitle('Logout'));
    expect(logout).toHaveBeenCalled();
  });

  it('invokes onMenuToggle when hamburger pressed', () => {
    const onMenuToggle = vi.fn();
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="*" element={<Header onMenuToggle={onMenuToggle} />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole('button', { name: /open menu/i }));
    expect(onMenuToggle).toHaveBeenCalled();
  });
});
