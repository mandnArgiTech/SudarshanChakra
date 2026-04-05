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

function HasModuleProbe({ id }: { id: string | null }) {
  const { hasModule } = useAuth();
  return <span data-testid="hm">{String(hasModule(id))}</span>;
}

function LoginWithModules({ modules }: { modules: string[] }) {
  const { login } = useAuth();
  return (
    <button
      type="button"
      onClick={() =>
        login('tok', 'ref', {
          id: '1',
          username: 'u',
          email: '',
          role: 'viewer',
          farmId: 'f1',
          active: true,
          modules,
        })
      }
    >
      in-mod
    </button>
  );
}

describe('useAuth hasModule', () => {
  beforeEach(() => localStorage.clear());

  it('null module is always allowed', () => {
    render(
      <AuthProvider>
        <HasModuleProbe id={null} />
      </AuthProvider>,
    );
    expect(screen.getByTestId('hm')).toHaveTextContent('true');
  });

  it('missing modules on user allows any module (legacy)', () => {
    render(
      <AuthProvider>
        <>
          <T />
          <HasModuleProbe id="cameras" />
        </>
      </AuthProvider>,
    );
    fireEvent.click(screen.getByText('in'));
    expect(screen.getByTestId('hm')).toHaveTextContent('true');
  });

  it('restricts to listed modules when modules non-empty', () => {
    render(
      <AuthProvider>
        <>
          <LoginWithModules modules={['water', 'alerts']} />
          <HasModuleProbe id="cameras" />
          <HasModuleProbe id="water" />
        </>
      </AuthProvider>,
    );
    fireEvent.click(screen.getByText('in-mod'));
    expect(screen.getAllByTestId('hm')[0]).toHaveTextContent('false');
    expect(screen.getAllByTestId('hm')[1]).toHaveTextContent('true');
  });

  it('empty modules array allows all (legacy)', () => {
    render(
      <AuthProvider>
        <>
          <LoginWithModules modules={[]} />
          <HasModuleProbe id="cameras" />
        </>
      </AuthProvider>,
    );
    fireEvent.click(screen.getByText('in-mod'));
    expect(screen.getByTestId('hm')).toHaveTextContent('true');
  });
});
