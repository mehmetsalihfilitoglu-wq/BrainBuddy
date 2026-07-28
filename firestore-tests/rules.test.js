// EDUmio Firestore security-rules tests (emulator).
// Run:  firebase emulators:exec --only firestore "npm --prefix firestore-tests test"
// These validate ownership isolation + server-authoritative (client-read-only) paths in firestore.rules.
// NOTE: requires the Firebase emulator (owner/CI machine); it is NOT run by the Android/Gradle build.

const fs = require('fs');
const path = require('path');
const assert = require('assert');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, deleteDoc } = require('firebase/firestore');

let testEnv;
const ALICE = 'alice_uid';
const BOB = 'bob_uid';

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'edumio-rules-test',
    firestore: { rules: fs.readFileSync(path.resolve(__dirname, '../firestore.rules'), 'utf8') },
  });
});
after(async () => { await testEnv.cleanup(); });
beforeEach(async () => { await testEnv.clearFirestore(); });

function ctx(uid, email = `${uid}@edumio.app`, emailVerified = true) {
  return testEnv.authenticatedContext(uid, { email, email_verified: emailVerified }).firestore();
}
function anon() { return testEnv.unauthenticatedContext().firestore(); }

describe('users/{uid} profile', () => {
  it('owner can create own profile with matching uid/email', async () => {
    const db = ctx(ALICE);
    await assertSucceeds(setDoc(doc(db, 'users', ALICE), {
      uid: ALICE, email: `${ALICE}@edumio.app`, emailVerified: true,
      displayName: 'Alice', authProvider: 'EMAIL', schemaVersion: 1,
    }));
  });

  it('cannot forge another user\'s profile', async () => {
    const db = ctx(ALICE);
    await assertFails(setDoc(doc(db, 'users', BOB), { uid: BOB, email: `${BOB}@edumio.app` }));
  });

  it('cannot spoof uid inside own doc', async () => {
    const db = ctx(ALICE);
    await assertFails(setDoc(doc(db, 'users', ALICE), { uid: BOB, email: `${ALICE}@edumio.app` }));
  });

  it('anonymous cannot read a profile', async () => {
    await assertFails(getDoc(doc(anon(), 'users', ALICE)));
  });

  it('a different user cannot read the profile', async () => {
    await assertFails(getDoc(doc(ctx(BOB), 'users', ALICE)));
  });
});

describe('users/{uid}/syncRecords', () => {
  it('owner can write a well-formed sync record', async () => {
    const db = ctx(ALICE);
    await assertSucceeds(setDoc(doc(db, 'users', ALICE, 'syncRecords', 'global__settings'), {
      scope: 'GLOBAL', areaId: '', storeKey: 'settings', updatedAt: 123, schemaVersion: 1, payloadJson: '{}',
    }));
  });

  it('rejects an unknown scope', async () => {
    const db = ctx(ALICE);
    await assertFails(setDoc(doc(db, 'users', ALICE, 'syncRecords', 'x'), {
      scope: 'HACK', storeKey: 'settings', updatedAt: 1,
    }));
  });

  it('another user cannot write into your sync records', async () => {
    await assertFails(setDoc(doc(ctx(BOB), 'users', ALICE, 'syncRecords', 'global__settings'), {
      scope: 'GLOBAL', storeKey: 'settings', updatedAt: 1, payloadJson: '{}',
    }));
  });
});

describe('server-authoritative paths (client-read-only)', () => {
  it('client cannot write its own entitlement', async () => {
    await assertFails(setDoc(doc(ctx(ALICE), 'users', ALICE, 'entitlements', 'premium'), { isPremium: true }));
  });

  it('client cannot write a canonical challenge', async () => {
    await assertFails(setDoc(doc(ctx(ALICE), 'users', ALICE, 'challenges', '2026-07-19'), { questionIds: ['q1'] }));
  });

  it('owner CAN read its entitlement (server writes it via Admin SDK)', async () => {
    // Seed via a rules-bypassing admin context, then read as the owner.
    await testEnv.withSecurityRulesDisabled(async (admin) => {
      await setDoc(doc(admin.firestore(), 'users', ALICE, 'entitlements', 'premium'), { isPremium: true });
    });
    await assertSucceeds(getDoc(doc(ctx(ALICE), 'users', ALICE, 'entitlements', 'premium')));
  });
});
