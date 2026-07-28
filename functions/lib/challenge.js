/**
 * Pure server-side Daily-Challenge logic (no Firestore), so the trust-critical bits — canonical day from a
 * timezone offset, the 5-question rule, and the streak transition — are unit-testable with mocha. Mirrors
 * the Kotlin `ServerChallengeContract` + `DailyChallengeStreak`, and is the single source used by index.js.
 */
const CHALLENGE_SIZE = 5;
const DAY_MS = 86400000;

/** Canonical YYYY-MM-DD from epoch millis + timezone offset (minutes east of UTC). */
function canonicalDay(nowMs, tzOffsetMinutes) {
  const off = Number.isFinite(tzOffsetMinutes) ? tzOffsetMinutes : 0;
  const d = new Date(nowMs + off * 60000);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/** Exactly CHALLENGE_SIZE distinct, non-empty question ids. */
function isValidProposal(questionIds) {
  return Array.isArray(questionIds) &&
    questionIds.length === CHALLENGE_SIZE &&
    new Set(questionIds).size === CHALLENGE_SIZE &&
    questionIds.every((q) => typeof q === "string" && q.length > 0);
}

/**
 * Next streak on completing [day]. Consecutive → +1; same day → unchanged (idempotent); any gap → reset to
 * 1. Longest never decreases. Matches DailyChallengeStreak.onComplete.
 */
function nextStreak(streak, day, yesterday) {
  const s = streak || { current: 0, longest: 0, lastDay: "" };
  let current;
  if (s.lastDay === day) current = s.current || 0;
  else if (s.lastDay === yesterday) current = (s.current || 0) + 1;
  else current = 1;
  const longest = Math.max(s.longest || 0, current);
  return { current, longest, lastDay: day };
}

module.exports = { CHALLENGE_SIZE, DAY_MS, canonicalDay, isValidProposal, nextStreak };
