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
        <Route index element={<DashboardPage />} />
        <Route path="alerts" element={<AlertsPage />} />
        <Route path="cameras" element={<CamerasPage />} />
        <Route path="zones" element={<ZonesPage />} />
        <Route path="devices" element={<DevicesPage />} />
        <Route path="siren" element={<SirenPage />} />
        <Route path="workers" element={<WorkersPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="water" element={<WaterPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
