import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from './client';
import type { Alert, Page } from '@/types';

interface AlertFilters {
  priority?: string;
  status?: string;
  nodeId?: string;
  page?: number;
  size?: number;
}

async function fetchAlerts(filters: AlertFilters = {}): Promise<Page<Alert>> {
  const params = new URLSearchParams();
  if (filters.priority && filters.priority !== 'all') params.set('priority', filters.priority);
  if (filters.status && filters.status !== 'all') params.set('status', filters.status);
  if (filters.nodeId) params.set('nodeId', filters.nodeId);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 20));
  params.set('sort', 'createdAt,desc');
  const { data } = await apiClient.get<Page<Alert>>(`/alerts?${params}`);
  return data;
}

async function fetchAlert(id: string): Promise<Alert> {
  const { data } = await apiClient.get<Alert>(`/alerts/${id}`);
  return data;
}

async function acknowledgeAlert(id: string): Promise<Alert> {
  const { data } = await apiClient.patch<Alert>(`/alerts/${id}/acknowledge`, {});
  return data;
}

async function resolveAlert(id: string): Promise<Alert> {
  const { data } = await apiClient.patch<Alert>(`/alerts/${id}/resolve`, {});
  return data;
}

async function markFalsePositive(id: string): Promise<Alert> {
  const { data } = await apiClient.patch<Alert>(`/alerts/${id}/false-positive`, {});
  return data;
}

export function useAlerts(filters: AlertFilters = {}) {
  return useQuery({
    queryKey: ['alerts', filters],
    queryFn: () => fetchAlerts(filters),
    refetchInterval: 10_000,
  });
}

export function useAlert(id: string) {
  return useQuery({
    queryKey: ['alert', id],
    queryFn: () => fetchAlert(id),
    enabled: !!id,
  });
}

export function useAcknowledgeAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: acknowledgeAlert,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
    },
  });
}

export function useResolveAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: resolveAlert,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
    },
  });
}

export function useMarkFalsePositive() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: markFalsePositive,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
    },
  });
}
