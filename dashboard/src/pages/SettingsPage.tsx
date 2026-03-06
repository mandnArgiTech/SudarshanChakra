import { useState } from 'react';
import { Save, Bell, Shield, Monitor, Wifi } from 'lucide-react';

export default function SettingsPage() {
  const [autoSiren, setAutoSiren] = useState(true);
  const [notifications, setNotifications] = useState(true);
  const [darkMode, setDarkMode] = useState(true);
  const [criticalThreshold, setCriticalThreshold] = useState('0.85');
  const [dedupWindow, setDedupWindow] = useState('30');

  return (
    <div className="max-w-3xl">
      <div className="space-y-6">
        <div className="bg-sc-surface border border-sc-border rounded-xl p-6">
          <div className="flex items-center gap-3 mb-4">
            <Bell size={20} className="text-sc-accent" />
            <h3 className="text-sc-text text-base font-semibold">Notification Settings</h3>
          </div>
          <div className="space-y-4">
            <label className="flex items-center justify-between">
              <div>
                <div className="text-sc-text text-sm">Push Notifications</div>
                <div className="text-sc-text-muted text-xs">Receive alerts on your device</div>
              </div>
              <button
                onClick={() => setNotifications(!notifications)}
                className={`w-11 h-6 rounded-full transition-colors relative ${
                  notifications ? 'bg-sc-accent' : 'bg-sc-border'
                }`}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-white absolute top-1 transition-all ${
                    notifications ? 'left-6' : 'left-1'
                  }`}
                />
              </button>
            </label>
            <div>
              <label className="text-sc-text-dim text-xs uppercase tracking-wider font-mono block mb-2">
                Critical Alert Confidence Threshold
              </label>
              <input
                type="number"
                step="0.05"
                min="0.5"
                max="1.0"
                value={criticalThreshold}
                onChange={(e) => setCriticalThreshold(e.target.value)}
                className="w-full px-4 py-2.5 rounded-lg bg-sc-surface-alt border border-sc-border text-sc-text text-sm font-mono focus:outline-none focus:border-sc-accent/50 transition-colors"
              />
            </div>
          </div>
        </div>

        <div className="bg-sc-surface border border-sc-border rounded-xl p-6">
          <div className="flex items-center gap-3 mb-4">
            <Shield size={20} className="text-sc-critical" />
            <h3 className="text-sc-text text-base font-semibold">Siren Settings</h3>
          </div>
          <div className="space-y-4">
            <label className="flex items-center justify-between">
              <div>
                <div className="text-sc-text text-sm">Auto-Trigger Siren</div>
                <div className="text-sc-text-muted text-xs">
                  Automatically trigger siren on critical alerts
                </div>
              </div>
              <button
                onClick={() => setAutoSiren(!autoSiren)}
                className={`w-11 h-6 rounded-full transition-colors relative ${
                  autoSiren ? 'bg-sc-critical' : 'bg-sc-border'
                }`}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-white absolute top-1 transition-all ${
                    autoSiren ? 'left-6' : 'left-1'
                  }`}
                />
              </button>
            </label>
          </div>
        </div>

        <div className="bg-sc-surface border border-sc-border rounded-xl p-6">
          <div className="flex items-center gap-3 mb-4">
            <Wifi size={20} className="text-sc-info" />
            <h3 className="text-sc-text text-base font-semibold">Detection Settings</h3>
          </div>
          <div>
            <label className="text-sc-text-dim text-xs uppercase tracking-wider font-mono block mb-2">
              Deduplication Window (seconds)
            </label>
            <input
              type="number"
              min="5"
              max="300"
              value={dedupWindow}
              onChange={(e) => setDedupWindow(e.target.value)}
              className="w-full px-4 py-2.5 rounded-lg bg-sc-surface-alt border border-sc-border text-sc-text text-sm font-mono focus:outline-none focus:border-sc-accent/50 transition-colors"
            />
          </div>
        </div>

        <div className="bg-sc-surface border border-sc-border rounded-xl p-6">
          <div className="flex items-center gap-3 mb-4">
            <Monitor size={20} className="text-sc-success" />
            <h3 className="text-sc-text text-base font-semibold">Display Settings</h3>
          </div>
          <label className="flex items-center justify-between">
            <div>
              <div className="text-sc-text text-sm">Dark Mode</div>
              <div className="text-sc-text-muted text-xs">Use dark theme (recommended)</div>
            </div>
            <button
              onClick={() => setDarkMode(!darkMode)}
              className={`w-11 h-6 rounded-full transition-colors relative ${
                darkMode ? 'bg-sc-success' : 'bg-sc-border'
              }`}
            >
              <div
                className={`w-4 h-4 rounded-full bg-white absolute top-1 transition-all ${
                  darkMode ? 'left-6' : 'left-1'
                }`}
              />
            </button>
          </label>
        </div>

        <button className="flex items-center gap-2 px-6 py-3 rounded-lg bg-sc-accent text-sc-bg font-bold text-sm uppercase tracking-wider hover:bg-sc-accent/90 transition-colors">
          <Save size={16} />
          Save Settings
        </button>
      </div>
    </div>
  );
}
