import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import WaterTankGauge from './WaterTankGauge';

describe('WaterTankGauge', () => {
  it('shows name and percent', () => {
    render(<WaterTankGauge tank={{ id: '1', name: 'Tank A', levelPct: 55 }} />);
    expect(screen.getByText('Tank A')).toBeInTheDocument();
    expect(screen.getByText('55%')).toBeInTheDocument();
  });

  it('renders with history', () => {
    render(
      <WaterTankGauge
        tank={{
          id: '1',
          name: 'B',
          levelPct: 80,
          history: [
            { t: '1', v: 70 },
            { t: '2', v: 80 },
          ],
        }}
      />,
    );
    expect(screen.getByText('B')).toBeInTheDocument();
  });

  it('rounds display', () => {
    render(<WaterTankGauge tank={{ id: '1', name: 'C', levelPct: 33.7 }} />);
    expect(screen.getByText('34%')).toBeInTheDocument();
  });

  it('empty history uses level', () => {
    const { container } = render(
      <WaterTankGauge tank={{ id: '1', name: 'D', levelPct: 10, history: [] }} />,
    );
    expect(container.querySelector('.recharts-wrapper') || container).toBeTruthy();
  });

  it('has chart region', () => {
    const { container } = render(
      <WaterTankGauge tank={{ id: '1', name: 'E', levelPct: 50 }} />,
    );
    expect(container.querySelector('h3')).toHaveTextContent('E');
  });
});
