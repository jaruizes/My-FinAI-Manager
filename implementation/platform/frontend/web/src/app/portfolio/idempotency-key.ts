/**
 * Client-generated idempotency key for one create attempt (FR-031a).
 *
 * `crypto.randomUUID()` is only defined in a **secure context** (HTTPS or localhost). The
 * containerized platform serves the app over plain HTTP on a Docker service name, so we fall back
 * to `crypto.getRandomValues()` (available in non-secure contexts) and finally to `Math.random()`.
 * The key only needs to be unique, not cryptographically strong.
 */
export function newIdempotencyKey(): string {
  const c: Crypto | undefined = globalThis.crypto;

  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }

  const bytes = new Uint8Array(16);
  if (c && typeof c.getRandomValues === 'function') {
    c.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  // RFC 4122 v4 layout.
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'));
  return (
    hex.slice(0, 4).join('') +
    '-' +
    hex.slice(4, 6).join('') +
    '-' +
    hex.slice(6, 8).join('') +
    '-' +
    hex.slice(8, 10).join('') +
    '-' +
    hex.slice(10, 16).join('')
  );
}
