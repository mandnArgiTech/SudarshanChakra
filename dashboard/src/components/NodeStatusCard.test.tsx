import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import NodeStatusCard from './NodeStatusCard';
import type { EdgeNode } from '@/types';

const onlineNode: EdgeNode = {
  id: 'n1',
  farmId: 'f1',
  displayName: 'Edge Alpha',
  vpnIp: '10.8.0.5',
  localIp: '192.168.1.2',
  status: 'online',
  lastHeartbeat: new Date().toISOString(),
  hardwareInfo: JSON.stringify({ gpu_util: '40%' }),
  config: null,
  createdAt: '',
  updatedAt: '',
};

const offlineNode: EdgeNode = {
  ...onlineNode,
  id: 'n2',
  displayName: 'Edge Beta',
  status: 'offline',
  hardwareInfo: null,
};

describe('NodeStatusCard', () => {
  it('renders display name and VPN IP', () => {
    render(<NodeStatusCard node={onlineNode} />);
    expect(screen.getByText('Edge Alpha')).toBeInTheDocument();
    expect(screen.getByText('10.8.0.5')).toBeInTheDocument();
  });

  it('shows GPU util from hardware info', () => {
    render(<NodeStatusCard node={onlineNode} />);
    expect(screen.getByText('40%')).toBeInTheDocument();
  });

  it('uses success styling copy for online status', () => {
    render(<NodeStatusCard node={onlineNode} />);
    expect(screen.getByText('online')).toHaveClass('text-sc-success');
  });

  it('uses critical styling for offline status', () => {
    render(<NodeStatusCard node={offlineNode} />);
    expect(screen.getByText('offline')).toHaveClass('text-sc-critical');
  });
});
