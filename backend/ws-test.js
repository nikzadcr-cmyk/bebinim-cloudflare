// WebSocket protocol test — simulates the Bebinim app client
// Usage: node ws-test.js <lobbyToken1> <lobbyToken2>
const url = 'wss://bebinim-backend.agora-chat.workers.dev/ws';

function connect(token, label) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url + '?token=' + encodeURIComponent(token));
    const log = (...a) => console.log(`[${label}]`, ...a);
    const state = { userId: null, alias: null, users: [] };
    ws.addEventListener('open', async () => {
      log('connected');
      ws.send(JSON.stringify({ type: 'verify', data: token }));
    });
    ws.addEventListener('message', (ev) => {
      const m = ev.data;
      const isBin = typeof m !== 'string';
      if (isBin) { log('<< binary frame', m.size ?? m.byteLength, 'bytes'); return; }
      const msg = JSON.parse(m);
      log('<<', JSON.stringify(msg).slice(0, 220));
      if (msg.type === 'verify-result' && msg.state === 1) {
        ws.send(JSON.stringify({ type: 'basemsg-join-to-lobby', data: token }));
      }
      if (msg.type === 'basemsg-join-to-lobby' && msg.state === 1) {
        state.userId = msg.data.unit_socket_id;
        ws.send(JSON.stringify({ type: 'basemsg-alias', lobbycode: msg.data.code, data: { name: label === 'A' ? 'علی' : 'رضا' } }));
        if (label === 'A') {
          // host sets video link, plays, seeks
          setTimeout(() => ws.send(JSON.stringify({ type: 'basemsg-change-vlink', lobbycode: msg.data.code, data: { nlink: 'https://example.com/movie.mp4', vcurrenttime: 0 } })), 300);
          setTimeout(() => ws.send(JSON.stringify({ type: 'basemsg-play-pause', lobbycode: msg.data.code, data: 'play', currentTime: 5.2 })), 600);
          setTimeout(() => ws.send(JSON.stringify({ type: 'basemsg-chat', lobbycode: msg.data.code, data: { text: 'سلام بچه‌ها!', to: null } })), 900);
          setTimeout(() => ws.send(JSON.stringify({ type: 'basemsg-player-ready', lobbycode: msg.data.code, data: 'ready' })), 1200);
        } else {
          setTimeout(() => ws.send(JSON.stringify({ type: 'basemsg-player-ready', lobbycode: msg.data.code, data: 'ready' })), 1500);
          // voice frame relay test (binary): [0x10][4B session][2B seq][payload]
          setTimeout(() => {
            const b = Buffer.alloc(1 + 4 + 2 + 8);
            b[0] = 0x10; b.writeUInt32BE(0, 1); b.writeUInt16BE(1, 5); b.write('VOICE123', 7);
            ws.send(b);
          }, 1800);
        }
      }
      if (msg.type === 'basemsg-all-ready') log('✅ ALL READY');
    });
    ws.addEventListener('close', (ev) => log('closed', ev.code, ev.reason));
    ws.addEventListener('error', (e) => { log('error', e.message || 'ws error'); });
    // auto-exit after 6s
    setTimeout(() => { resolve(state); ws.close(1000); }, 6000);
  });
}

(async () => {
  const [t1, t2] = process.argv.slice(2);
  if (!t1) { console.error('usage: node ws-test.js <token1> [token2]'); process.exit(1); }
  await Promise.all([connect(t1, 'A'), t2 ? connect(t2, 'B') : Promise.resolve()]);
  console.log('TEST DONE');
  process.exit(0);
})();
