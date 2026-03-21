import { describe, it, expect } from 'vitest';
import { maskRtspUrl } from './edgeSnapshot';

describe('maskRtspUrl', () => {
  it('masks credentials and preserves host path', () => {
    expect(maskRtspUrl('rtsp://admin:secret@192.168.1.5:554/stream2')).toBe(
      'rtsp://***@192.168.1.5:554/stream2',
    );
  });

  it('masks when password contains @ (Tapo-style)', () => {
    expect(maskRtspUrl('rtsp://administrator:interOP@123@192.168.68.56:554/stream2')).toBe(
      'rtsp://***@192.168.68.56:554/stream2',
    );
  });

  it('returns em dash for empty', () => {
    expect(maskRtspUrl('')).toBe('—');
    expect(maskRtspUrl('   ')).toBe('—');
  });
});
