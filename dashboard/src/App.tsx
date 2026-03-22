import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import Layout from '@/components/Layout/Layout';
import LoginPage from '@/pages/LoginPage';
import DashboardPage from '@/pages/DashboardPage';
import AlertsPage from '@/pages/AlertsPage';
import CamerasPage from '@/pages/CamerasPage';
import ZonesPage from '@/pages/ZonesPage';
import DevicesPage from '@/pages/DevicesPage';
import SirenPage from '@/pages/SirenPage';
import WorkersPage from '@/pages/WorkersPage';
import AnalyticsPage from '@/pages/AnalyticsPage';
import SettingsPage from '@/pages/SettingsPage';
import WaterPage from '@/pages/WaterPage';
import MotorControlPage from '@/pages/MotorControlPage';
import ModuleRoute from '@/components/ModuleRoute';
import RoleRoute from '@/components/RoleRoute';
import AdminFarmsPage from '@/pages/AdminFarmsPage';
import AdminUsersPage from '@/pages/AdminUsersPage';
import AdminAuditPage from '@/pages/AdminAuditPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route
          index
          element={
            <ModuleRoute module="alerts">
              <DashboardPage />
            </ModuleRoute>
          }
        />
        <Route
          path="alerts"
          element={
            <ModuleRoute module="alerts">
              <AlertsPage />
            </ModuleRoute>
          }
        />
        <Route
          path="cameras"
          element={
            <ModuleRoute module="cameras">
              <CamerasPage />
            </ModuleRoute>
          }
        />
        <Route
          path="zones"
          element={
            <ModuleRoute module="zones">
              <ZonesPage />
            </ModuleRoute>
          }
        />
        <Route
          path="devices"
          element={
            <ModuleRoute module="devices">
              <DevicesPage />
            </ModuleRoute>
          }
        />
        <Route
          path="siren"
          element={
            <ModuleRoute module="sirens">
              <SirenPage />
            </ModuleRoute>
          }
        />
        <Route
          path="workers"
          element={
            <ModuleRoute module="workers">
              <WorkersPage />
            </ModuleRoute>
          }
        />
        <Route
          path="analytics"
          element={
            <ModuleRoute module="analytics">
              <AnalyticsPage />
            </ModuleRoute>
          }
        />
        <Route path="settings" element={<SettingsPage />} />
        <Route
          path="water"
          element={
            <ModuleRoute module="water">
              <WaterPage />
            </ModuleRoute>
          }
        />
        <Route
          path="water/motors"
          element={
            <ModuleRoute module="pumps">
              <MotorControlPage />
            </ModuleRoute>
          }
        />
        <Route
          path="admin/farms"
          element={
            <RoleRoute roles={['super_admin']}>
              <AdminFarmsPage />
            </RoleRoute>
          }
        />
        <Route
          path="admin/users"
          element={
            <RoleRoute roles={['super_admin', 'admin']}>
              <AdminUsersPage />
            </RoleRoute>
          }
        />
        <Route
          path="admin/audit"
          element={
            <RoleRoute roles={['super_admin', 'admin', 'manager']}>
              <AdminAuditPage />
            </RoleRoute>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
