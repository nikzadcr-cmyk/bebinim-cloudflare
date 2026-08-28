-- Bebinim backend — D1 schema
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT UNIQUE,
  phone TEXT UNIQUE,
  name TEXT,
  username TEXT,
  password_hash TEXT,
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone ON users(phone) WHERE phone IS NOT NULL;

CREATE TABLE IF NOT EXISTS otps (
  identity TEXT PRIMARY KEY,
  code TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  attempts INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS plans (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT,
  price INTEGER NOT NULL,
  price_formatted TEXT NOT NULL,
  duration_days INTEGER NOT NULL,
  features TEXT NOT NULL,        -- JSON array
  type TEXT NOT NULL,
  users INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS user_plans (
  user_id TEXT PRIMARY KEY,
  plan_id TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  max_users INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  device_id TEXT,
  expires_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS rank_tiers (
  level INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  color TEXT NOT NULL,
  icon TEXT,
  img TEXT,
  min_hours INTEGER NOT NULL,
  max_hours INTEGER             -- NULL = unlimited
);

CREATE TABLE IF NOT EXISTS user_stats (
  user_id TEXT PRIMARY KEY,
  total_minutes INTEGER DEFAULT 0,
  updated_at INTEGER
);

CREATE TABLE IF NOT EXISTS lobby_tokens (
  token TEXT PRIMARY KEY,
  code TEXT NOT NULL,
  lobby_type TEXT NOT NULL,
  creator_id TEXT NOT NULL,
  max_users INTEGER DEFAULT 8,
  expires_at INTEGER NOT NULL,
  used INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_lobby_tokens_code ON lobby_tokens(code);

CREATE TABLE IF NOT EXISTS lobbies (
  code TEXT PRIMARY KEY,
  lobby_type TEXT NOT NULL,
  creator_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  closed INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS music_categories (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  slug TEXT,
  color TEXT,
  image TEXT
);

CREATE TABLE IF NOT EXISTS artists (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  english_name TEXT,
  bio TEXT,
  image TEXT,
  cover_image TEXT,
  verified INTEGER DEFAULT 0,
  genres TEXT DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS musics (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  artist_id TEXT,
  artist_name TEXT,
  audio_url TEXT NOT NULL,
  cover_image TEXT,
  duration INTEGER DEFAULT 0,
  play_count INTEGER DEFAULT 0,
  category_id TEXT
);
CREATE INDEX IF NOT EXISTS idx_musics_category ON musics(category_id);
CREATE INDEX IF NOT EXISTS idx_musics_artist ON musics(artist_id);

CREATE TABLE IF NOT EXISTS artist_follows (
  device_id TEXT NOT NULL,
  artist_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (device_id, artist_id)
);

CREATE TABLE IF NOT EXISTS play_history (
  device_id TEXT NOT NULL,
  music_id TEXT NOT NULL,
  played_at INTEGER NOT NULL,
  PRIMARY KEY (device_id, music_id)
);
