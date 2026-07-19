/**
 * EDUmio Cloud Functions — server-authoritative trust layer.
 *
 * Design: the question banks are bundled in the app, so SELECTION stays on the client; the server owns
 * IDENTITY. The client proposes today's 5 question ids; the server persists exactly ONE immutable challenge
 * per (uid, canonical day) in a transaction (first-writer-wins across devices), validates the 5-question
 * rule, and owns completion + streak. This makes the canonical challenge and streak impossible to forge or
 * duplicate, and defeats device-clock / timezone manipulation, WITHOUT moving any content server-side.
 *
 * Not deployed by the Android build. Deploy with `firebase deploy --only functions` (owner, Blaze plan).
 */
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const functionsV1 = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { CHALLENGE_SIZE, DAY_MS, canonicalDay, isValidProposal, nextStreak } = require("./lib/challenge");

admin.initializeApp();
const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;

// NOTE: region is a deploy detail; align with the owner's Firestore region (guide §6).
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

function requireAuth(req) {
  const uid = req.auth && req.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  return uid;
}

/**
 * claimDailyChallenge — returns the existing immutable challenge for the server's current day, or creates
 * exactly one from the client's proposal. Two devices racing both converge on the first-created challenge.
 * data: { exam?: string, questionIds: string[5], tzOffsetMinutes?: number }
 */
exports.claimDailyChallenge = onCall(async (req) => {
  const uid = requireAuth(req);
  const { exam = null, questionIds = [], tzOffsetMinutes = 0 } = req.data || {};
  const day = canonicalDay(Date.now(), Number(tzOffsetMinutes));
  const ref = db.doc(`users/${uid}/challenges/${day}`);

  return db.runTransaction(async (txn) => {
    const snap = await txn.get(ref);
    if (snap.exists) {
      // Immutable: ignore any new proposal, always return the canonical challenge.
      const d = snap.data();
      return { day, created: false, exam: d.exam, questionIds: d.questionIds, status: d.status };
    }
    if (!isValidProposal(questionIds)) {
      throw new HttpsError("invalid-argument", `Proposal must be exactly ${CHALLENGE_SIZE} distinct question ids.`);
    }
    txn.set(ref, {
      day, exam, questionIds, status: "OPEN",
      createdAt: FieldValue.serverTimestamp(), schemaVersion: 1,
    });
    return { day, created: true, exam, questionIds, status: "OPEN" };
  });
});

/**
 * completeDailyChallenge — records completion for the server's CURRENT day only (defeats back/forward
 * dating), monotonically (idempotent if already completed), and recomputes the authoritative streak from
 * consecutive completed days.
 * data: { day: string, tzOffsetMinutes?: number }
 */
exports.completeDailyChallenge = onCall(async (req) => {
  const uid = requireAuth(req);
  const { day, tzOffsetMinutes = 0 } = req.data || {};
  const serverDay = canonicalDay(Date.now(), Number(tzOffsetMinutes));
  if (day !== serverDay) {
    throw new HttpsError("failed-precondition", "Completion is only allowed for the current day.");
  }
  const chRef = db.doc(`users/${uid}/challenges/${day}`);
  const streakRef = db.doc(`users/${uid}/learningState/streak`);

  return db.runTransaction(async (txn) => {
    const ch = await txn.get(chRef);
    if (!ch.exists) throw new HttpsError("not-found", "No challenge to complete for today.");
    const streak = (await txn.get(streakRef)).data() || { current: 0, longest: 0, lastDay: "" };

    if (ch.data().status === "COMPLETED") {
      return { day, alreadyCompleted: true, current: streak.current || 0, longest: streak.longest || 0 };
    }

    txn.update(chRef, { status: "COMPLETED", completedAt: FieldValue.serverTimestamp() });

    const yesterday = canonicalDay(Date.now() - DAY_MS, Number(tzOffsetMinutes));
    const next = nextStreak(streak, day, yesterday);
    txn.set(streakRef, { ...next, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { day, alreadyCompleted: false, current: next.current, longest: next.longest };
  });
});

/**
 * onUserCreate — server-side minimal profile at users/{uid} on account creation (Phase 1 profile writer,
 * done server-side so it is trustworthy). The client only updates preference fields afterwards.
 */
exports.onUserCreate = functionsV1.region("europe-west1").auth.user().onCreate(async (user) => {
  await db.doc(`users/${user.uid}`).set({
    uid: user.uid,
    email: user.email || null,
    emailVerified: user.emailVerified || false,
    displayName: user.displayName || null,
    photoUrl: user.photoURL || null,
    authProvider: (user.providerData && user.providerData[0] && user.providerData[0].providerId === "google.com")
      ? "GOOGLE" : "EMAIL",
    accountStatus: "ACTIVE",
    schemaVersion: 1,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
});

// Phase 4 (billing) functions — verifyPurchase (Play Developer API) + playRtdnHandler (Pub/Sub) — are added
// in billing/index.js and re-exported here when that phase lands.
