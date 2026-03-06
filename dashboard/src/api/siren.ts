import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from './client';
import type { SirenAction, SirenRequest, SirenResponse, Page } from '@/types';

async function triggerSiren(request: SirenRequest): Promise<SirenResponse> {
  const { data } = await apiClient.post<SirenResponse>('/siren/trigger', request);
  return data;
}

async function stopSiren(request: SirenRequest): Promise<SirenResponse> {
  const { data } = await apiClient.post<SirenResponse>('/siren/stop', request);
  return data;
}

async function fetchSirenHistory(page = 0, size = 20): Promise<Page<SirenAction>> {
  const { data } = await apiClient.get<Page<SirenAction>>(`/siren/history?page=${page}&size=${size}&sort=createdAt,desc`);
  return data;
}

export function useTriggerSiren() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: triggerSiren,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sirenHistory'] });
    },
  });
}

export function useStopSiren() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: stopSiren,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sirenHistory'] });
    },
  });
}

export function useSirenHistory(page = 0, size = 20) {
  return useQuery({
    queryKey: ['sirenHistory', page, size],
    queryFn: () => fetchSirenHistory(page, size),
  });
}
