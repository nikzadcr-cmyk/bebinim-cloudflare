// Bebinim backend — LobbyRoom Durable Object
// Implements the exact WebSocket protocol of the original app (basemsg-* envelope)
// plus voice relay (binary Opus/PCM frames, same packet framing as the original UDP relay).
import { verifyJwt, type JwtPayload } from './auth';

export interface Env {
  DB: D1Database;
  LOBBY: DurableObjectNamespace;
  JWT_SECRET: string;
  OTP_DEV_MODE?: string;
  APP_NAME?: string;
}

export interface LobbyUser {
  socketId: string;      // unit_socket_id
  realId: string;        // authenticated user id
  userId: string;        // public user_id sent to clients (= realId)
  alias: string;
  icon: string;
  username: string;
  isCreator: boolean;
  ws: WebSocket | null;
  ready: boolean;
  micEnabled: boolean;
  session: number;       // voice session id
  lastSeen: number;
}

interface VoiceCred {
  token: string;
  key: string; // base64 AES key
  expiresAt: number;
}

const MAX_USERS_DEFAULT = 8;
const VOICE_KEY_TTL_MS = 30 * 60 * 1000; // 30 min
const enc = new TextEncoder();

function b64(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s);
}

export class LobbyRoom {
  private state: DurableObjectState;
  private env: Env;
  private users: Map<string, LobbyUser> = new Map(); // by socketId
  private socketIds: Map<WebSocket, string> = new Map(); // classic-mode socket lookup
  private lobbyCode: string = '';
  private lobbyType: string = 'movie';
  private creatorSocketId: string | null = null;
  private creatorRealId: string | null = null;
  private closed = false;

  // playback state (for basemsg-playback-sync on late joiners)
  private currentUrl: string | null = null;
  private currentMode: string = 'link';
  private playing: boolean = false;
  private currentTime: number = 0;
  private updatedAt: number = 0;
  private musicMeta: Record<string, unknown> | null = null;
  private sharedFileName: string | null = null;
  private nextSession = 1;
  private voiceCred: VoiceCred | null = null;
  private hydrated = false;

  constructor(state: DurableObjectState, env: Env) {
    this.state = state;
    this.env = env;
    // restore persisted playback meta
    this.state.blockConcurrencyWhile(async () => {
      this.lobbyCode = (await this.state.storage.get<string>('code')) || '';
      this.lobbyType = (await this.state.storage.get<string>('lobbyType')) || 'movie';
      this.currentUrl = (await this.state.storage.get<string>('url')) || null;
      this.currentMode = (await this.state.storage.get<string>('mode')) || 'link';
      this.musicMeta = (await this.state.storage.get<Record<string, unknown>>('musicMeta')) || null;
      this.creatorRealId = (await this.state.storage.get<string>('creator')) || null;
      const closed = await this.state.storage.get<number>('closed');
      this.closed = !!closed;
    });
  }

  async fetch(request: Request): Promise<Response> {
    try {
      return await this.handle(request);
    } catch (e) {
      console.error('DO ERROR:', e instanceof Error ? e.stack : String(e));
      return new Response('DO error: ' + (e instanceof Error ? e.message : String(e)), { status: 500 });
    }
  }

  private async handle(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === '/ws' && request.headers.get('Upgrade') === 'websocket') {
      return this.handleUpgrade(request);
    }
    // HTTP control endpoints
    if (url.pathname === '/close') {
      this.broadcast({ type: 'basemsg-close-lobby', state: 1 }, null);
      this.closed = true;
      await this.state.storage.put('closed', 1);
      // close all sockets
      for (const u of this.users.values()) { try { u.ws?.close(1000, 'lobby closed'); } catch { /* noop */ } }
      this.users.clear();
      return new Response('closed');
    }
    if (url.pathname === '/info') {
      this.hydrate();
      return new Response(JSON.stringify({
        code: this.lobbyCode, type: this.lobbyType, users: this.users.size, closed: this.closed,
        userList: Array.from(this.users.values()).map((u) => ({ user_id: u.userId, alias: u.alias, username: u.username })),
      }), { headers: { 'Content-Type': 'application/json' } });
    }
    return new Response('not found', { status: 404 });
  }

  private handleUpgrade(request: Request): Response {
    this.hydrate(); // this instance may have woken from hibernation — restore existing sockets first
    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    const socketId = 's_' + crypto.randomUUID().slice(0, 13);
    const token = new URL(request.url).searchParams.get('token') || '';

    const user: LobbyUser = {
      socketId, realId: '', userId: '', alias: '', icon: '', username: '',
      isCreator: false, ws: server, ready: false, micEnabled: false,
      session: 0, lastSeen: Date.now(),
    };
    this.users.set(socketId, user);
    this.socketIds.set(server, socketId);
    this.hydrated = true; // maps are now authoritative

    // Hibernating WebSocket API — the DO can hibernate between messages, so persist
    // the per-socket identity in the socket attachment and rebuild maps on wake.
    server.serializeAttachment({
      socketId, realId: '', userId: '', alias: '', icon: '', username: '',
      isCreator: false, ready: false, micEnabled: false, session: 0,
    });
    this.state.acceptWebSocket(server);
    // return the CLIENT end in the 101 response; the accepted (server) end stays inside the DO

    // pre-verify the query token if present; in-band verify also supported
    if (token) { void this.tryVerify(socketId, token); }
    return new Response(null, { status: 101, webSocket: client });
  }

  /**
   * Rebuild in-memory maps after a hibernation wake. When the runtime evicts the DO,
   * all plain fields reset — but accepted WebSockets survive and keep their serialized
   * attachments. Without this, every message after hibernation was silently dropped
   * (the "connection drops / messages never arrive" bug).
   */
  private hydrate(): void {
    if (this.hydrated) return;
    for (const ws of this.state.getWebSockets()) {
      try {
        const a = ws.deserializeAttachment() as Partial<LobbyUser> | null;
        if (!a?.socketId) continue;
        const existing = this.users.get(a.socketId);
        if (existing) { existing.ws = ws; this.socketIds.set(ws, a.socketId); continue; }
        this.users.set(a.socketId, {
          socketId: a.socketId, realId: a.realId || '', userId: a.realId || '',
          alias: a.alias || '', icon: a.icon || '', username: a.username || '',
          isCreator: !!a.isCreator, ws, ready: !!a.ready, micEnabled: !!a.micEnabled,
          session: a.session || 0, lastSeen: Date.now(),
        });
        this.socketIds.set(ws, a.socketId);
        if (a.isCreator && !this.creatorSocketId) this.creatorSocketId = a.socketId;
      } catch { /* skip broken socket */ }
    }
    this.hydrated = true;
  }

  private persistUser(u: LobbyUser): void {
    try {
      u.ws?.serializeAttachment({
        socketId: u.socketId, realId: u.realId, userId: u.userId, alias: u.alias,
        icon: u.icon, username: u.username, isCreator: u.isCreator,
        ready: u.ready, micEnabled: u.micEnabled, session: u.session,
      });
    } catch { /* noop */ }
  }

  private async tryVerify(socketId: string, token: string): Promise<void> {
    const u = this.users.get(socketId);
    if (!u) { console.log('DO-VERIFY no user for', socketId); return; }
    if (u.realId) return; // already verified — avoid duplicate verify-result
    const payload = await verifyLobbyToken(token, this.env.JWT_SECRET);
    if (!payload) {
      this.send(socketId, { type: 'verify-result', state: 0, msg: 'invalid token' });
      try { u.ws?.close(4001, 'invalid token'); } catch { /* noop */ }
      return;
    }
    u.realId = payload.real_id || payload.sub;
    u.userId = u.realId;
    u.username = payload.email || payload.phone || u.realId;
    if (payload.lobby_code) this.lobbyCode = payload.lobby_code;
    if (payload.lobby_type) this.lobbyType = payload.lobby_type;
    if (payload.is_creator) {
      u.isCreator = true;
      this.creatorSocketId = socketId;
      this.creatorRealId = u.realId;
    }
    await this.state.storage.put('code', this.lobbyCode);
    await this.state.storage.put('lobbyType', this.lobbyType);
    if (u.isCreator) await this.state.storage.put('creator', u.realId);
    this.persistUser(u);
    this.send(socketId, { type: 'verify-result', state: 1, msg: 'ok' });
  }

  // ---------- helpers ----------
  private send(socketId: string, obj: Record<string, unknown>): void {
    const u = this.users.get(socketId);
    if (!u?.ws || u.ws.readyState !== WebSocket.OPEN) { console.log('DO-SEND FAIL readyState=', u?.ws?.readyState, 'for', obj.type); return; }
    try { u.ws.send(JSON.stringify(obj)); } catch { /* noop */ }
  }

  private sendTo(obj: Record<string, unknown>, exceptSocketId: string | null): void {
    for (const [sid, u] of this.users) {
      if (exceptSocketId && sid === exceptSocketId) continue;
      if (sid === exceptSocketId) continue;
      this.send(sid, obj);
    }
  }

  private broadcast(obj: Record<string, unknown>, exceptSocketId: string | null): void {
    this.sendTo(obj, exceptSocketId);
  }

  private usersJson(): Array<Record<string, unknown>> {
    return Array.from(this.users.values()).map((u) => ({
      user_id: u.socketId,   // client-facing id = socket id (original namespace)
      real_id: u.realId,
      alias: u.alias || u.username || 'کاربر',
      username: u.alias || u.username || 'نامشخص',
      icon: u.icon || '',
      is_creator: u.isCreator,
    }));
  }

  private pushUsersList(): void {
    const list = this.usersJson();
    this.broadcast({ type: 'basemsg-lobby-users', state: 1, data: list }, null);
  }

  // ---------- WS message handling ----------
  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    this.hydrate();
    const socketId = this.socketIds.get(ws);
    if (!socketId) { console.log('DO-MSG unknown socket'); return; }
    const u = this.users.get(socketId);
    if (!u) return;
    u.lastSeen = Date.now();

    // binary voice frames → relay
    if (message instanceof ArrayBuffer) {
      this.relayVoice(socketId, message);
      return;
    }

    let msg: Record<string, unknown>;
    try { msg = JSON.parse(message); } catch { console.log('DO-MSG parse fail'); return; }
    const type = String(msg.type || '');
    const data = msg.data;
    const lobbycode = msg.lobbycode;
    const state = typeof msg.state === 'number' ? msg.state : undefined;

    switch (type) {
      case 'verify': {
        const token = typeof data === 'string' ? data : String(data ?? '');
        await this.tryVerify(socketId, token);
        return;
      }

      case 'basemsg-join-to-lobby': {
        const lobbyToken = typeof data === 'string' ? data : JSON.stringify(data);
        const payload = await verifyLobbyToken(lobbyToken, this.env.JWT_SECRET);
        if (!payload || payload.typ !== 'lobby') {
          this.send(socketId, { type: 'basemsg-join-to-lobby', state: 0, msg: 'invalid lobby token', data: null });
          return;
        }
        if (this.closed) {
          this.send(socketId, { type: 'basemsg-join-to-lobby', state: 0, msg: 'lobby closed', data: null });
          return;
        }
        if (this.users.size > MAX_USERS_DEFAULT + 1) {
          this.send(socketId, { type: 'basemsg-join-to-lobby', state: 0, msg: 'lobby full', data: null });
          return;
        }
        if (!u.realId) { // allow join flow without prior verify
          u.realId = payload.real_id || payload.sub;
          u.userId = u.realId;
          u.username = payload.email || payload.phone || u.realId;
        }
        if (payload.lobby_code) this.lobbyCode = payload.lobby_code;
        if (payload.lobby_type) this.lobbyType = payload.lobby_type;
        u.isCreator = !!payload.is_creator;
        if (u.isCreator) { this.creatorSocketId = socketId; this.creatorRealId = u.realId; await this.state.storage.put('creator', u.realId); }
        await this.state.storage.put('code', this.lobbyCode);
        await this.state.storage.put('lobbyType', this.lobbyType);
        u.session = this.nextSession++;
        // lobby is live again — cancel the empty-lobby auto-close alarm
        void this.state.storage.deleteAlarm();
        this.persistUser(u);

        this.send(socketId, {
          type: 'basemsg-join-to-lobby',
          state: 1,
          data: {
            code: this.lobbyCode,
            unit_socket_id: u.socketId,
            is_creator: u.isCreator,
            lobbyType: this.lobbyType,
          },
        });
        // original semantics: basemsg-new-connection goes ONLY to the new socket itself
        // (the client learns its own socket id + creator from it). Existing members just
        // get the refreshed users list — otherwise they would overwrite their own id.
        this.send(socketId, {
          type: 'basemsg-new-connection',
          data: {
            code: this.lobbyCode,
            unit_socket_id: u.socketId,
            creater: this.creatorSocketId || '',
            creater_fake_id: this.creatorRealId || '',
          },
        });
        this.pushUsersList();
        // send current playback state so late joiners sync
        this.send(socketId, {
          type: 'basemsg-playback-sync',
          state: 1,
          data: {
            vlink: this.currentUrl || '',
            mode: this.currentMode,
            playing: this.playing ? 'play' : 'pause',
            currentTime: this.playbackTime(),
          },
        });
        if (this.currentMode === 'music' && this.musicMeta) {
          this.send(socketId, { type: 'basemsg-music-metadata', data: this.musicMeta });
        }
        if (this.currentMode === 'shared' && this.sharedFileName) {
          this.send(socketId, { type: 'basemsg-change-mode', data: { mode: 'shared', fileName: this.sharedFileName } });
        }
        return;
      }

      case 'basemsg-alias': {
        const d0 = (data ?? {}) as { name?: string; icon?: string };
        const name = d0.name || '';
        u.alias = String(name).slice(0, 30) || 'کاربر';
        if (typeof d0.icon === 'string' && d0.icon) u.icon = String(d0.icon).slice(0, 30);
        this.persistUser(u);
        // user_id = SOCKET id — the client namespaces displayNames/users by socket id
        this.broadcast({
          type: 'basemsg-alias',
          state: 1,
          user_id: u.socketId,
          data: { name: u.alias, icon: u.icon || '' },
        }, null);
        this.pushUsersList();
        return;
      }

      case 'basemsg-chat': {
        const text = (data as { text?: string })?.text ?? '';
        const to = (data as { to?: string | null })?.to ?? null;
        const trimmed = String(text).slice(0, 500);
        // user_id = sender's SOCKET id — the original client dedupes its optimistic echo by
        // comparing user_id with its own unit_socket_id, so the echo MUST carry the socket id.
        this.send(socketId, { type: 'basemsg-chat', state: 1, user_id: u.socketId, data: { text: trimmed, to } });
        this.sendTo({ type: 'basemsg-chat', state: 1, user_id: u.socketId, data: { text: trimmed, to } }, socketId);
        return;
      }

      case 'basemsg-change-vlink': {
        const d = data as { nlink?: string; url?: string; type?: string; vcurrenttime?: number; name?: string; movieId?: string };
        const url = d.nlink || d.url || '';
        const linkType = d.type || 'link';
        if (url) this.currentUrl = url;
        this.currentMode = ['aparat', 'shared', 'archive', 'radio', 'webview'].includes(linkType) ? linkType : 'link';
        this.currentTime = Number(d.vcurrenttime || 0);
        this.playing = false; // fresh content always starts paused; clients auto-play on load
        for (const x of this.users.values()) x.ready = false; // re-gate playback for new content
        this.updatedAt = Date.now();
        await this.persistPlayback();
        this.broadcast({ type: 'basemsg-change-vlink', state: 1, user_id: u.socketId, data: d }, null);
        return;
      }

      case 'basemsg-change-mode': {
        const d = data as { mode?: string; url?: string; fileName?: string };
        const mode = String(d.mode || 'link');
        if (mode !== this.currentMode) {
          this.currentMode = mode;
          this.currentTime = 0;
          this.playing = false;
          for (const x of this.users.values()) x.ready = false;
          this.updatedAt = Date.now();
        }
        if (mode === 'shared' && d.fileName) this.sharedFileName = d.fileName;
        if ((mode === 'radio' || mode === 'webview' || mode === 'aparat') && d.url) this.currentUrl = d.url;
        await this.persistPlayback();
        this.broadcast({ type: 'basemsg-change-mode', state: 1, user_id: u.socketId, data: d }, null);
        return;
      }

      case 'basemsg-change-video': {
        const d = data as { type?: string; url?: string; name?: string; movieId?: string };
        this.currentMode = d.type || 'archive';
        this.currentUrl = d.url || '';
        this.currentTime = 0;
        this.playing = false;
        for (const x of this.users.values()) x.ready = false;
        this.updatedAt = Date.now();
        await this.persistPlayback();
        this.broadcast({ type: 'basemsg-change-video', state: 1, user_id: u.socketId, data: d }, null);
        return;
      }

      case 'basemsg-play-pause': {
        const isPlaying = data === 'play' || (data as { isPlaying?: boolean })?.isPlaying === true;
        this.playing = isPlaying;
        this.currentTime = Number(msg.currentTime ?? (data as { currentTime?: number })?.currentTime ?? this.currentTime);
        this.updatedAt = Date.now();
        await this.state.storage.put('playing', this.playing);
        this.broadcast({ type: 'basemsg-play-pause', user_id: u.socketId, data: isPlaying ? 'play' : 'pause', currentTime: this.currentTime }, socketId);
        return;
      }

      case 'basemsg-click-bar': {
        const t = Number((data as { currentTime?: number })?.currentTime ?? 0);
        this.currentTime = t;
        this.updatedAt = Date.now();
        await this.state.storage.put('currentTime', t);
        this.broadcast({ type: 'basemsg-click-bar', user_id: u.socketId, data: { currentTime: t } }, socketId);
        return;
      }

      case 'basemsg-music-metadata': {
        const d = (data ?? {}) as Record<string, unknown>;
        this.musicMeta = d;
        this.currentMode = 'music';
        if (typeof d.audioUrl === 'string' && d.audioUrl) this.currentUrl = d.audioUrl;
        this.updatedAt = Date.now();
        await this.state.storage.put('musicMeta', d);
        this.broadcast({ type: 'basemsg-music-metadata', state: 1, user_id: u.socketId, data: d }, null);
        return;
      }

      case 'basemsg-mic-status': {
        const enabled = !!(data as { enabled?: boolean })?.enabled;
        u.micEnabled = enabled;
        this.persistUser(u);
        this.broadcast({ type: 'basemsg-mic-status', state: 1, user_id: u.socketId, data: { enabled } }, null);
        return;
      }

      case 'basemsg-player-ready': {
        u.ready = true;
        this.persistUser(u);
        // only sockets that actually joined the lobby count towards readiness
        const members = Array.from(this.users.values()).filter((x) => x.session > 0);
        const total = members.length;
        const ready = members.filter((x) => x.ready).length;
        this.broadcast({ type: 'basemsg-ready-status', data: { ready_count: ready, total_count: total } }, null);
        if (total > 0 && ready >= total) {
          this.broadcast({ type: 'basemsg-all-ready', data: { ready_count: ready, total_count: total } }, null);
        }
        return;
      }

      case 'basemsg-get-voice-token': {
        const cred = this.ensureVoiceCred();
        this.send(socketId, {
          type: 'basemsg-voice-token',
          state: 1,
          data: { token: cred.token, key: cred.key },
          msg: 'ok',
        });
        return;
      }

      case 'basemsg-exit-lobby': {
        this.removeUser(socketId);
        return;
      }

      case 'basemsg-close-lobby': {
        if (u.isCreator || u.realId === this.creatorRealId) {
          this.broadcast({ type: 'basemsg-close-lobby', state: 1 }, null);
          this.closed = true;
          await this.state.storage.put('closed', 1);
          for (const [sid, uu] of this.users) {
            try { uu.ws?.close(1000, 'lobby closed'); } catch { /* noop */ }
            this.users.delete(sid);
          }
        }
        return;
      }

      case 'basemsg-kick-user': {
        // host-only convenience (same id namespace as the users list: socket ids)
        if (u.isCreator || u.realId === this.creatorRealId) {
          const target = String((data as { user_id?: string })?.user_id || '');
          if (target && this.users.has(target)) {
            this.send(target, { type: 'basemsg-close-lobby', state: 1, msg: 'kicked' });
            this.removeUser(target);
          }
        }
        return;
      }

      case 'close-socket-connection': {
        this.removeUser(socketId);
        return;
      }

      default:
        return;
    }
  }

  private playbackTime(): number {
    if (!this.playing) return this.currentTime;
    return this.currentTime + (Date.now() - this.updatedAt) / 1000;
  }

  private async persistPlayback(): Promise<void> {
    await this.state.storage.put('url', this.currentUrl ?? '');
    await this.state.storage.put('mode', this.currentMode);
    await this.state.storage.put('currentTime', this.currentTime);
    await this.state.storage.put('playing', this.playing);
  }

  // ---------- voice ----------
  private ensureVoiceCred(): VoiceCred {
    const now = Date.now();
    if (this.voiceCred && this.voiceCred.expiresAt > now + 60_000) return this.voiceCred;
    const rawKey = crypto.getRandomValues(new Uint8Array(32));
    this.voiceCred = {
      token: 'vt_' + crypto.randomUUID(),
      key: b64(rawKey),
      expiresAt: now + VOICE_KEY_TTL_MS,
    };
    return this.voiceCred;
  }

  // Binary voice frame framing (same as original UDP relay):
  // [1B type=0x10][4B BE senderSession][2B BE seq][payload]
  // For simplicity over WS: sender session comes from user's assigned session id.
  private relayVoice(socketId: string, frame: ArrayBuffer): void {
    const u = this.users.get(socketId);
    if (!u || !u.session) return;
    const bytes = new Uint8Array(frame);
    if (bytes.length < 1) return;
    const pktType = bytes[0];
    if (pktType === 0x11) { // LEAVE — just ignore (socket close handles it)
      return;
    }
    if (pktType !== 0x10) return; // only AUDIO relayed

    // rewrite sender session bytes to the server-assigned session
    const dv = new DataView(frame);
    dv.setUint32(1, u.session, false);
    this.sendToBinary(frame, socketId);
  }

  private sendToBinary(frame: ArrayBuffer, exceptSocketId: string | null): void {
    for (const [sid, u] of this.users) {
      if (sid === exceptSocketId) continue;
      if (!u.session) continue;
      if (u.ws && u.ws.readyState === WebSocket.OPEN) {
        try { u.ws.send(frame); } catch { /* noop */ }
      }
    }
  }

  private removeUser(socketId: string): void {
    this.hydrate();
    const u = this.users.get(socketId);
    if (!u) return;
    if (u.ws) this.socketIds.delete(u.ws);
    this.users.delete(socketId);
    try { u.ws?.close(1000, 'exit'); } catch { /* noop */ }
    const remaining = this.usersJson().map((x) => ({
      user_id: x.user_id, real_id: x.real_id, username: x.username, email: x.username,
    }));
    this.broadcast({ type: 'basemsg-exit-lobby', data: remaining }, null);
    this.pushUsersList();
    if (socketId === this.creatorSocketId) {
      // creator socket changed (reconnect) — hand creator role to any remaining socket
      // of the same real user; only truly-empty or creator-abandoned lobbies close.
      const sameUser = Array.from(this.users.values()).find((x) => x.realId === u.realId);
      if (sameUser) {
        sameUser.isCreator = true;
        this.creatorSocketId = sameUser.socketId;
      } else {
        this.creatorSocketId = null;
      }
    }
    // empty lobby → auto-close after a 60s grace period (survives brief reconnects)
    if (this.users.size === 0 && !this.closed) {
      void this.state.storage.setAlarm(Date.now() + 60_000);
    }
  }

  async webSocketAlarm(): Promise<void> {
    this.hydrate();
    if (this.users.size === 0 && !this.closed) {
      this.closed = true;
      await this.state.storage.put('closed', 1);
    }
  }

  async webSocketClose(ws: WebSocket): Promise<void> {
    this.hydrate();
    const socketId = this.socketIds.get(ws);
    if (socketId) { this.socketIds.delete(ws); this.removeUser(socketId); }
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    this.hydrate();
    const socketId = this.socketIds.get(ws);
    if (socketId) { this.socketIds.delete(ws); this.removeUser(socketId); }
  }
}

// lobby JWT verification (typ 'lobby' or generic access token)
async function verifyLobbyToken(token: string, secret: string): Promise<JwtPayload | null> {
  return verifyJwt(token, secret);
}
