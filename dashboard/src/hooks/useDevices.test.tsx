import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import apiClient from '@/api/client';
import { useNodes, useCameras } from '@/api/devices';
import type { EdgeNode, Camera } from '@/types';

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchInterval: false },
  },
});

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

const sampleNodes: EdgeNode[] = [
  {
    id: 'node-a',
    farmId: 'f1',
    displayName: 'Node A',
    vpnIp: '10.0.0.1',
    localIp: '192.168.0.1',
    status: 'online',
    lastHeartbeat: null,
    hardwareInfo: null,
    config: null,
    createdAt: '',
    updatedAt: '',
  },
];

const sampleCameras: Camera[] = [
  {
    id: 'CAM-1',
    nodeId: 'node-a',
    name: 'Gate',
    rtspUrl: '',
    model: null,
    locationDescription: null,
    fpsTarget: 2,
    resolution: '640x480',
    enabled: true,
    status: 'active',
    createdAt: '',
  },
];

describe('useDevices (nodes & cameras)', () => {
  beforeEach(() => {
    queryClient.clear();
    vi.mocked(apiClient.get).mockReset();
  });

  it('useNodes returns edge nodes from API', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: sampleNodes });

    const { result } = renderHook(() => useNodes(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].displayName).toBe('Node A');
    expect(apiClient.get).toHaveBeenCalledWith('/nodes');
  });

  it('useCameras returns camera list from API', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: sampleCameras });

    const { result } = renderHook(() => useCameras(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0].name).toBe('Gate');
    expect(apiClient.get).toHaveBeenCalledWith('/cameras');
  });
});
