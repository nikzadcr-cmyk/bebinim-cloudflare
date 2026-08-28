// Bebinim backend — Cloudflare Worker (REST + D1 + WS lobby)
// Protocol-compatible with the original app; archives & support (tickets) removed.
import { LobbyRoom, type Env } from './lobby';
import {
  signJwt, verifyJwt, hashPassword, verifyPassword,
  ok, err, json, randomCode, randomId, nowSec,
} from './auth';

export { LobbyRoom };

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-Device-ID,X-Device-Id,X-User-Email,X-User-ID,X-Retry-Count',
};

function corsed(res: Response): Response {
  const h = new Headers(res.headers);
  for (const [k, v] of Object.entries(CORS)) h.set(k, v);
  return new Response(res.body, { status: res.status, headers: h });
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (request.method === 'OPTIONS') return corsed(new Response(null, { status: 204 }));
    try {
      const res = await route(request, env, ctx);
      if (res.status === 101 || res.webSocket) return res; // never reconstruct WebSocket upgrades
      return corsed(res);
    } catch (e) {
      return corsed(err('خطای داخلی سرور: ' + (e instanceof Error ? e.message : String(e)), 500));
    }
  },
};

// ---------- helpers ----------
function b64url(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
const enc = new TextEncoder();
// module-scoped env (set per request) for the auth() helper
let ENV: Env;

async function auth(request: Request) {
  const authHeader = request.headers.get('Authorization') || '';
  if (!authHeader.startsWith('Bearer ')) return null;
  const payload = await verifyJwt(authHeader.slice(7), ENV.JWT_SECRET);
  if (!payload) return null;
  return { userId: payload.sub, email: payload.email, phone: payload.phone };
}

async function route(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  ENV = env;
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, '') || '/';
  const method = request.method;

  // ---------- WebSocket upgrade → Durable Object ----------
  if (path === '/ws' || path === '/lobby/ws') {
    if (request.headers.get('Upgrade') !== 'websocket') return err('websocket upgrade required', 426);
    const token = url.searchParams.get('token') || '';
    // token identifies user; the lobby code arrives via basemsg-join-to-lobby.
    // We route every connection to a stable "gateway" DO instance that forwards? 
    // Simpler: single DO per lobby is required. The client joins by token which contains lobby code
    // only AFTER create/join-token call. But the original app connects FIRST, then joins with token.
    // Strategy: route to a "gate" DO by hash of the lobby token; the gate verifies and, if the token
    // carries a code, forwards to the per-code DO. To keep it simple and robust we use ONE DO instance
    // per lobby code derived from the token; unknown tokens land on a shared gate that verifies then
    // responds verify-result only (join happens after basemsg-join-to-lobby — but socket is already bound!).
    //
    // Robust approach: parse JWT payload NOW (unverified here, verified inside DO) to extract code.
    const pre = preParseJwt(token);
    const code = pre?.lobby_code || pre?.code || '_gate';
    const id = env.LOBBY.idFromName('lobby:' + code);
    const stub = env.LOBBY.get(id);
    // forward the ORIGINAL upgrade request (token already present as query param)
    return stub.fetch(request);
  }

  // DO admin endpoints (proxied with secret path)
  if (path.startsWith('/lobby-admin/')) {
    const parts = path.split('/'); // /lobby-admin/{code}/{action}
    const code = parts[2];
    const action = parts[3] || '';
    const id = env.LOBBY.idFromName('lobby:' + code);
    const stub = env.LOBBY.get(id);
    return stub.fetch(new Request('https://do/' + action));
  }



  // ================= AUTH =================
  if (method === 'POST' && path === '/api/v1/register') {
    const body: any = await request.json().catch(() => ({}));
    const name = String(body.name || '').trim();
    const phone = String(body.phone_number || body.phoneNumber || '').replace(/[\s-]/g, '');
    const password = String(body.password || '');
    if (!/^(\+98|0)?9\d{9}$/.test(phone)) return err('شماره تلفن معتبر نیست. مثال: 09123456789');
    if (password.length < 6) return err('رمز عبور باید حداقل ۶ کاراکتر باشد');
    if (!name) return err('نام را وارد کنید');

    const norm = phone.startsWith('+98') ? '0' + phone.slice(3) : phone.startsWith('98') ? '0' + phone.slice(2) : phone;
    const exists = await env.DB.prepare('SELECT id FROM users WHERE phone = ?').bind(norm).first();
    if (exists) return err('این شماره قبلاً ثبت‌نام کرده است');

    const userId = randomId();
    const username = 'user' + norm.slice(-4) + Math.floor(Math.random() * 900 + 100);
    const hash = await hashPassword(password);
    await env.DB.prepare(
      'INSERT INTO users (id, phone, name, username, password_hash, created_at) VALUES (?,?,?,?,?,?)'
    ).bind(userId, norm, name, username, hash, nowSec()).run();
    // starter stats
    await env.DB.prepare('INSERT OR IGNORE INTO user_stats (user_id, total_minutes, updated_at) VALUES (?,?,?)')
      .bind(userId, 0, nowSec()).run();

    const token = await signJwt({
      sub: userId, real_id: userId, phone: norm, email: null, typ: 'access',
      iat: nowSec(), exp: nowSec() + 60 * 60 * 24 * 30,
    }, env.JWT_SECRET);
    return ok({ token, user: { id: userId, name, username, email: null } }, 'ثبت‌نام موفقیت‌آمیز بود');
  }

  if (method === 'POST' && path === '/api/v1/login') {
    const body: any = await request.json().catch(() => ({}));
    const email = String(body.email || '').trim().toLowerCase();
    const password = String(body.password || '');
    if (!email || !password) return err('لطفاً تمام فیلدها را پر کنید');
    const user = await env.DB.prepare('SELECT * FROM users WHERE lower(email) = ? OR phone = ?').bind(email, email).first<any>();
    if (!user || !user.password_hash) return err('مشخصات ورودی اشتباه است');
    if (!(await verifyPassword(password, user.password_hash))) return err('مشخصات ورودی اشتباه است');
    const token = await signJwt({
      sub: user.id, real_id: user.id, email: user.email, phone: user.phone, typ: 'access',
      iat: nowSec(), exp: nowSec() + 60 * 60 * 24 * 30,
    }, env.JWT_SECRET);
    return ok(token, 'ورود موفقیت‌آمیز بود');
  }

  if (method === 'POST' && path === '/api/v1/login/send-otp') {
    const body: any = await request.json().catch(() => ({}));
    const identity = String(body.identity || '').trim().toLowerCase();
    if (!identity) return err('ایمیل یا شماره موبایل را وارد کنید');
    const code = String(Math.floor(100000 + Math.random() * 900000));
    await env.DB.prepare(
      `INSERT INTO otps (identity, code, expires_at, attempts) VALUES (?,?,?,0)
       ON CONFLICT(identity) DO UPDATE SET code=excluded.code, expires_at=excluded.expires_at, attempts=0`
    ).bind(identity, code, nowSec() + 120).run();
    const data: Record<string, unknown> = { expires_in: 120 };
    if (env.OTP_DEV_MODE === 'true') data.dev_code = code; // dev-only convenience (no SMS gateway attached)
    return ok(data, 'کد تایید ارسال شد');
  }

  if (method === 'POST' && path === '/api/v1/login/verify-otp') {
    const body: any = await request.json().catch(() => ({}));
    const identity = String(body.identity || '').trim().toLowerCase();
    const otp = String(body.otp_code || body.otpCode || '');
    const row = await env.DB.prepare('SELECT * FROM otps WHERE identity = ?').bind(identity).first<any>();
    if (!row || row.expires_at < nowSec()) return err('کد تایید منقضی شده است');
    if (row.code !== otp) return err('کد تایید اشتباه است');
    await env.DB.prepare('DELETE FROM otps WHERE identity = ?').bind(identity).run();

    // find or create user by phone/email
    let user = await env.DB.prepare('SELECT * FROM users WHERE lower(email) = ? OR phone = ?').bind(identity, identity).first<any>();
    if (!user) {
      const isPhone = /^(\+98|0)?9\d{9}$/.test(identity.replace(/[\s-]/g, ''));
      if (!isPhone) return err('کاربری با این مشخصات یافت نشد');
      const norm = identity.startsWith('+98') ? '0' + identity.slice(3) : identity;
      const userId = randomId();
      const username = 'user' + norm.slice(-4) + Math.floor(Math.random() * 900 + 100);
      await env.DB.prepare('INSERT INTO users (id, phone, name, username, created_at) VALUES (?,?,?,?,?)')
        .bind(userId, norm, username, username, nowSec()).run();
      await env.DB.prepare('INSERT OR IGNORE INTO user_stats (user_id, total_minutes, updated_at) VALUES (?,?,?)')
        .bind(userId, 0, nowSec()).run();
      user = { id: userId, phone: norm, email: null, name: username, username };
    }
    const token = await signJwt({
      sub: user.id, real_id: user.id, email: user.email, phone: user.phone, typ: 'access',
      iat: nowSec(), exp: nowSec() + 60 * 60 * 24 * 30,
    }, env.JWT_SECRET);
    return ok(token, 'ورود موفقیت‌آمیز بود');
  }

  if (method === 'POST' && path === '/api/v1/refresh-token') {
    const body: any = await request.json().catch(() => ({}));
    const old = String(body.token || '');
    const payload = await verifyJwt(old, env.JWT_SECRET);
    if (!payload) return err('توکن نامعتبر است', 401);
    const token = await signJwt({
      sub: payload.sub, real_id: payload.real_id, email: payload.email, phone: payload.phone, typ: 'access',
      iat: nowSec(), exp: nowSec() + 60 * 60 * 24 * 30,
    }, env.JWT_SECRET);
    return ok(token, 'success');
  }

  if (method === 'GET' && path === '/api/v1/user') {
    const a = await auth(request);
    if (!a) return err('unauthorized', 401);
    const user = await env.DB.prepare('SELECT id, email, phone, name, username, created_at FROM users WHERE id = ?').bind(a.userId).first<any>();
    if (!user) return err('user not found', 404);
    return ok({
      id: user.id, name: user.name || '', username: user.username || user.phone || '',
      email: user.email || '', roles: [{ name: 'user', label: 'کاربر' }],
      createdAt: new Date((user.created_at || 0) * 1000).toISOString(),
    });
  }

  if (method === 'POST' && path === '/api/v1/generate-web-login') {
    const body: any = await request.json().catch(() => ({}));
    const token = String(body.token || '');
    const redirect = String(body.redirect || '/');
    const payload = await verifyJwt(token, env.JWT_SECRET);
    if (!payload) return err('توکن نامعتبر است', 401);
    const webToken = await signJwt({
      sub: payload.sub, real_id: payload.real_id, email: payload.email, phone: payload.phone,
      typ: 'access', iat: nowSec(), exp: nowSec() + 600,
    }, env.JWT_SECRET);
    return ok({ token: webToken, url: `https://${url.host}${redirect}?_t=${webToken}` });
  }

  // ================= PLANS =================
  if (method === 'GET' && path === '/api/v1/plans') {
    const rows = await env.DB.prepare('SELECT * FROM plans ORDER BY price ASC').all<any>();
    return ok(rows.results.map(planJson));
  }
  const plansMatch = path.match(/^\/api\/v1\/plans\/([^/]+)$/);
  if (method === 'GET' && plansMatch) {
    const plan = await env.DB.prepare('SELECT * FROM plans WHERE id = ?').bind(plansMatch[1]).first<any>();
    if (!plan) return err('پلن یافت نشد', 404);
    return ok(planJson(plan));
  }
  if (method === 'GET' && path === '/api/v1/user/plan') {
    const a = await auth(request);
    if (!a) return err('unauthorized', 401);
    const up = await env.DB.prepare(
      `SELECT up.*, p.name, p.users, p.price_formatted FROM user_plans up JOIN plans p ON p.id = up.plan_id
       WHERE up.user_id = ? AND up.expires_at > ?`
    ).bind(a.userId, nowSec()).first<any>();
    if (!up) return ok(null, 'no active plan');
    return ok({
      id: up.plan_id, name: up.name, price: 0, priceFormatted: up.price_formatted,
      durationDays: Math.max(0, Math.ceil((up.expires_at - nowSec()) / 86400)),
      features: [], type: 'active', users: up.max_users,
    });
  }
  if (method === 'POST' && path === '/api/v1/verify-plan') {
    const a = await auth(request);
    if (!a) return err('unauthorized', 401);
    const up = await env.DB.prepare(
      `SELECT up.*, p.name FROM user_plans up JOIN plans p ON p.id = up.plan_id WHERE up.user_id = ?`
    ).bind(a.userId).first<any>();
    if (!up) {
      return ok({ hasActivePlan: false, planName: '', status: 'inactive', message: 'شما پلن فعالی ندارید', daysRemaining: 0, expirationDate: '', planDetails: null });
    }
    const days = Math.max(0, Math.ceil((up.expires_at - nowSec()) / 86400));
    return ok({
      hasActivePlan: up.expires_at > nowSec(),
      planName: up.name,
      status: up.expires_at > nowSec() ? 'active' : 'expired',
      message: up.expires_at > nowSec() ? 'اشتراک فعال است' : 'اشتراک منقضی شده',
      daysRemaining: days,
      expirationDate: new Date(up.expires_at * 1000).toISOString(),
      planDetails: { name: up.name, expiration: up.expires_at, users: up.max_users },
    });
  }
  // grant plan (dev/self-service purchase endpoint — replaces website payment)
  if (method === 'POST' && path === '/api/v1/plans/purchase') {
    const a = await auth(request);
    if (!a) return err('unauthorized', 401);
    const body: any = await request.json().catch(() => ({}));
    const planId = String(body.planId || body.plan_id || '');
    const plan = await env.DB.prepare('SELECT * FROM plans WHERE id = ?').bind(planId).first<any>();
    if (!plan) return err('پلن یافت نشد', 404);
    await env.DB.prepare(
      `INSERT INTO user_plans (user_id, plan_id, started_at, expires_at, max_users) VALUES (?,?,?,?,?)
       ON CONFLICT(user_id) DO UPDATE SET plan_id=excluded.plan_id, started_at=excluded.started_at,
       expires_at=MAX(excluded.expires_at, user_plans.expires_at), max_users=excluded.max_users`
    ).bind(a.userId, planId, nowSec(), nowSec() + plan.duration_days * 86400, plan.users).run();
    return ok({ purchased: true, planId }, 'اشتراک فعال شد');
  }

  // ================= LOBBY =================
  if (method === 'POST' && path === '/api/v1/lobby/create-token') {
    const a = await auth(request);
    if (!a) return err('unauthorized', 401);
    const body: any = await request.json().catch(() => ({}));
    const lobbyType = String(body.lobbyType || 'movie') === 'music' ? 'music' : 'movie';
    const code = randomCode(8);
    const lobbyToken = await signJwt({
      sub: a.userId, real_id: a.userId, email: a.email, phone: a.phone,
      typ: 'lobby', lobby_code: code, lobby_type: lobbyType, is_creator: true,
      iat: nowSec(), exp: nowSec() + 60 * 60 * 12,
    }, env.JWT_SECRET);
    await env.DB.prepare('INSERT INTO lobbies (code, lobby_type, creator_id, created_at, closed) VALUES (?,?,?,?,0)')
      .bind(code, lobbyType, a.userId, nowSec()).run();
    return ok({ code, token: lobbyToken, lobbyType, maxUsers: 8, expiration: nowSec() + 43200 }, 'lobby created');
  }

  if (method === 'POST' && path === '/api/v1/lobby/join-token') {
    const a = await auth(request);
    if (!a) return err('unauthorized', 401);
    const body: any = await request.json().catch(() => ({}));
    const code = String(body.code || '').trim().toUpperCase();
    if (!code) return err('کد لابی را وارد کنید');
    const lobby = await env.DB.prepare('SELECT * FROM lobbies WHERE code = ? AND closed = 0').bind(code).first<any>();
    if (!lobby) return err('لابی با این کد یافت نشد', 404);
    const lobbyToken = await signJwt({
      sub: a.userId, real_id: a.userId, email: a.email, phone: a.phone,
      typ: 'lobby', lobby_code: code, lobby_type: lobby.lobby_type, is_creator: false,
      iat: nowSec(), exp: nowSec() + 60 * 60 * 12,
    }, env.JWT_SECRET);
    return ok({ code, token: lobbyToken, lobbyType: lobby.lobby_type, maxUsers: 8, expiration: nowSec() + 43200 });
  }

  if (method === 'GET' && path === '/api/v1/lobby/active') {
    const a = await auth(request);
    if (!a) return err('unauthorized', 401);
    const rows = await env.DB.prepare(
      `SELECT l.*, u.name as creator_name FROM lobbies l LEFT JOIN users u ON u.id = l.creator_id
       WHERE l.closed = 0 ORDER BY l.created_at DESC LIMIT 30`
    ).all<any>();
    const lobbies = [];
    for (const l of rows.results) {
      const id = env.LOBBY.idFromName('lobby:' + l.code);
      const stub = env.LOBBY.get(id);
      let users: Array<{ user_id: string; username: string }> = [];
      try {
        const info = await stub.fetch(new Request('https://do/info'));
        const j: any = await info.json();
        if (!j.closed) users = (j.userList || []).map((x: any) => ({ user_id: x.user_id, username: x.alias || x.username || 'کاربر' }));
      } catch { /* DO unreachable */ }
      lobbies.push({
        code: l.code,
        creater: l.creator_name || l.creator_id,
        is_owner: l.creator_id === a.userId,
        lobbyType: l.lobby_type,
        userplan: 'gold',
        users,
      });
    }
    return ok({ lobbies });
  }

  // ================= RANKING =================
  if (method === 'GET' && path === '/api/v1/ranking') {
    const tiers = await env.DB.prepare('SELECT * FROM rank_tiers ORDER BY level ASC').all<any>();
    const rows = await env.DB.prepare(
      `SELECT s.user_id, u.name, u.username, s.total_minutes FROM user_stats s
       LEFT JOIN users u ON u.id = s.user_id ORDER BY s.total_minutes DESC LIMIT 100`
    ).all<any>();
    const leaderboard = rows.results.map((r, i) => {
      const hours = Math.floor(r.total_minutes / 60);
      const tier = tierFor(tiers.results, hours);
      return {
        userId: r.user_id,
        displayName: r.name || r.username || 'کاربر',
        position: i + 1,
        totalHours: hours,
        rankLevel: tier?.level ?? 1,
        rankName: tier?.name ?? 'تازه‌کار',
        rankColor: tier?.color ?? '#8D99AE',
        rankIcon: tier?.icon ?? '',
        rankImg: tier?.img ?? '',
      };
    });
    return ok({
      leaderboard,
      allRanks: tiers.results.map((t) => ({
        level: t.level, name: t.name, color: t.color, img: t.img,
        minHours: t.min_hours, maxHours: t.max_hours ?? null,
      })),
    });
  }

  if (method === 'GET' && path === '/api/v1/ranking/me') {
    const a = await auth(request);
    if (!a) return err('unauthorized', 401);
    return ok(await rankInfoFor(env, a.userId));
  }

  const rankUserMatch = path.match(/^\/api\/v1\/ranking\/user\/([^/]+)$/);
  if (method === 'GET' && rankUserMatch) {
    const info = await rankInfoFor(env, rankUserMatch[1]);
    return ok({
      userId: info.userId,
      displayName: info.displayName,
      rank: info.rank,
      achievements: [],
    });
  }

  // ================= MUSIC =================
  const musicRoutes: Array<[RegExp, (m: RegExpMatchArray, u: URL, r: Request) => Promise<Response>]> = [
    [/^\/api\/v1\/lobby\/music\/all$/, async (m, u) => musicList(env, u, 'all')],
    [/^\/api\/v1\/lobby\/music\/category\/([^/]+)$/, async (m, u) => musicList(env, u, 'category', m[1])],
    [/^\/api\/v1\/lobby\/music\/new-releases$/, async (m, u) => musicList(env, u, 'new')],
    [/^\/api\/v1\/lobby\/music\/trending$/, async (m, u) => musicList(env, u, 'trending')],
    [/^\/api\/v1\/lobby\/music\/random$/, async (m, u) => musicList(env, u, 'random')],
    [/^\/api\/v1\/lobby\/music\/recently-played$/, async (m, u) => musicList(env, u, 'recent')],
    [/^\/api\/v1\/lobby\/music\/recommended$/, async (m, u) => musicList(env, u, 'recommended')],
  ];
  for (const [re, handler] of musicRoutes) {
    const m = path.match(re);
    if (m && method === 'GET') return await handler(m, url, request);
  }

  if (method === 'POST' && /^\/api\/v1\/lobby\/music\/([^/]+)\/play$/.test(path)) {
    const musicId = path.split('/')[5];
    await env.DB.prepare('UPDATE musics SET play_count = play_count + 1 WHERE id = ?').bind(musicId).run();
    const devId = request.headers.get('X-Device-Id') || request.headers.get('X-Device-ID') || '';
    if (devId) {
      await env.DB.prepare(
        `INSERT INTO play_history (device_id, music_id, played_at) VALUES (?,?,?)
         ON CONFLICT(device_id, music_id) DO UPDATE SET played_at=excluded.played_at`
      ).bind(devId, musicId, nowSec()).run();
    }
    return ok({ played: true });
  }

  if (method === 'POST' && path === '/api/v1/lobby/music/history') {
    return ok({ saved: true });
  }

  if (method === 'GET' && path === '/api/v1/lobby/music-categories') {
    const rows = await env.DB.prepare('SELECT * FROM music_categories').all<any>();
    return ok({ categories: rows.results.map((c) => ({ id: c.id, name: c.name, slug: c.slug, color: c.color, image: c.image })), success: true });
  }

  if (method === 'GET' && path === '/api/v1/lobby/artists') {
    const limit = parseInt(url.searchParams.get('limit') || '20');
    const skip = parseInt(url.searchParams.get('skip') || '0');
    const search = (url.searchParams.get('search') || '').toLowerCase();
    const rows = await env.DB.prepare('SELECT * FROM artists ORDER BY name LIMIT ? OFFSET ?').bind(limit, skip).all<any>();
    const artists = rows.results.filter((a) => !search || (a.name + (a.english_name || '')).toLowerCase().includes(search));
    return ok({ artists: artists.map(artistJson), success: true, total: artists.length });
  }

  if (method === 'GET' && path === '/api/v1/lobby/artists/popular') {
    const limit = parseInt(url.searchParams.get('limit') || '10');
    const rows = await env.DB.prepare(
      `SELECT a.*, COUNT(m.id) as track_count FROM artists a LEFT JOIN musics m ON m.artist_id = a.id
       GROUP BY a.id ORDER BY track_count DESC LIMIT ?`
    ).bind(limit).all<any>();
    return ok({ artists: rows.results.map((a) => artistJson(a)), success: true, total: rows.results.length });
  }

  if (method === 'GET' && path === '/api/v1/lobby/artists/followed') {
    const devId = request.headers.get('X-Device-Id') || request.headers.get('X-Device-ID') || '';
    const rows = await env.DB.prepare(
      `SELECT a.* FROM artist_follows f JOIN artists a ON a.id = f.artist_id WHERE f.device_id = ?`
    ).bind(devId).all<any>();
    return ok({ artists: rows.results.map(artistJson), success: true, total: rows.results.length });
  }

  const artistMatch = path.match(/^\/api\/v1\/lobby\/artist\/([^/]+)$/);
  if (method === 'GET' && artistMatch) {
    const artist = await env.DB.prepare('SELECT * FROM artists WHERE id = ?').bind(artistMatch[1]).first<any>();
    if (!artist) return err('artist not found', 404);
    const tracks = await env.DB.prepare('SELECT * FROM musics WHERE artist_id = ?').bind(artistMatch[1]).all<any>();
    return ok({
      artist: artistJson(artist),
      tracks: tracks.results.map((t) => musicJson(t)),
      success: true,
    });
  }

  const followMatch = path.match(/^\/api\/v1\/lobby\/artist\/([^/]+)\/(follow|unfollow|follow-status)$/);
  if (followMatch) {
    const [, artistId, action] = followMatch;
    const devId = request.headers.get('X-Device-Id') || request.headers.get('X-Device-ID') || '';
    if (method === 'GET' && action === 'follow-status') {
      const f = await env.DB.prepare('SELECT 1 as x FROM artist_follows WHERE device_id = ? AND artist_id = ?').bind(devId, artistId).first();
      return new Response(JSON.stringify({ success: true, isFollowing: !!f }), { headers: { 'Content-Type': 'application/json' } });
    }
    if (method === 'POST' && action === 'follow') {
      await env.DB.prepare('INSERT OR IGNORE INTO artist_follows (device_id, artist_id, created_at) VALUES (?,?,?)')
        .bind(devId, artistId, nowSec()).run();
      return new Response(JSON.stringify({ success: true, isFollowing: true }), { headers: { 'Content-Type': 'application/json' } });
    }
    if (method === 'POST' && action === 'unfollow') {
      await env.DB.prepare('DELETE FROM artist_follows WHERE device_id = ? AND artist_id = ?').bind(devId, artistId).run();
      return new Response(JSON.stringify({ success: true, isFollowing: false }), { headers: { 'Content-Type': 'application/json' } });
    }
  }

  // Legacy lobby-server style endpoint (kept for app compat)
  if (method === 'POST' && path === '/user-active-lobby') {
    const a = await auth(request);
    if (!a) return new Response(JSON.stringify({ active: null }), { headers: { 'Content-Type': 'application/json' } });
    return new Response(JSON.stringify({ active: null }), { headers: { 'Content-Type': 'application/json' } });
  }

  // health
  if (path === '/' || path === '/health') {
    return json({ status: 'ok', app: env.APP_NAME || 'bebinim', time: new Date().toISOString() });
  }

  return err('not found: ' + method + ' ' + path, 404);
}

// ---------- domain helpers ----------
function planJson(p: any) {
  let features: string[] = [];
  try { features = JSON.parse(p.features); } catch { /* noop */ }
  return {
    id: p.id, name: p.name, description: p.description || '',
    price: p.price, priceFormatted: p.price_formatted,
    duration: p.duration_days, durationDays: p.duration_days,
    features, type: p.type, users: p.users,
  };
}

function tierFor(tiers: any[], hours: number): any | null {
  let best: any = null;
  for (const t of tiers) {
    if (hours >= t.min_hours && (t.max_hours == null || hours < t.max_hours)) return t;
    if (hours >= t.min_hours) best = t;
  }
  return best ?? tiers[0];
}

async function rankInfoFor(env: Env, userId: string) {
  const tiers = await env.DB.prepare('SELECT * FROM rank_tiers ORDER BY level ASC').all<any>();
  const s = await env.DB.prepare('SELECT total_minutes FROM user_stats WHERE user_id = ?').bind(userId).first<any>();
  const totalMinutes = s?.total_minutes ?? 0;
  const hours = Math.floor(totalMinutes / 60);
  const tier = tierFor(tiers.results, hours);
  const next = tiers.results.find((t) => t.level === (tier?.level ?? 1) + 1);
  const isMax = !next;
  const progress = isMax ? 100 : Math.min(100, Math.round(
    ((hours - (tier?.min_hours ?? 0)) / Math.max(1, (next?.min_hours ?? 1) - (tier?.min_hours ?? 0))) * 100
  ));
  // position
  const posRow = await env.DB.prepare(
    'SELECT COUNT(*) + 1 as pos FROM user_stats WHERE total_minutes > ?'
  ).bind(totalMinutes).first<any>();
  const user = await env.DB.prepare('SELECT name, username FROM users WHERE id = ?').bind(userId).first<any>();
  return {
    userId,
    displayName: user?.name || user?.username || 'کاربر',
    position: posRow?.pos ?? null,
    rank: {
      name: tier?.name ?? 'تازه‌کار',
      level: tier?.level ?? 1,
      totalHours: hours,
      totalMinutes,
      progress,
      hoursToNext: isMax ? 0 : Math.max(0, (next?.min_hours ?? 0) - hours),
      isMaxRank: isMax,
      nextRank: next?.name ?? '',
      color: tier?.color ?? '#8D99AE',
      icon: tier?.icon ?? '',
      img: tier?.img ?? '',
    },
  };
}

function musicJson(m: any) {
  return {
    id: m.id, name: m.name,
    artist: m.artist_name || '', artistId: m.artist_id, artistName: m.artist_name || '',
    artistInfo: null, audioUrl: m.audio_url, coverImage: m.cover_image || '',
    duration: m.duration ?? 0, playCount: m.play_count ?? 0,
    categoryId: m.category_id || '', categoryName: '', categoryColor: '',
  };
}

function artistJson(a: any) {
  return {
    id: a.id, name: a.name, englishName: a.english_name || '', bio: a.bio || '',
    image: a.image || '', coverImage: a.cover_image || '',
    followers: 0, monthlyListeners: 0, trackCount: 0, verified: !!a.verified,
    genres: (() => { try { return JSON.parse(a.genres || '[]'); } catch { return []; } })(),
    socialLinks: { spotify: '', instagram: '', telegram: '', twitter: '', soundcloud: '' },
  };
}

async function musicList(env: Env, url: URL, kind: string, categoryId?: string): Promise<Response> {
  const limit = Math.min(100, parseInt(url.searchParams.get('limit') || '30'));
  const skip = parseInt(url.searchParams.get('skip') || '0');
  const search = (url.searchParams.get('search') || '').toLowerCase();
  let stmt;
  if (kind === 'category') {
    stmt = env.DB.prepare('SELECT * FROM musics WHERE category_id = ? ORDER BY play_count DESC LIMIT ? OFFSET ?').bind(categoryId!, limit, skip);
  } else if (kind === 'new') {
    stmt = env.DB.prepare('SELECT * FROM musics ORDER BY rowid DESC LIMIT ? OFFSET ?').bind(limit, skip);
  } else if (kind === 'trending') {
    stmt = env.DB.prepare('SELECT * FROM musics ORDER BY play_count DESC LIMIT ? OFFSET ?').bind(limit, skip);
  } else if (kind === 'random') {
    stmt = env.DB.prepare('SELECT * FROM musics ORDER BY RANDOM() LIMIT ?').bind(limit);
  } else {
    stmt = env.DB.prepare('SELECT * FROM musics ORDER BY name LIMIT ? OFFSET ?').bind(limit, skip);
  }
  const rows = await stmt.all<any>();
  let musics = rows.results.map(musicJson);
  if (search) musics = musics.filter((m) => (m.name + m.artistName).toLowerCase().includes(search));
  if (kind === 'recent' || kind === 'recommended') {
    // without per-device history tables populated, fall back to popular
    musics = musics.sort((a, b) => (b.playCount || 0) - (a.playCount || 0));
  }
  return ok({ musics, success: true, total: musics.length });
}

function preParseJwt(token: string): Record<string, unknown> | null {
  try {
    const mid = token.split('.')[1];
    const json = atob(mid.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}
