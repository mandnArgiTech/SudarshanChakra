import { describe, it, expect } from 'vitest';
import { alertHasClipEvidence, edgeAlertClipUrl, maskRtspUrl } from './edgeSnapshot';

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

describe('alertHasClipEvidence', () => {
  it('returns false for null, empty, or invalid JSON', () => {
    expect(alertHasClipEvidence(null)).toBe(false);
    expect(alertHasClipEvidence('')).toBe(false);
    expect(alertHasClipEvidence('not-json')).toBe(false);
  });

  it('returns true when clip_path is non-empty', () => {
    expect(alertHasClipEvidence('{"clip_path":"a1b2.mp4"}')).toBe(true);
  });

  it('returns false when clip_path missing or empty', () => {
    expect(alertHasClipEvidence('{}')).toBe(false);
    expect(alertHasClipEvidence('{"clip_path":""}')).toBe(false);
  });
});

describe('edgeAlertClipUrl', () => {
  it('uses /edge gateway path when VITE_EDGE_SNAPSHOT_BASE is unset', () => {
    expect(edgeAlertClipUrl('alert-uuid-1')).toBe('/edge/api/clips/alert-uuid-1.mp4');
  });
});
