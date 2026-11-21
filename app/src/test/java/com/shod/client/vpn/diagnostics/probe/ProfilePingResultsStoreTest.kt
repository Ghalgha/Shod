package com.shod.client.vpn.diagnostics.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePingResultsStoreTest {
    @Test
    fun profileMeasurementsAreUpdatedAndRemovedIndependently() {
        val store = ProfilePingResultsStore()
        store.saveProfile(1, ProfilePingResult.Success(42))
        store.saveProfile(2, ProfilePingResult.Failure("timeout"))
        store.removeProfile(1)

        assertEquals(mapOf(2 to ProfilePingResult.Failure("timeout")), store.profileResults.value)
    }

    @Test
    fun turnMeasurementsCanBeReplacedAndClearedWithoutTouchingProfiles() {
        val store = ProfilePingResultsStore()
        store.saveProfile(3, ProfilePingResult.Success(18))
        store.saveTurn("192.0.2.0/24", 25)
        store.saveTurn("192.0.2.0/24", 30)
        store.clearTurn()

        assertTrue(store.turnResults.value.isEmpty())
        assertEquals(ProfilePingResult.Success(18), store.profileResults.value[3])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTurnMeasurement() {
        ProfilePingResultsStore().saveTurn("192.0.2.0/24", 0)
    }
}
