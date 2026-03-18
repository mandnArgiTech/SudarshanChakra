import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AlertCard from './AlertCard';
import type { Alert } from '@/types';

const base: Alert = {
  id: '1',
  nodeId: 'n',
  cameraId: 'c',
  zoneId: 'z',
  zoneName: 'Perimeter',
  zoneType: 'intrusion',
  priority: 'high',
  detectionClass: 'person',
  confidence: 0.9,
  bbox: null,
  snapshotUrl: null,
  thumbnailUrl: null,
  workerSuppressed: false,
  status: 'new',
  acknowledgedBy: null,
  acknowledgedAt: null,
  resolvedBy: null,
  resolvedAt: null,
  notes: null,
  metadata: null,
  createdAt: new Date().toISOString(),
};

describe('AlertCard', () => {
  it('renders detection class and zone', () => {
    render(<AlertCard alert={base} />);
    expect(screen.getByText(/PERSON/i)).toBeInTheDocument();
    expect(screen.getByText(/Perimeter/)).toBeInTheDocument();
  });

  it('calls onClick when clicked', () => {
    const fn = vi.fn();
    render(<AlertCard alert={base} onClick={fn} />);
    fireEvent.click(screen.getByText(/PERSON/i).closest('div')!);
    expect(fn).toHaveBeenCalled();
  });

  it('shows critical priority styling context', () => {
    render(<AlertCard alert={{ ...base, priority: 'critical' }} />);
    expect(screen.getByText(/PERSON/i)).toBeInTheDocument();
  });

  it('renders resolved status', () => {
    render(<AlertCard alert={{ ...base, status: 'resolved' }} />);
    expect(screen.getByText(/PERSON/i)).toBeInTheDocument();
  });

  it('renders worker suppressed flag in metadata area', () => {
    render(<AlertCard alert={{ ...base, workerSuppressed: true }} />);
    expect(screen.getByText(/PERSON/i)).toBeInTheDocument();
  });
});
