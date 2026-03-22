# PA system — field test checklist

**Context:** `AlertManagement/` (Raspberry Pi PA). Code target ~80% complete; this checklist supports safe on-site validation.

## Preconditions

- [ ] Pi on stable power; speakers/amplifier wired and grounded.
- [ ] Network path from Pi to MQTT broker (or local fallback) verified.
- [ ] Volume start **low**; hearing protection available for sustained tones.
- [ ] Rollback: script or systemd unit to stop playback service immediately.

## Functional checks

1. **Subscribe** — Pi client connects to alert topic; log shows connection ACK.
2. **Idle** — No audio when no messages; CPU idle acceptable.
3. **Test tone** — Trigger a non-production test clip (if supported) at low volume.
4. **Alert path** — Publish a single test alert payload matching production schema; PA announces once (no duplicate loops).
5. **Stop / clear** — Send stop/clear message; audio ceases within SLA (e.g. &lt; 2 s).
6. **Failure** — Broker disconnect: Pi logs error, reconnects with backoff; no speaker lock-up.

## Safety

- [ ] Farm staff notified before audible tests.
- [ ] Max duration / auto-timeout configured to avoid runaway playback.
- [ ] Document who can trigger live siren vs simulator-only tests.

## Sign-off

| Date | Tester | Result | Notes |
|:-----|:-------|:-------|:------|
|      |        |        |       |
