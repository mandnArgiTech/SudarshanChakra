import { useQuery } from '@tanstack/react-query';
import apiClient from './client';
import type { EdgeNode, Camera, Zone, WorkerTag } from '@/types';

async function fetchNodes(): Promise<EdgeNode[]> {
  const { data } = await apiClient.get<EdgeNode[]>('/devices/nodes');
  return data;
}

async function fetchCameras(): Promise<Camera[]> {
  const { data } = await apiClient.get<Camera[]>('/devices/cameras');
  return data;
}

async function fetchZones(): Promise<Zone[]> {
  const { data } = await apiClient.get<Zone[]>('/devices/zones');
  return data;
}

async function fetchTags(): Promise<WorkerTag[]> {
  const { data } = await apiClient.get<WorkerTag[]>('/devices/tags');
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
