import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import AnalyticsPage from './AnalyticsPage';

describe('AnalyticsPage', () => {
  it('renders line chart section', () => {
    render(<AnalyticsPage />);
    expect(screen.getByText('Alerts Over Time (24h)')).toBeInTheDocument();
  });

  it('renders bar and pie chart sections', () => {
    render(<AnalyticsPage />);
    expect(screen.getByText('Alerts by Zone')).toBeInTheDocument();
    expect(screen.getByText('Detection Classes')).toBeInTheDocument();
  });

  it('renders summary statistics grid', () => {
    render(<AnalyticsPage />);
    expect(screen.getByText('Summary Statistics')).toBeInTheDocument();
    expect(screen.getByText('Total Alerts (24h)')).toBeInTheDocument();
    expect(screen.getByText('143')).toBeInTheDocument();
  });
});
