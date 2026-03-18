import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import SirenButton from './SirenButton';

describe('SirenButton', () => {
  it('renders label', () => {
    render(<SirenButton label="Trigger" onClick={() => {}} />);
    expect(screen.getByRole('button', { name: /Trigger/i })).toBeInTheDocument();
  });

  it('fires onClick', () => {
    const fn = vi.fn();
    render(<SirenButton label="Go" onClick={fn} />);
    fireEvent.click(screen.getByRole('button'));
    expect(fn).toHaveBeenCalled();
  });

  it('disabled when loading', () => {
    render(<SirenButton label="X" onClick={() => {}} loading />);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('secondary variant', () => {
    render(<SirenButton label="Stop" onClick={() => {}} variant="secondary" />);
    expect(screen.getByText('Stop')).toBeInTheDocument();
  });

  it('active state', () => {
    render(<SirenButton label="On" onClick={() => {}} active />);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });
});
