package com.app.hamfilm.desktop

/**
 * Shared state models — field-compatible with the Android app's
 * com.app.bebinim.data.websocket models (same basemsg-* wire protocol).
 */

data class ChatMessage(
    val username: String,
    val message: String,
    val timestamp: Long,
    val userId: String,
    val isSystemMessage: Boolean = false
)

data class LobbyUser(
    val userId: String,
    val realId: String,
    val username: String,
    val displayName: String,
    val isHost: Boolean
)

data class LobbyInfo(val code: String, val lobbyType: String)

sealed class ConnectionState {
    data object Connected : ConnectionState()
    data object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/** isSeek=true marks an explicit scrub (basemsg-click-bar) — receivers must always follow it. */
data class VideoSyncState(val currentTime: Double, val isPlaying: Boolean, val isSeek: Boolean = false)

data class PlaybackSyncState(
    val videoUrl: String?,
    val currentTime: Double,
    val isPlaying: Boolean,
    val mode: String,
    val fromSync: Boolean = true
)

data class ReadyStatus(val readyCount: Int, val totalCount: Int)

data class SubtitleInfo(val url: String, val language: String = "fa", val label: String = "فارسی", val mimeType: String = "text/vtt")

data class MusicMetadata(
    val audioUrl: String,
    val name: String,
    val artist: String,
    val coverImage: String,
    val duration: Int,
    val musicId: String = ""
)

data class VoiceCredential(val token: String, val keyBase64: String, val expiresInSec: Int)

// ---------------- avatar / sticker catalogs (mirror StickerCatalog.kt) ----------------

data class AvatarDef(val id: String, val label: String, val colorHex: Long)

val LOBBY_ICONS = listOf(
    AvatarDef("jojeh", "جوجه", 0xFFFDD835),
    AvatarDef("meymo", "میمون", 0xFFAB47BC),
    AvatarDef("foxy", "فاکسی", 0xFFFF7043),
    AvatarDef("shipy", "شیپی", 0xFF42A5F5),
    AvatarDef("tems", "تمس", 0xFF66BB6A),
    AvatarDef("panda", "پاندا", 0xFFE0E0E0),
    AvatarDef("khargoosh", "خرگوش", 0xFFF48FB1),
    AvatarDef("gorbeh", "گربه", 0xFFFFB74D)
)

/** Legacy remote fallback for old icon ids (old clients). */
const val LOBBY_ICON_BASE_URL = "https://app.bebinim.me/hw-assets/images/"

/** Quick-send emoji bar above the chat input (same 8 as the Android app). */
val QUICK_EMOJIS = listOf("❤️", "😂", "🔥", "👍", "🎉", "😮", "😭", "👏")

data class StickerGroup(val id: String, val label: String, val stickers: List<String>)

object StickerCatalog {
    const val STICKER_PREFIX = "::sticker::"

    val groups: List<StickerGroup> = listOf(
        StickerGroup(
            "pishi", "پیشی",
            listOf(
                "pishi-cat-happy", "pishi-cat-smile", "pishi-cat-smilling", "pishi-catnoted",
                "pishi-crythumbsup", "pishi-love4you", "pishi-peace", "pishi-plotting",
                "pishi-shhhhh", "pishi-shockedcat"
            )
        ),
        StickerGroup(
            "anim", "انیمه",
            listOf(
                "anim-catgirl-cozy", "anim-chibi-paimon-think", "anim-cute",
                "anim-gawrgurawavebackgroundless", "anim-zorolike"
            )
        ),
        StickerGroup(
            "normal", "واکنش‌ها",
            listOf(
                "normal-bruh", "normal-heartache", "normal-shaggywtf",
                "normal-stare", "normal-windowstarebob"
            )
        ),
        StickerGroup(
            "meme", "میم",
            listOf(
                "meme-laugh", "meme-what", "meme-smirk", "meme-cry", "meme-panic",
                "meme-cool", "meme-think", "meme-angry", "meme-scared", "meme-sleepy",
                "meme-popcorn", "meme-skull", "meme-tongue", "meme-please"
            )
        )
    )

    private val known: Set<String> = groups.flatMap { it.stickers }.toSet()

    fun isStickerMessage(message: String): Boolean =
        message.startsWith(STICKER_PREFIX) && message.removePrefix(STICKER_PREFIX) in known

    fun fileNameFor(message: String): String? =
        if (message.startsWith(STICKER_PREFIX)) message.removePrefix(STICKER_PREFIX) else null
}

// ---------------- REST models (subset used by the desktop client) ----------------

data class LobbyToken(val code: String, val token: String, val lobbyType: String = "movie")

data class ActiveLobby(
    val code: String,
    val creater: String,
    val lobbyType: String,
    val usersCount: Int
)

data class SessionUser(
    val token: String,
    val userId: String,
    val username: String,
    val name: String,
    val email: String
)
