import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import LoginPage from './LoginPage';
import { AuthProvider } from '@/hooks/useAuth';
import type { User } from '@/types';

vi.mock('@/api/auth', () => ({
  useLogin: () => ({
    mutateAsync: vi.fn().mockResolvedValue({
      token: 'tok',
      refreshToken: 'ref',
      user: { id: '1', username: 'u', email: 'e@e.com', role: 'admin' } as User,
    }),
  }),
}));

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

function wrap(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>
        <AuthProvider>{ui}</AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('renders username field', () => {
    wrap(<LoginPage />);
    expect(screen.getByPlaceholderText(/username/i)).toBeInTheDocument();
  });

  it('renders password field', () => {
    wrap(<LoginPage />);
    expect(screen.getByPlaceholderText(/password/i)).toBeInTheDocument();
  });

  it('submit button present', () => {
    wrap(<LoginPage />);
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('allows typing credentials', () => {
    wrap(<LoginPage />);
    fireEvent.change(screen.getByPlaceholderText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByPlaceholderText(/password/i), { target: { value: 'secret12' } });
    expect((screen.getByPlaceholderText(/username/i) as HTMLInputElement).value).toBe('admin');
  });

  it('shows dashboard branding', () => {
    wrap(<LoginPage />);
    expect(screen.getByText(/SUDARSHAN/i)).toBeInTheDocument();
  });

  it('form wraps inputs', () => {
    wrap(<LoginPage />);
    expect(screen.getByPlaceholderText(/username/i).closest('form')).toBeTruthy();
  });
});
