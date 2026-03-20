import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AuthProvider, useAuth } from './useAuth';

function T() {
  const { token, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="t">{token || 'none'}</span>
      <button
        type="button"
        onClick={() =>
          login('a', 'b', {
            id: '1',
            username: 'x',
            email: '',
            role: 'viewer',
            farmId: 'f1',
            active: true,
          })
        }
      >
        in
      </button>
      <button type="button" onClick={logout}>
        out
      </button>
    </div>
  );
}

describe('useAuth', () => {
  beforeEach(() => localStorage.clear());

  it('starts logged out', () => {
    render(
      <AuthProvider>
        <T />
      </AuthProvider>,
    );
    expect(screen.getByTestId('t')).toHaveTextContent('none');
  });

  it('login sets token', () => {
    render(
      <AuthProvider>
        <T />
      </AuthProvider>,
    );
    fireEvent.click(screen.getByText('in'));
    expect(screen.getByTestId('t')).toHaveTextContent('a');
  });

  it('logout clears', () => {
    render(
      <AuthProvider>
        <T />
      </AuthProvider>,
    );
    fireEvent.click(screen.getByText('in'));
    fireEvent.click(screen.getByText('out'));
    expect(screen.getByTestId('t')).toHaveTextContent('none');
  });

  it('persists to localStorage', () => {
    render(
      <AuthProvider>
        <T />
      </AuthProvider>,
    );
    fireEvent.click(screen.getByText('in'));
    expect(localStorage.getItem('sc_token')).toBe('a');
  });

  it('clears localStorage on logout', () => {
    render(
      <AuthProvider>
        <T />
      </AuthProvider>,
    );
    fireEvent.click(screen.getByText('in'));
    fireEvent.click(screen.getByText('out'));
    expect(localStorage.getItem('sc_token')).toBeNull();
  });
});
