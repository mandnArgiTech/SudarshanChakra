import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import SettingsPage from './SettingsPage';

describe('SettingsPage', () => {
  it('renders notification and siren settings sections', () => {
    render(<SettingsPage />);
    expect(screen.getByText('Notification Settings')).toBeInTheDocument();
    expect(screen.getByText('Siren Settings')).toBeInTheDocument();
    expect(screen.getByText('Detection Settings')).toBeInTheDocument();
  });

  it('renders threshold and dedup inputs', () => {
    render(<SettingsPage />);
    expect(screen.getByDisplayValue('0.85')).toBeInTheDocument();
    expect(screen.getByDisplayValue('30')).toBeInTheDocument();
  });

  it('renders save settings button', () => {
    render(<SettingsPage />);
    expect(screen.getByRole('button', { name: /save settings/i })).toBeInTheDocument();
  });

  it('toggles push notifications control', () => {
    render(<SettingsPage />);
    const toggles = screen.getAllByRole('button').filter((el) => el.className.includes('rounded-full'));
    expect(toggles.length).toBeGreaterThan(0);
    fireEvent.click(toggles[0]);
  });
});
