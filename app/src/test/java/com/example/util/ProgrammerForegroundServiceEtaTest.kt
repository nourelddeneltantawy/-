package com.example.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProgrammerForegroundServiceEtaTest {

    @Before
    fun setUp() {
        ProgrammerForegroundService.resetPauseAndEtaState()
    }

    @After
    fun tearDown() {
        ProgrammerForegroundService.resetPauseAndEtaState()
    }

    @Test
    fun slidingWindowEtaComputesAccurateMovingAverageAndIgnoresSkipsAndPauses() {
        val total = 32 * 1024 // 32 KB
        var t = 1_000L

        // Initial baseline sample (256B)
        ProgrammerForegroundService.updateThroughputAndEta(
            bytesDone = 256,
            totalBytes = total,
            isSkippedStep = false,
            nowMsOverride = t
        )
        assertEquals("--:--", ProgrammerForegroundService.etaTextFlow.value)

        // 5 real chunks of 1024B each taking 1000ms -> ~1024 B/s
        var done = 256
        repeat(5) {
            t += 1_000L
            done += 1_024
            ProgrammerForegroundService.updateThroughputAndEta(
                bytesDone = done,
                totalBytes = total,
                isSkippedStep = false,
                nowMsOverride = t
            )
        }
        // Remaining = 32768 - 5376 = 27392 bytes @ 1024 B/s => ~26 seconds ("00:26")
        assertEquals("00:26", ProgrammerForegroundService.etaTextFlow.value)

        // Instant zero-latency empty block skip (e.g. 8192 bytes skipped in 2ms) MUST NOT spike ETA speed
        t += 2L
        done += 8_192
        ProgrammerForegroundService.updateThroughputAndEta(
            bytesDone = done,
            totalBytes = total,
            isSkippedStep = true,
            nowMsOverride = t
        )
        // Remaining = 32768 - 13568 = 19200 bytes still using ~1024 B/s window => "00:18"
        assertEquals("00:18", ProgrammerForegroundService.etaTextFlow.value)

        // Simulate user pausing for 60 seconds before the next 1024B block finishes in 1000ms active time
        ProgrammerForegroundService.addPauseDeltaForTest(60_000L)
        t += 61_000L
        done += 1_024
        ProgrammerForegroundService.updateThroughputAndEta(
            bytesDone = done,
            totalBytes = total,
            isSkippedStep = false,
            nowMsOverride = t
        )
        // Pause subtracted => active delta is 1000ms, remaining = 18176 bytes @ 1024 B/s => "00:17"
        assertEquals("00:17", ProgrammerForegroundService.etaTextFlow.value)
    }

    @Test
    fun slidingWindowDropsOldSlowSamplesAfterTenUpdates() {
        val total = 64 * 1024
        var t = 1_000L
        var done = 256
        ProgrammerForegroundService.updateThroughputAndEta(done, total, false, nowMsOverride = t)

        // 5 slow samples: 512B per 1000ms (512 B/s)
        repeat(5) {
            t += 1_000L
            done += 512
            ProgrammerForegroundService.updateThroughputAndEta(done, total, false, nowMsOverride = t)
        }

        // 10 fast samples: 4096B per 1000ms (4096 B/s) -> completely evicts the 5 slow samples
        repeat(10) {
            t += 1_000L
            done += 4_096
            ProgrammerForegroundService.updateThroughputAndEta(done, total, false, nowMsOverride = t)
        }

        // Remaining = 65536 - 43776 = 21760 bytes @ 4096 B/s => 5.31s => "00:05"
        assertEquals("00:05", ProgrammerForegroundService.etaTextFlow.value)
    }
}
