export const PRIORITY_COLORS: Record<string, string> = {
  critical: '#ef4444',
  high: '#f97316',
  warning: '#eab308',
  info: '#3b82f6',
};

export const PRIORITY_BG: Record<string, string> = {
  critical: 'bg-red-500/10 border-red-500/20',
  high: 'bg-orange-500/10 border-orange-500/20',
  warning: 'bg-yellow-500/10 border-yellow-500/20',
  info: 'bg-blue-500/10 border-blue-500/20',
};

export const PRIORITY_TEXT: Record<string, string> = {
  critical: 'text-sc-critical',
  high: 'text-sc-high',
  warning: 'text-sc-warning',
  info: 'text-sc-info',
};

export const STATUS_COLORS: Record<string, string> = {
  new: '#ef4444',
  acknowledged: '#f59e0b',
  resolved: '#22c55e',
  false_positive: '#64748b',
};

export const STATUS_TEXT: Record<string, string> = {
  new: 'text-sc-critical',
  acknowledged: 'text-sc-accent',
  resolved: 'text-sc-success',
  false_positive: 'text-sc-text-muted',
};

export function priorityColor(priority: string): string {
  return PRIORITY_COLORS[priority] || PRIORITY_COLORS.info;
}

export function statusColor(status: string): string {
  return STATUS_COLORS[status] || STATUS_COLORS.false_positive;
}
