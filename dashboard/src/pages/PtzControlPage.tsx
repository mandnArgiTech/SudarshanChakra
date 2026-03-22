import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import LiveCameraFeed from '@/components/LiveCameraFeed';
import PtzJoystick from '@/components/PtzJoystick';

export default function PtzControlPage() {
  const { cameraId } = useParams<{ cameraId: string }>();
  const navigate = useNavigate();

  if (!cameraId) {
    return <p className="text-sc-text-muted p-4">No camera selected</p>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate('/cameras')}
          className="p-2 rounded-lg text-sc-text-muted hover:bg-sc-surface-alt hover:text-sc-text"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-xl font-semibold text-sc-text">
          PTZ Control: {cameraId}
        </h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2">
          <div className="relative w-full aspect-video rounded-xl border border-sc-border overflow-hidden">
            <LiveCameraFeed cameraId={cameraId} className="w-full h-full" />
          </div>
        </div>

        <div className="bg-sc-surface border border-sc-border rounded-xl p-4">
          <h2 className="text-sm font-semibold text-sc-text mb-4">Controls</h2>
          <PtzJoystick cameraId={cameraId} />
        </div>
      </div>
    </div>
  );
}
