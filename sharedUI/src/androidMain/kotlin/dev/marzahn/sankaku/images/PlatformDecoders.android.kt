package dev.marzahn.sankaku.images

import coil3.decode.Decoder

internal actual fun platformImageDecoders(): List<Decoder.Factory> = emptyList()

internal actual val platformMaxAvifBitDepth: Int = 10
