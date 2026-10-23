package com.benton.lightchat.util

import java.util.concurrent.atomic.AtomicLong

object LocalId {

    private val counter = AtomicLong(System.currentTimeMillis() * 1000)

    fun next(): Long = -counter.incrementAndGet()
}
