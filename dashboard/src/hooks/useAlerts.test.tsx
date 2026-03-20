import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import apiClient from '@/api/client';
import { useAlerts } from '@/api/alerts';
import type { Alert, Page } from '@/types';

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}));

const emptyPage: Page<Alert> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  number: 0,
  first: true,
  last: true,
};

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchInterval: false },
  },
});

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('useAlerts', () => {
  beforeEach(() => {
    queryClient.clear();
    vi.mocked(apiClient.get).mockReset();
  });

  it('loads alert page on success', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { ...emptyPage, totalElements: 5, content: [] },
    });

    const { result } = renderHook(() => useAlerts({ size: 10 }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.totalElements).toBe(5);
    expect(apiClient.get).toHaveBeenCalledWith(expect.stringMatching(/\/alerts\?/));
  });

  it('surfaces error state when request fails', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('network'));

    const { result } = renderHook(() => useAlerts(), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeDefined();
  });
});
