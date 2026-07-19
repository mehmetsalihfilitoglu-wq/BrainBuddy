/**
 * EDUmio email — provider-agnostic lifecycle email. Verification + password-reset are sent natively by
 * Firebase Auth; this layer covers receipts (transactional), learning reports/reminders (opt-out), and
 * marketing (opt-in). The owner wires ONE transactional provider (SendGrid/SES/Postmark, guide §12); its
 * credential comes from Functions config / Secret Manager, NEVER the repo. Source only.
 *
 * Guarantees enforced here: consent (transactional always; learning opt-out; marketing opt-in),
 * suppression (hard bounce / complaint), idempotency/dedup, and — by contract — NO solution bodies or
 * answer data are ever emailed (callers pass only safe report summaries).
 */
const admin = require("firebase-admin");
const { onRequest } = require("firebase-functions/v2/https");

const CATEGORY = { TRANSACTIONAL: "TRANSACTIONAL", LEARNING: "LEARNING", MARKETING: "MARKETING" };

/** Provider seam — replace the body once the owner supplies a provider + credential. */
async function sendViaProvider(to, subject, html, text) {
  // Example (SendGrid):
  //   const sg = require("@sendgrid/mail");
  //   sg.setApiKey(process.env.EMAIL_API_KEY);          // from Secret Manager, never the repo
  //   await sg.send({ to, from: process.env.EMAIL_FROM, subject, html, text });
  throw new Error("Email provider not configured — see docs/OWNER_CONSOLE_SETUP_GUIDE.md §12.");
}

function canSend(category, prefs) {
  if (category === CATEGORY.TRANSACTIONAL) return true;
  if (category === CATEGORY.LEARNING) return prefs.learningEnabled !== false; // opt-out (default on)
  if (category === CATEGORY.MARKETING) return prefs.marketingConsent === true; // opt-in (default off)
  return false;
}

/** Governed send: suppression → dedup → consent → provider. Returns {sent, reason?}. */
async function sendEmail({ uid, to, category, subject, html, text, idempotencyKey }) {
  const db = admin.firestore();
  const FieldValue = admin.firestore.FieldValue;

  if ((await db.doc(`emailSuppression/${to}`).get()).exists) return { sent: false, reason: "suppressed" };
  if (idempotencyKey && (await db.doc(`emailSends/${idempotencyKey}`).get()).exists) {
    return { sent: false, reason: "duplicate" };
  }
  const prefsSnap = uid ? await db.doc(`users/${uid}/preferences/email`).get() : null;
  const prefs = prefsSnap && prefsSnap.exists ? prefsSnap.data() : {};
  if (!canSend(category, prefs)) return { sent: false, reason: "no-consent" };

  await sendViaProvider(to, subject, html, text);
  if (idempotencyKey) {
    await db.doc(`emailSends/${idempotencyKey}`).set({ uid: uid || null, category, at: FieldValue.serverTimestamp() });
  }
  return { sent: true };
}

/** Provider webhook → record hard bounce / complaint as suppression. Owner points the provider here. */
const emailWebhook = onRequest({ region: "europe-west1" }, async (req, res) => {
  try {
    const db = admin.firestore();
    const events = Array.isArray(req.body) ? req.body : [req.body];
    for (const e of events) {
      const type = (e && (e.event || e.type) || "").toLowerCase();
      const addr = e && (e.email || e.recipient);
      if (addr && (type.includes("bounce") || type.includes("complaint") || type.includes("spam"))) {
        await db.doc(`emailSuppression/${addr}`).set(
          { reason: type, at: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
      }
    }
  } catch (_) { /* ignore malformed webhook */ }
  res.status(200).send("ok");
});

module.exports = { sendEmail, canSend, emailWebhook, CATEGORY };
