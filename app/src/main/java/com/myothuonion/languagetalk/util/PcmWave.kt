package com.myothuonion.languagetalk.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun pcm16Wave(pcm: ByteArray, sampleRate: Int = 16000): ByteArray = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
    put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
    putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
    put("data".toByteArray()); putInt(pcm.size); put(pcm)
}.array()
