// Security-rules tests for firestore.rules, run by the OFFICIAL Firestore emulator (the same rules
// engine as production). They use the emulator's offline "demo-" project, never a real project.
//
//   cd firebase-tests && npm run test:emulator
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc,
} from 'firebase/firestore';

const PROJECT = 'demo-pickupplay';
const RULES = readFileSync(fileURLToPath(new URL('../firestore.rules', import.meta.url)), 'utf8');
const ID = '3f1c2a7e-1111-4222-8333-444455556666';
const OTHER_ID = '9a8b7c6d-1111-4222-8333-444455556666';

let env;

/** A valid session document as the app writes it (RemoteSession.toFields + serverCreatedAt). */
function session(ownerUid, overrides = {}) {
  return {
    ownerUid,
    sport: 'TENNIS',
    title: 'Doubles at Old College',
    location: 'Old College Field',
    day: 'SATURDAY',
    timeSlot: 'MORNING',
    skill: 'CASUAL',
    maxPlayers: 4,
    playerCount: 1,
    hostName: 'alice',
    notes: '',
    createdAt: 1_763_900_000_000,
    serverCreatedAt: serverTimestamp(),
    schemaVersion: 1,
    ...overrides,
  };
}

const anon = () => env.unauthenticatedContext().firestore();
const asUser = (uid) => env.authenticatedContext(uid, { email: `${uid}@example.com` }).firestore();

/** Writes a document owned by `owner` with rules bypassed (test setup only). */
async function seed(id, owner) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const { serverCreatedAt, ...rest } = session(owner);
    await setDoc(doc(ctx.firestore(), 'sessions', id), { ...rest, serverCreatedAt: new Date() });
  });
}

before(async () => {
  env = await initializeTestEnvironment({ projectId: PROJECT, firestore: { rules: RULES } });
});
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());

describe('unauthenticated requests are denied', () => {
  test('cannot read a session document', async () => {
    await seed(ID, 'alice');
    await assertFails(getDoc(doc(anon(), 'sessions', ID)));
  });
  test('cannot list the sessions collection', async () => {
    await seed(ID, 'alice');
    await assertFails(getDocs(collection(anon(), 'sessions')));
  });
  test('cannot create a session', async () => {
    await assertFails(setDoc(doc(anon(), 'sessions', ID), session('alice')));
  });
});

describe('authenticated users', () => {
  test('can read and list sessions', async () => {
    await seed(ID, 'alice');
    await assertSucceeds(getDoc(doc(asUser('bob'), 'sessions', ID)));
    await assertSucceeds(getDocs(collection(asUser('bob'), 'sessions')));
  });
  test('can create a session under their own UID', async () => {
    await assertSucceeds(setDoc(doc(asUser('alice'), 'sessions', ID), session('alice')));
  });
  test('can re-upload (idempotent retry) their own session', async () => {
    const db = asUser('alice');
    await assertSucceeds(setDoc(doc(db, 'sessions', ID), session('alice')));
    await assertSucceeds(setDoc(doc(db, 'sessions', ID), session('alice')));
  });
});

describe('no impersonation or tampering', () => {
  test('cannot create a session owned by another UID', async () => {
    await assertFails(setDoc(doc(asUser('mallory'), 'sessions', ID), session('alice')));
  });
  test("cannot overwrite another host's session", async () => {
    await seed(ID, 'alice');
    await assertFails(setDoc(doc(asUser('mallory'), 'sessions', ID), session('mallory')));
  });
  test("cannot edit fields of another host's session", async () => {
    await seed(ID, 'alice');
    await assertFails(updateDoc(doc(asUser('mallory'), 'sessions', ID), { title: 'Hijacked' }));
  });
  test('owner cannot transfer a session to another UID', async () => {
    await seed(ID, 'alice');
    await assertFails(setDoc(doc(asUser('alice'), 'sessions', ID), session('bob')));
  });
  test('nobody can delete sessions', async () => {
    await seed(ID, 'alice');
    await assertFails(deleteDoc(doc(asUser('alice'), 'sessions', ID)));
    await assertFails(deleteDoc(doc(asUser('mallory'), 'sessions', ID)));
  });
  test('other collections are closed', async () => {
    await assertFails(setDoc(doc(asUser('alice'), 'users', 'alice'), { admin: true }));
    await assertFails(getDoc(doc(asUser('alice'), 'users', 'alice')));
  });
});

describe('field validation', () => {
  const create = (data, id = OTHER_ID) => setDoc(doc(asUser('alice'), 'sessions', id), data);

  test('rejects an unknown sport', async () => {
    await assertFails(create(session('alice', { sport: 'CURLING' })));
  });
  test('rejects an empty or too-long title', async () => {
    await assertFails(create(session('alice', { title: '' })));
    await assertFails(create(session('alice', { title: 'x'.repeat(61) })));
  });
  test('rejects player counts outside the limits', async () => {
    await assertFails(create(session('alice', { maxPlayers: 31 })));
    await assertFails(create(session('alice', { maxPlayers: 1 })));
    await assertFails(create(session('alice', { playerCount: 5, maxPlayers: 4 })));
    await assertFails(create(session('alice', { maxPlayers: '4' })));
  });
  test('rejects extra or missing fields', async () => {
    await assertFails(create(session('alice', { isAdmin: true })));
    const { notes, ...missing } = session('alice');
    await assertFails(create(missing));
  });
  test('rejects a client-chosen creation timestamp', async () => {
    await assertFails(create(session('alice', { serverCreatedAt: new Date(0) })));
  });
  test('rejects a malformed document id', async () => {
    await assertFails(create(session('alice'), 'short'));
  });
  test('accepts the maximum allowed lengths', async () => {
    await assertSucceeds(create(session('alice', {
      title: 'x'.repeat(60), location: 'y'.repeat(80), notes: 'z'.repeat(300), maxPlayers: 30,
    })));
  });
});
