# ببینیم — بازسازی کامل از صفر با بک‌اند Cloudflare

بازسازی دقیق اپلیکیشن **bebinim-2 v1.5** (تماشای گروهی فیلم و موزیک) با:

- 📱 **فرانت‌اند:** اندروید native با **Jetpack Compose** (Kotlin) — دقیقاً مثل اپ اصلی
- ☁️ **بک‌اند:** **Cloudflare Workers + D1 + Durable Objects** (REST API + WebSocket لابی + رله صدا)
- 🚫 طبق درخواست: بخش **آرشیو فیلم‌ها** و بخش **پشتیبانی (تیکت‌ها)** حذف شده‌اند

---

## 🌐 بک‌اند (دیپلوی شده و فعال)

| مورد | آدرس |
|---|---|
| REST API | `https://bebinim-backend.agora-chat.workers.dev/` |
| WebSocket لابی | `wss://bebinim-backend.agora-chat.workers.dev/ws` |
| سلامت سرور | `GET /health` |

### امکانات بک‌اند
- **احراز هویت:** ثبت‌نام با شماره موبایل، ورود با رمز، ورود با OTP (حالت dev کد را در پاسخ برمی‌گرداند)، توکن JWT با HS256، refresh خودکار
- **لابی:** ساخت/پیوستن با کد ۸ کاراکتری، Durable Object اختصاصی برای هر لابی
- **پروتکل WebSocket:** کاملاً یکسان با اپ اصلی (`verify`, `basemsg-join-to-lobby`, `basemsg-alias`, `basemsg-chat`, `basemsg-change-vlink`, `basemsg-change-mode`, `basemsg-play-pause`, `basemsg-click-bar`, `basemsg-music-metadata`, `basemsg-mic-status`, `basemsg-player-ready`, `basemsg-ready-status`, `basemsg-all-ready`, `basemsg-get-voice-token`, `basemsg-exit-lobby`, `basemsg-close-lobby`)
- **همگام‌سازی پخش:** play/pause/seek بین همه کاربران + sync برای کاربران دیررس (`basemsg-playback-sync`)
- **چت صوتی:** فریم‌های باینری صدا (همان فریمینگ رله UDP اصلی: `[0x10][4B session][2B seq][payload]`) روی WebSocket رله می‌شود + رمزنگاری AES-GCM با کلید ارسالی از `basemsg-voice-token`
- **موزیک:** کاتالوگ موزیک، دسته‌بندی‌ها، هنرمندان، دنبال‌کردن، trending/random/recommended
- **رنکینگ:** لیدربورد + ۷ مرتبه رنک (تازه‌کار تا افسانه‌ای) + رنک شخصی + پروفایل عمومی
- **پلن‌ها:** ۳ پلن (برنزی/نقره‌ای/طلایی) + وضعیت اشتراک

> حذف‌شده‌ها بر اساس درخواست: `tickets/*`، `departments`، `archive-movies` و حالت `archive` لابی.

### دیپلوی مجدد بک‌اند
```bash
cd backend
npm install
# کانفیگ در wrangler.toml انجام شده است
npx wrangler d1 execute bebinim-db --remote --file=schema.sql
npx wrangler d1 execute bebinim-db --remote --file=seed.sql
npx wrangler deploy
```

### تنظیمات مهم (`wrangler.toml`)
- `JWT_SECRET`: کلید امضای JWT — **حتماً در production تغییر دهید**
- `OTP_DEV_MODE = "true"`: در پاسخ `send-otp` فیلد `dev_code` برمی‌گردد (چون SMS gateway وصل نیست). برای production با اتصال SMS gateway این را `false` کنید.

---

## 📱 اپ اندروید (Jetpack Compose)

```
android-app/
├── app/src/main/java/com/app/bebinim/
│   ├── data/
│   │   ├── api/          ← Retrofit (BebinimApiService + LobbyApiService) + مدل‌ها + TokenAuthenticator
│   │   ├── websocket/    ← WebSocketManager (پروتکل basemsg-* کامل)
│   │   ├── voicechat/    ← VoiceRelayManager (رله صدا روی WebSocket + AES-GCM)
│   │   └── utils/        ← TokenManager (DataStore) + UserPreferences + SoundPlayer
│   ├── ui/
│   │   ├── navigation/   ← Screen + AppNavigation (همان route های اصلی)
│   │   ├── screens/      ← Home, Login, Register, Profile, Plans, MyPlans,
│   │   │                    Ranking, CreateJoinLobby, LobbyScreen (فیلم),
│   │   │                    MusicLobbyScreen, VideoPlayer, MusicPlaybackService
│   │   └── theme/        ← رنگ‌ها و تایپوگرافی دقیق اپ اصلی (DarkNavy/YellowAccent/...)
│   └── viewmodel/        ← AuthViewModel, LobbyViewModel, RankingViewModel, ...
└── app/src/main/res/     ← آیکون‌ها و لوگوی اکسترکت‌شده از اپ اصلی + notification_sound
```

### ساخت (Build)
پیش‌نیاز: Android Studio (Koala+) یا SDK 35

```bash
cd android-app
./gradlew assembleDebug        # خروجی: app/build/outputs/apk/debug/app-debug.apk
```
یا پروژه را در Android Studio باز کنید و Run بزنید.

### نکته‌های فنی مهم
1. **آدرس سرور** در `app/build.gradle.kts` به‌صورت `BuildConfig.BASE_URL` و `BuildConfig.WS_URL` تعریف شده — برای تغییر سرور فقط همین را عوض کنید.
2. **فونت:** اپ اصلی از IranSansX استفاده می‌کند (به‌دلیل لایسنس، فایل‌های فونت داخل APK نمی‌توانیم باندل کنیم). فایل‌های `.ttf` را در `res/font/` بگذارید و در `ui/theme/Type.kt` معرفی کنید. فعلاً از فونت سیستمی استفاده می‌شود که فارسی را خوب رندر می‌کند.
3. **صدا:** به‌جای Opus native، فریم‌های PCM 16kHz/mono (فرمت باینری یکسان) رله می‌شوند — بدون نیاز به `.so` خارجی. کیفیت و تأخیر برای چت لابی کاملاً مناسب است.
4. **حالت‌های پخش لابی فیلم:** لینک مستقیم / رادیو / WebView / فایل مشترک (حالت آرشیو حذف شده).
5. لابی موزیک: کاتالوگ از API خوانده می‌شود؛ نمونه‌های قابل‌پخش (SoundHelix) در seed قرار دارند.

---

## 🧪 تست سریع بک‌اند

```bash
BASE=https://bebinim-backend.agora-chat.workers.dev

# ثبت‌نام
curl -X POST $BASE/api/v1/register -H 'Content-Type: application/json' \
  -d '{"name":"تست","phone_number":"09121234567","password":"test1234"}'

# پلن‌ها
curl $BASE/api/v1/plans

# رنکینگ
curl $BASE/api/v1/ranking

# موزیک
curl "$BASE/api/v1/lobby/music/all?limit=5"
```

تست کامل WebSocket دو کلاینت: `backend/ws-test.js`

---

## 📋 ساختار پروژه

```
bebinim-rebuild/
├── backend/          ← Cloudflare Worker (TypeScript) + D1 schema/seed
│   ├── src/index.ts  ← همه REST endpoints
│   ├── src/lobby.ts  ← Durable Object لابی (WS + voice relay)
│   ├── src/auth.ts   ← JWT + PBKDF2
│   ├── schema.sql / seed.sql
│   └── wrangler.toml
├── android-app/      ← اپ Jetpack Compose کامل
└── README.md
```
