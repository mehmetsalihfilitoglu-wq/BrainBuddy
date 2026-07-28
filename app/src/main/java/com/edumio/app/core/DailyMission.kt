package com.edumio.app.core

/**
 * Lightweight daily mission: small goals to encourage daily study.
 *
 * @param date ISO-like YYYY-MM-DD string for the local day.
 * @param testsTarget how many tests to complete today.
 * @param testsDone how many tests completed so far today.
 * @param retryTarget how many wrong questions to solve correctly today.
 * @param retryDone how many wrong questions have been corrected today.
 * @param weakTopicTarget how many tests to take from the weakest topic.
 * @param weakTopicDone how many such tests have been done today.
 */
data class DailyMission(
    val date: String,
    val testsTarget: Int,
    val testsDone: Int,
    val retryTarget: Int,
    val retryDone: Int,
    val weakTopicTarget: Int,
    val weakTopicDone: Int
)

