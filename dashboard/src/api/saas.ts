import apiClient from './client';
import type { AuditLogRow, Farm, Page, User } from '@/types';

export async function fetchFarms(): Promise<Farm[]> {
  const { data } = await apiClient.get<Farm[]>('/farms');
  return data;
}

export async function fetchAuditPage(page = 0, size = 50, action?: string): Promise<Page<AuditLogRow>> {
  const { data } = await apiClient.get<Page<AuditLogRow>>('/audit', {
    params: { page, size, ...(action ? { action } : {}) },
  });
  return data;
}

export async function fetchUsersList(): Promise<User[]> {
  const { data } = await apiClient.get<User[]>('/users');
  return data;
}
