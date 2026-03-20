import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from './client';

export interface WaterMotorDto {
  id: string;
  farmId: string;
  displayName: string;
  deviceTag: string | null;
  location: string | null;
  controlType: string;
  state: string;
  mode: string;
  runSeconds: number;
  status: string;
  autoMode: boolean;
  pumpOnPercent: number;
  pumpOffPercent: number;
}

export function useWaterMotors() {
  return useQuery({
    queryKey: ['water-motors'],
    queryFn: async () => {
      const { data } = await apiClient.get<WaterMotorDto[]>('/water/motors');
      return Array.isArray(data) ? data : [];
    },
    refetchInterval: 15_000,
  });
}

export function useMotorCommand() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ motorId, command }: { motorId: string; command: string }) => {
      const { data } = await apiClient.post<Record<string, string>>(
        `/water/motors/${motorId}/command`,
        { command },
      );
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['water-motors'] });
    },
  });
}
