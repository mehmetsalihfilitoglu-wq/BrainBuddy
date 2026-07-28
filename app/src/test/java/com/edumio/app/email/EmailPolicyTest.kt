package com.edumio.app.email

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailPolicyTest {

    @Test fun transactionalAlwaysSends() {
        assertTrue(EmailConsentPolicy.canSend(EmailCategory.TRANSACTIONAL, learningEnabled = false, marketingConsent = false))
        assertFalse(EmailConsentPolicy.canUnsubscribe(EmailCategory.TRANSACTIONAL))
    }

    @Test fun learningIsOptOut() {
        assertTrue(EmailConsentPolicy.canSend(EmailCategory.LEARNING, learningEnabled = true, marketingConsent = false))
        assertFalse(EmailConsentPolicy.canSend(EmailCategory.LEARNING, learningEnabled = false, marketingConsent = false))
        assertTrue(EmailConsentPolicy.canUnsubscribe(EmailCategory.LEARNING))
    }

    @Test fun marketingIsOptIn() {
        assertFalse("default off", EmailConsentPolicy.canSend(EmailCategory.MARKETING, learningEnabled = true, marketingConsent = false))
        assertTrue(EmailConsentPolicy.canSend(EmailCategory.MARKETING, learningEnabled = false, marketingConsent = true))
    }

    @Test fun dailyReminderAtMostOncePerDayAndStopsOnCompletion() {
        assertTrue(EmailFrequencyPolicy.dailyReminderAllowed(alreadySentTodayForUser = false, completedToday = false))
        assertFalse(EmailFrequencyPolicy.dailyReminderAllowed(alreadySentTodayForUser = true, completedToday = false))
        assertFalse(EmailFrequencyPolicy.dailyReminderAllowed(alreadySentTodayForUser = false, completedToday = true))
    }

    @Test fun capsDedupSuppression() {
        assertTrue(EmailFrequencyPolicy.withinCap(sentInWindow = 1, cap = 3))
        assertFalse(EmailFrequencyPolicy.withinCap(sentInWindow = 3, cap = 3))
        assertTrue(EmailFrequencyPolicy.isDuplicate("k1", alreadyProcessed = true))
        assertFalse(EmailFrequencyPolicy.isDuplicate("k1", alreadyProcessed = false))
        assertTrue(EmailFrequencyPolicy.isSuppressed(bounced = true, complained = false))
        assertTrue(EmailFrequencyPolicy.isSuppressed(bounced = false, complained = true))
        assertFalse(EmailFrequencyPolicy.isSuppressed(bounced = false, complained = false))
    }
}
