package com.app.bebinim.data.model

import androidx.annotation.DrawableRes
import com.app.bebinim.R

/**
 * Exact port of the original app's StickerCatalog.
 * Stickers travel as chat messages with the "::sticker::" prefix + file name,
 * so they sync for every member through the regular chat pipeline.
 */
data class Sticker(val fileName: String, @DrawableRes val drawableRes: Int)

data class StickerGroup(val id: String, val label: String, val stickers: List<Sticker>)

object StickerCatalog {

    const val STICKER_PREFIX = "::sticker::"

    val groups: List<StickerGroup> = listOf(
        StickerGroup(
            "pishi", "پیشی",
            listOf(
                Sticker("pishi-cat-happy", R.drawable.sticker_pishi_cat_happy),
                Sticker("pishi-cat-smile", R.drawable.sticker_pishi_cat_smile),
                Sticker("pishi-cat-smilling", R.drawable.sticker_pishi_cat_smilling),
                Sticker("pishi-catnoted", R.drawable.sticker_pishi_catnoted),
                Sticker("pishi-crythumbsup", R.drawable.sticker_pishi_crythumbsup),
                Sticker("pishi-love4you", R.drawable.sticker_pishi_love4you),
                Sticker("pishi-peace", R.drawable.sticker_pishi_peace),
                Sticker("pishi-plotting", R.drawable.sticker_pishi_plotting),
                Sticker("pishi-shhhhh", R.drawable.sticker_pishi_shhhhh),
                Sticker("pishi-shockedcat", R.drawable.sticker_pishi_shockedcat)
            )
        ),
        StickerGroup(
            "anim", "انیمه",
            listOf(
                Sticker("anim-catgirl-cozy", R.drawable.sticker_anim_catgirl_cozy),
                Sticker("anim-chibi-paimon-think", R.drawable.sticker_anim_chibi_paimon_think),
                Sticker("anim-cute", R.drawable.sticker_anim_cute),
                Sticker("anim-gawrgurawavebackgroundless", R.drawable.sticker_anim_gawrgurawavebackgroundless),
                Sticker("anim-zorolike", R.drawable.sticker_anim_zorolike)
            )
        ),
        StickerGroup(
            "normal", "واکنش‌ها",
            listOf(
                Sticker("normal-bruh", R.drawable.sticker_normal_bruh),
                Sticker("normal-heartache", R.drawable.sticker_normal_heartache),
                Sticker("normal-shaggywtf", R.drawable.sticker_normal_shaggywtf),
                Sticker("normal-stare", R.drawable.sticker_normal_stare),
                Sticker("normal-windowstarebob", R.drawable.sticker_normal_windowstarebob)
            )
        )
    )

    private val byFileName: Map<String, Sticker> =
        groups.flatMap { it.stickers }.associateBy { it.fileName }

    fun isStickerMessage(message: String): Boolean =
        message.startsWith(STICKER_PREFIX) && byFileName.containsKey(message.removePrefix(STICKER_PREFIX))

    fun drawableFor(message: String): Int? {
        if (!message.startsWith(STICKER_PREFIX)) return null
        return byFileName[message.removePrefix(STICKER_PREFIX)]?.drawableRes
    }
}
