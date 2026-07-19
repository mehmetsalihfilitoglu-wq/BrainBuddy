/**
 * EDUmio messaging functions — server-initiated FCM pushes. The local WorkManager reminders remain the
 * PRIMARY mechanism (they work offline); server push is supplementary (e.g. streak-at-risk, re-engagement).
 * Source only — not deployed by the Android build.
 */
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

/** Send a push to every registered device of a user; prune tokens Play/FCM reports as dead. */
async function sendPushToUser(uid, title, body, data) {
  const db = admin.firestore();
  const snap = await db.collection(`users/${uid}/fcmTokens`).get();
  const tokens = snap.docs.map((d) => d.id).filter(Boolean);
  if (!tokens.length) return { sent: 0 };
  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: data || {},
  });
  const removals = [];
  res.responses.forEach((r, i) => {
    const code = !r.success && r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-argument") {
      removals.push(db.doc(`users/${uid}/fcmTokens/${tokens[i]}`).delete());
    }
  });
  await Promise.all(removals);
  return { sent: res.successCount };
}

/** Owner/tester helper to verify push delivery end-to-end. */
const sendTestPush = onCall({ region: "europe-west1" }, async (req) => {
  const uid = req.auth && req.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  return sendPushToUser(uid, "EDUmio", "Test bildirimi ✅");
});

module.exports = { sendPushToUser, sendTestPush };
