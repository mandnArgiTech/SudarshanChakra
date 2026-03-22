import { Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';

/**
 * Renders children only if the current user has the SaaS module enabled (or full legacy access).
 */
export default function ModuleRoute({
  module: moduleId,
  children,
}: {
  module: string | null;
  children: React.ReactNode;
}) {
  const { hasModule } = useAuth();
  if (!hasModule(moduleId)) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
