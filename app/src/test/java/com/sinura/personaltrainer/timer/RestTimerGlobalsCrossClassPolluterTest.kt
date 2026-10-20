package com.sinura.personaltrainer.timer

import org.junit.Test

/** No [com.sinura.personaltrainer.testutil.RestTimerGlobalsRule]: leaves globals dirty on purpose. */
class RestTimerGlobalsCrossClassPolluterTest {
    @Test
    fun leavesForegroundAndLedgerClaimForTheNextClass() {
        RestTimerAppForeground.setForegroundForTest(true)
        RestTimerCompletion.ledger.recordClaimForTest("cross-class-polluter")
    }
}
