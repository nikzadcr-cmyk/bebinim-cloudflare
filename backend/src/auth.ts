// Bebinim backend — JWT (HS256) + PBKDF2 password hashing via WebCrypto
export const JWT_SECRET_KEY = 'JWT_SECRET';

const enc = new TextEncoder();
const dec = new TextDecoder();

function b64url(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function b64urlDecode(s: string): Uint8Array {
  s = s.replace(/-/g, '+').replace(/_/g, '/');
  while (s.length % 4) s += '=';
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
}

export interface JwtPayload {
  sub: string;          // user id
  email?: string | null;
  phone?: string | null;
  real_id: string;      // same as sub (kept for app compat: JWT claim read by client)
  exp: number;
  iat: number;
  typ: 'access' | 'refresh' | 'lobby';
  lobby_code?: string;  // lobby tokens only
  lobby_type?: 'movie' | 'music';
  is_creator?: boolean;
}

export async function signJwt(payload: JwtPayload, secret: string): Promise<string> {
  const header = { alg: 'HS256', typ: 'JWT' };
  const h = b64url(enc.encode(JSON.stringify(header)));
  const p = b64url(enc.encode(JSON.stringify(payload)));
  const data = `${h}.${p}`;
  const sig = await crypto.subtle.sign('HMAC', await hmacKey(secret), enc.encode(data));
  return `${data}.${b64url(sig)}`;
}

export async function verifyJwt(token: string, secret: string): Promise<JwtPayload | null> {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const data = `${parts[0]}.${parts[1]}`;
    const expected = b64url(await crypto.subtle.sign('HMAC', await hmacKey(secret), enc.encode(data)));
    if (!timingSafeEqual(expected, parts[2])) return null;
    const payload = JSON.parse(dec.decode(b64urlDecode(parts[1]))) as JwtPayload;
    if (payload.exp * 1000 < Date.now()) return null;
    return payload;
  } catch {
    return null;
  }
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

// ---------- password hashing (PBKDF2-SHA256, 100k iters) ----------
export async function hashPassword(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt, iterations: 100_000, hash: 'SHA-256' }, key, 256
  );
  return `pbkdf2$100000$${b64url(salt)}$${b64url(bits)}`;
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  try {
    const [algo, iters, saltB64, hashB64] = stored.split('$');
    if (algo !== 'pbkdf2') return false;
    const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits(
      { name: 'PBKDF2', salt: b64urlDecode(saltB64), iterations: parseInt(iters), hash: 'SHA-256' }, key, 256
    );
    return timingSafeEqual(b64url(bits), hashB64);
  } catch {
    return false;
  }
}

// ---------- misc ----------
export function json(data: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', ...headers },
  });
}

export function ok<T>(data: T, message = 'success'): Response {
  return json({ status: 'success', message, data });
}

export function err(message: string, _status = 200): Response {
  // Always HTTP 200: the app parses body() and reads status/message fields
  return json({ status: 'error', message, data: null }, 200);
}

export function randomCode(len = 8): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const bytes = crypto.getRandomValues(new Uint8Array(len));
  let s = '';
  for (const b of bytes) s += chars[b % chars.length];
  return s;
}

export function randomId(): string {
  return crypto.randomUUID();
}

export function nowSec(): number {
  return Math.floor(Date.now() / 1000);
}
