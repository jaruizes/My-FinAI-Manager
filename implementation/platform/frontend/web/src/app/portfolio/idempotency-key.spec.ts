import { newIdempotencyKey } from './idempotency-key';

describe('newIdempotencyKey', () => {
  it('returns a UUID-shaped string', () => {
    expect(newIdempotencyKey()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
    );
  });

  it('returns distinct values', () => {
    const a = newIdempotencyKey();
    const b = newIdempotencyKey();
    expect(a).not.toEqual(b);
  });

  it('works without crypto.randomUUID (non-secure-context fallback)', () => {
    const original = globalThis.crypto.randomUUID;
    // Simulate a non-secure context where randomUUID is unavailable.
    (globalThis.crypto as unknown as { randomUUID?: unknown }).randomUUID = undefined;
    try {
      expect(newIdempotencyKey()).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
      );
    } finally {
      (globalThis.crypto as unknown as { randomUUID: unknown }).randomUUID = original;
    }
  });
});
