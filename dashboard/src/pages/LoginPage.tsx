import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Siren } from 'lucide-react';
import { useLogin } from '@/api/auth';
import { useAuth } from '@/hooks/useAuth';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const { login } = useAuth();
  const loginMutation = useLogin();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');

    try {
      const data = await loginMutation.mutateAsync({ username, password });
      login(data.token, data.refreshToken, data.user);
      navigate('/');
    } catch {
      setError('Invalid credentials. Please try again.');
    }
  }

  return (
    <div className="min-h-screen bg-sc-bg flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-sc-accent/10 border border-sc-accent/30 mb-4">
            <Siren size={32} className="text-sc-accent" />
          </div>
          <h1 className="text-sc-accent text-2xl font-extrabold font-mono tracking-wider">
            SUDARSHAN
          </h1>
          <p className="text-sc-text-muted text-xs font-mono tracking-[4px] mt-1">
            CHAKRA
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="bg-sc-surface border border-sc-border rounded-2xl p-8"
        >
          <h2 className="text-sc-text text-lg font-semibold mb-6">Sign in to Dashboard</h2>

          {error && (
            <div className="mb-4 p-3 rounded-lg bg-sc-critical/10 border border-sc-critical/30 text-sc-critical text-sm">
              {error}
            </div>
          )}

          <div className="mb-4">
            <label className="block text-sc-text-dim text-xs uppercase tracking-wider font-mono mb-2">
              Username
            </label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-3 rounded-lg bg-sc-surface-alt border border-sc-border text-sc-text text-sm focus:outline-none focus:border-sc-accent/50 transition-colors"
              placeholder="Enter username"
              required
            />
          </div>

          <div className="mb-6">
            <label className="block text-sc-text-dim text-xs uppercase tracking-wider font-mono mb-2">
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-3 rounded-lg bg-sc-surface-alt border border-sc-border text-sc-text text-sm focus:outline-none focus:border-sc-accent/50 transition-colors"
              placeholder="Enter password"
              required
            />
          </div>

          <button
            type="submit"
            disabled={loginMutation.isPending}
            className="w-full py-3 rounded-lg bg-sc-accent text-sc-bg font-bold text-sm uppercase tracking-wider hover:bg-sc-accent/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loginMutation.isPending ? 'Signing in...' : 'Sign In'}
          </button>

          <p className="text-center text-sc-text-muted text-xs mt-4">
            Farm Security Monitoring System
          </p>
        </form>
      </div>
    </div>
  );
}
