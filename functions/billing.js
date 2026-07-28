/**
 * EDUmio billing functions — server-side Google Play purchase verification + Real-time Developer
 * Notifications (RTDN). The client NEVER writes its own entitlement; only these functions do (the Admin SDK
 * bypasses `firestore.rules`, where `entitlements/*` is client-read-only).
 *
 * Owner setup (guide §10/§11): enable the "Google Play Android Developer API", link Play ↔ GCP, grant the
 * Functions service account access in Play Console, create the subscription products, and wire the RTDN
 * Pub/Sub topic. Source only — not deployed by the Android build.
 */
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const admin = require("firebase-admin");
const { google } = require("googleapis");

const PACKAGE_NAME = "com.edumio.app";
const RTDN_TOPIC = "play-rtdn"; // owner-created Pub/Sub topic name

/** Map Play subscriptionsv2 `subscriptionState` → EDUmio EntitlementState string. */
function mapState(subState) {
  switch (subState) {
    case "SUBSCRIPTION_STATE_ACTIVE": return "ACTIVE";
    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD": return "IN_GRACE_PERIOD";
    case "SUBSCRIPTION_STATE_ON_HOLD": return "ON_HOLD";
    case "SUBSCRIPTION_STATE_PAUSED": return "PAUSED";
    case "SUBSCRIPTION_STATE_CANCELED": return "CANCELED";
    case "SUBSCRIPTION_STATE_EXPIRED": return "EXPIRED";
    default: return "PENDING";
  }
}

async function publisher() {
  const auth = await google.auth.getClient({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  return google.androidpublisher({ version: "v3", auth });
}

/** Verify a token with Play and return {state, expiresAtMs, productId}. */
async function verifyWithPlay(purchaseToken) {
  const api = await publisher();
  const res = await api.purchases.subscriptionsv2.get({
    packageName: PACKAGE_NAME,
    token: purchaseToken,
  });
  const data = res.data || {};
  const line = (data.lineItems && data.lineItems[0]) || {};
  const expiry = line.expiryTime ? Date.parse(line.expiryTime) : null;
  const productId = line.productId || null;
  return { state: mapState(data.subscriptionState), expiresAtMs: expiry, productId };
}

async function writeEntitlement(uid, purchaseToken, verified) {
  const db = admin.firestore();
  const FieldValue = admin.firestore.FieldValue;
  await db.doc(`users/${uid}/entitlements/premium`).set({
    uid,
    state: verified.state,
    expiresAtMs: verified.expiresAtMs,
    productId: verified.productId,
    purchaseToken,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  // Reverse index so RTDN can resolve token → uid.
  await db.doc(`purchaseTokens/${purchaseToken}`).set({ uid, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
}

/**
 * verifyPurchase (callable) — called by the client after a successful Play purchase/restore.
 * data: { productId: string, purchaseToken: string }
 */
const verifyPurchase = onCall({ region: "europe-west1" }, async (req) => {
  const uid = req.auth && req.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const { purchaseToken } = req.data || {};
  if (!purchaseToken) throw new HttpsError("invalid-argument", "purchaseToken required.");
  try {
    const verified = await verifyWithPlay(purchaseToken);
    await writeEntitlement(uid, purchaseToken, verified);
    const now = Date.now();
    const isPremium = verified.state === "ACTIVE" || verified.state === "IN_GRACE_PERIOD" ||
      (verified.state === "CANCELED" && verified.expiresAtMs && verified.expiresAtMs > now);
    return { isPremium, state: verified.state, expiresAtMs: verified.expiresAtMs };
  } catch (e) {
    throw new HttpsError("internal", "Verification failed.", String(e && e.message));
  }
});

/**
 * playRtdnHandler (Pub/Sub) — Play pushes subscription lifecycle events (renew, cancel, expire, hold, …).
 * We re-verify the token and update the entitlement, so state stays correct without the client.
 */
const playRtdnHandler = onMessagePublished({ topic: RTDN_TOPIC, region: "europe-west1" }, async (event) => {
  const db = admin.firestore();
  let payload;
  try {
    payload = JSON.parse(Buffer.from(event.data.message.data, "base64").toString("utf8"));
  } catch (_) {
    return; // unparseable → ignore
  }
  const token = payload &&
    ((payload.subscriptionNotification && payload.subscriptionNotification.purchaseToken) || null);
  if (!token) return;
  const map = await db.doc(`purchaseTokens/${token}`).get();
  const uid = map.exists ? map.data().uid : null;
  if (!uid) return; // unknown token
  const verified = await verifyWithPlay(token);
  await writeEntitlement(uid, token, verified);
});

module.exports = { verifyPurchase, playRtdnHandler, mapState };
