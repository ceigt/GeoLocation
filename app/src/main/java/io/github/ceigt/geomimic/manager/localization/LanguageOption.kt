package io.github.ceigt.geomimic.manager.localization

import androidx.annotation.StringRes
import io.github.ceigt.geomimic.R
import io.github.ceigt.geomimic.data.DEFAULT_LANGUAGE_TAG

private const val ENGLISH_LANGUAGE_TAG = "en"
private const val CHINESE_LANGUAGE_TAG = "zh-CN"

enum class LanguageOption(
    val tag: String,
    @StringRes val labelRes: Int
) {
    SYSTEM(DEFAULT_LANGUAGE_TAG, R.string.language_system),
    ENGLISH(ENGLISH_LANGUAGE_TAG, R.string.language_english),
    CHINESE(CHINESE_LANGUAGE_TAG, R.string.language_chinese);

    companion object {
        fun fromTag(tag: String): LanguageOption {
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }
    }
}
