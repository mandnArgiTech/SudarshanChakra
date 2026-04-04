import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from './client';

export interface MdmDevice {
  id: string;
  farmId: string;
  userId?: string;
  deviceName: string;
  androidId: string;
  model?: string;
  osVersion?: string;
  appVersion?: string;
  serialNumber?: string;
  imei?: string;
  phoneNumber?: string;
  isDeviceOwner: boolean;
  isLockTaskActive: boolean;
  whitelistedApps: string[];
  policies: Record<string, boolean> | string | null;
  lastHeartbeat?: string;
  lastTelemetrySync?: string;
  lastLatitude?: number;
  lastLongitude?: number;
  lastLocationAt?: string;
  locationIntervalSec?: number;
  mqttClientId?: string;
  status: 'pending' | 'active' | 'locked' | 'wiped' | 'decommissioned';
  provisionedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface AppUsageRecord {
  date: string;
  packageName: string;
  appLabel: string;
  foregroundTimeSec: number;
  launchCount: number;
  category: string;
}

export interface CallLogRecord {
  phoneNumberMasked: string;
  callType: 'incoming' | 'outgoing' | 'missed' | 'rejected';
  callTimestamp: string;
  durationSec: number;
  contactName: string;
}

export interface ScreenTimeRecord {
  date: string;
  totalScreenTimeSec: number;
  unlockCount: number;
}

export interface MdmCommandRecord {
  id: string;
  deviceId: string;
  command: string;
  payload?: string;
  status: string;
  issuedBy?: string;
  issuedAt: string;
  deliveredAt?: string;
  executedAt?: string;
  result?: string;
}

export interface LocationRecord {
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
  altitudeMeters?: number;
  speedMps?: number;
  provider?: string;
  batteryPercent?: number;
  recordedAt: string;
}

async function fetchMdmDevices(): Promise<MdmDevice[]> {
  const { data } = await apiClient.get<MdmDevice[]>('/mdm/devices');
  return data;
}

async function fetchMdmDevice(id: string): Promise<MdmDevice> {
  const { data } = await apiClient.get<MdmDevice>(`/mdm/devices/${id}`);
  return data;
}

async function fetchDeviceUsage(id: string, from: string, to: string): Promise<AppUsageRecord[]> {
  const { data } = await apiClient.get<AppUsageRecord[]>(`/mdm/devices/${id}/usage`, {
    params: { from, to },
  });
  return data;
}

async function fetchDeviceCalls(id: string, from: string, to: string): Promise<CallLogRecord[]> {
  const { data } = await apiClient.get<CallLogRecord[]>(`/mdm/devices/${id}/calls`, {
    params: { from, to },
  });
  return data;
}

async function fetchDeviceScreenTime(id: string, from: string, to: string): Promise<ScreenTimeRecord[]> {
  const { data } = await apiClient.get<ScreenTimeRecord[]>(`/mdm/devices/${id}/screentime`, {
    params: { from, to },
  });
  return data;
}

async function fetchDeviceCommands(id: string): Promise<MdmCommandRecord[]> {
  const { data } = await apiClient.get<MdmCommandRecord[]>(`/mdm/commands/${id}`);
  return data;
}

async function fetchDeviceLocation(id: string, from: string, to: string): Promise<LocationRecord[]> {
  const { data } = await apiClient.get<LocationRecord[]>(`/mdm/devices/${id}/location`, {
    params: { from, to },
  });
  return data;
}

interface SendCommandBody {
  deviceId: string;
  command: string;
  payload?: Record<string, unknown>;
}

async function sendCommand(body: SendCommandBody): Promise<MdmCommandRecord> {
  const { data } = await apiClient.post<MdmCommandRecord>('/mdm/commands', body);
  return data;
}

export function useMdmDevices() {
  return useQuery({
    queryKey: ['mdm-devices'],
    queryFn: fetchMdmDevices,
    refetchInterval: 30_000,
  });
}

export function useMdmDevice(id: string) {
  return useQuery({
    queryKey: ['mdm-device', id],
    queryFn: () => fetchMdmDevice(id),
    enabled: !!id,
  });
}

export function useDeviceUsage(id: string, from: string, to: string) {
  return useQuery({
    queryKey: ['mdm-usage', id, from, to],
    queryFn: () => fetchDeviceUsage(id, from, to),
    enabled: !!id,
  });
}

export function useDeviceCalls(id: string, from: string, to: string) {
  return useQuery({
    queryKey: ['mdm-calls', id, from, to],
    queryFn: () => fetchDeviceCalls(id, from, to),
    enabled: !!id,
  });
}

export function useDeviceScreenTime(id: string, from: string, to: string) {
  return useQuery({
    queryKey: ['mdm-screentime', id, from, to],
    queryFn: () => fetchDeviceScreenTime(id, from, to),
    enabled: !!id,
  });
}

export function useDeviceCommands(id: string) {
  return useQuery({
    queryKey: ['mdm-commands', id],
    queryFn: () => fetchDeviceCommands(id),
    enabled: !!id,
    refetchInterval: 15_000,
  });
}

export function useDeviceLocation(id: string, from: string, to: string) {
  return useQuery({
    queryKey: ['mdm-location', id, from, to],
    queryFn: () => fetchDeviceLocation(id, from, to),
    enabled: !!id,
    refetchInterval: 60_000,
    retry: false,
  });
}

export function useSendCommand() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: sendCommand,
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['mdm-commands', vars.deviceId] });
      qc.invalidateQueries({ queryKey: ['mdm-device', vars.deviceId] });
    },
  });
}
