import { useQuery } from '@tanstack/react-query';
import apiClient from './client';
import type { EdgeNode, Camera, Zone, WorkerTag } from '@/types';

/** Paths match api-gateway → device-service (e.g. GET /api/v1/cameras), not /devices/... */
async function fetchNodes(): Promise<EdgeNode[]> {
  const { data } = await apiClient.get<EdgeNode[]>('/nodes');
  return data;
}

async function fetchCameras(): Promise<Camera[]> {
  const { data } = await apiClient.get<Camera[]>('/cameras');
  return data;
}

async function fetchZones(): Promise<Zone[]> {
  const { data } = await apiClient.get<Zone[]>('/zones');
  return data;
}

async function fetchTags(): Promise<WorkerTag[]> {
  const { data } = await apiClient.get<WorkerTag[]>('/tags');
  return data;
}

export function useNodes() {
  return useQuery({
    queryKey: ['nodes'],
    queryFn: fetchNodes,
    refetchInterval: 30_000,
  });
}

export function useCameras() {
  return useQuery({
    queryKey: ['cameras'],
    queryFn: fetchCameras,
    refetchInterval: 30_000,
  });
}

export function useZones() {
  return useQuery({
    queryKey: ['zones'],
    queryFn: fetchZones,
  });
}

export function useTags() {
  return useQuery({
    queryKey: ['tags'],
    queryFn: fetchTags,
    refetchInterval: 30_000,
  });
}
