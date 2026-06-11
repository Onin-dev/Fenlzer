package com.fenl.fenlzer.domain.text

import java.text.Normalizer
import java.util.Locale

object SearchNormalizer {
    fun sortKey(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .replace("\\s+".toRegex(), " ")
    }
}
