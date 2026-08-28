-- Bebinim seed data (plans, rank tiers, music catalog)
INSERT OR REPLACE INTO plans (id, name, description, price, price_formatted, duration_days, features, type, users) VALUES
  ('bronze', 'پلن برنزی', 'دسترسی پایه به لابی‌ها', 99000, '۹۹,۰۰۰', 30, '["لابی فیلم و موزیک","تا ۴ نفر همزمان","پشتیبانی چت"]', 'bronze', 4),
  ('silver', 'پلن نقره‌ای', 'دسترسی استاندارد با کیفیت بالا', 199000, '۱۹۹,۰۰۰', 30, '["لابی فیلم و موزیک","تا ۶ نفر همزمان","کیفیت پخش بالا","چت و میکروفون"]', 'silver', 6),
  ('gold', 'پلن طلایی', 'دسترسی کامل و نامحدود', 399000, '۳۹۹,۰۰۰', 30, '["لابی فیلم و موزیک نامحدود","تا ۸ نفر همزمان","کیفیت پخش بالا","چت و میکروفون","اولویت سرور"]', 'gold', 8);

INSERT OR REPLACE INTO rank_tiers (level, name, color, icon, img, min_hours, max_hours) VALUES
  (1,  'تازه‌کار',   '#8D99AE', '🥚', NULL, 0,    10),
  (2,  'برنزی',      '#CD7F32', '🥉', NULL, 10,   30),
  (3,  'نقره‌ای',    '#C0C0C0', '🥈', NULL, 30,   70),
  (4,  'طلایی',      '#FFD700', '🥇', NULL, 70,   150),
  (5,  'پلاتینیوم',  '#E5E4E2', '💎', NULL, 150,  300),
  (6,  'الماسی',     '#B9F2FF', '💠', NULL, 300,  600),
  (7,  'افسانه‌ای',  '#FF43A4', '👑', NULL, 600,  NULL);

INSERT OR REPLACE INTO music_categories (id, name, slug, color, image) VALUES
  ('pop',     'پاپ',     'pop',     '#FF6B6B', NULL),
  ('classic', 'کلاسیک',  'classic', '#4ECDC4', NULL),
  ('rock',    'راک',     'rock',    '#95E1D3', NULL),
  ('chill',   'چیل',     'chill',   '#A78BFA', NULL);

INSERT OR REPLACE INTO artists (id, name, english_name, bio, verified, genres) VALUES
  ('a1', 'کِیوان', 'Keyvan', 'هنرمند مستقل', 1, '["pop","chill"]'),
  ('a2', 'آوا', 'Ava', 'هنرمند مستقل', 1, '["pop"]'),
  ('a3', 'استودیو سایه', 'Sayeh Studio', 'گروه موسیقی', 0, '["rock"]');

INSERT OR REPLACE INTO musics (id, name, artist_id, artist_name, audio_url, cover_image, duration, play_count, category_id) VALUES
  ('m1', 'SoundHelix آهنگ ۱', 'a1', 'کِیوان', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', NULL, 372, 120, 'pop'),
  ('m2', 'SoundHelix آهنگ ۲', 'a1', 'کِیوان', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3', NULL, 422, 95,  'chill'),
  ('m3', 'SoundHelix آهنگ ۳', 'a2', 'آوا',   'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3', NULL, 354, 80,  'pop'),
  ('m4', 'SoundHelix آهنگ ۴', 'a2', 'آوا',   'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3', NULL, 271, 61,  'classic'),
  ('m5', 'SoundHelix آهنگ ۵', 'a3', 'استودیو سایه', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3', NULL, 384, 143, 'rock'),
  ('m6', 'SoundHelix آهنگ ۶', 'a3', 'استودیو سایه', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3', NULL, 297, 77,  'chill');
