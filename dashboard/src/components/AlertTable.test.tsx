import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AlertTable from './AlertTable';
import type { Alert } from '@/types';

const newAlert: Alert = {
  id: 't1',
  nodeId: 'node-a',
  cameraId: 'CAM-01',
  zoneId: 'z1',
  zoneName: 'Test Zone',
  zoneType: 'danger',
  priority: 'critical',
  detectionClass: 'person',
  confidence: 0.91,
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

describe('AlertTable', () => {
  it('renders column headers on desktop layout', () => {
    render(
      <AlertTable
        alerts={[newAlert]}
        onAcknowledge={vi.fn()}
        onResolve={vi.fn()}
      />,
    );
    expect(screen.getByText('Priority')).toBeInTheDocument();
    expect(screen.getByText('Detection')).toBeInTheDocument();
  });

  it('shows alert row content', () => {
    render(<AlertTable alerts={[newAlert]} />);
    expect(screen.getAllByText(/person — Test Zone/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('CAM-01').length).toBeGreaterThanOrEqual(1);
  });

  it('calls onAcknowledge and onResolve from mobile actions', () => {
    const onAck = vi.fn();
    const onRes = vi.fn();
    render(
      <AlertTable alerts={[newAlert]} onAcknowledge={onAck} onResolve={onRes} />,
    );
    const ackBtns = screen.getAllByRole('button', { name: /acknowledge/i });
    fireEvent.click(ackBtns[0]);
    const resolveBtns = screen.getAllByRole('button', { name: /^resolve$/i });
    fireEvent.click(resolveBtns[0]);
    expect(onAck).toHaveBeenCalledWith('t1');
    expect(onRes).toHaveBeenCalledWith('t1');
  });

  it('shows empty state when no alerts', () => {
    render(<AlertTable alerts={[]} />);
    const empty = screen.getAllByText('No alerts found');
    expect(empty.length).toBeGreaterThanOrEqual(1);
  });
});
