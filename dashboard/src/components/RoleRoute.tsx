import { Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import type { UserRole } from '@/types';

export default function RoleRoute({
  roles,
  children,
}: {
  roles: readonly UserRole[];
  children: React.ReactNode;
}) {
  const { user } = useAuth();
  const r = user?.role;
  if (!r || !roles.includes(r)) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
