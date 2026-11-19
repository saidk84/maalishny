"use strict";
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const { createHmac } = require("node:crypto");
const { validateMessage, validPhone } = require("./validation");
initializeApp();
const db = getFirestore();
const directoryKey = defineSecret("DIRECTORY_HMAC_KEY");
const options = { region: "us-central1", maxInstances: 2, enforceAppCheck: true,
  secrets: [directoryKey] };
const DAYS = 7 * 86400_000;
async function identity(request) {
  if (!request.auth || !validPhone(request.auth.token.phone_number))
    throw new HttpsError("unauthenticated", "Verify your mobile number first.");
  const user = await getAuth().getUser(request.auth.uid).catch(() => null);
  if (!user || user.disabled || user.phoneNumber !== request.auth.token.phone_number)
    throw new HttpsError("unauthenticated", "Sign in again.");
  return { uid: request.auth.uid, phone: request.auth.token.phone_number };
}
function phoneKey(phone) {
  return createHmac("sha256", directoryKey.value()).update(phone).digest("hex");
}
async function rate(uid, action, maximum) {
  const window = Math.floor(Date.now() / 3600_000);
  const ref = db.doc("limits/" + uid + "-" + action + "-" + window);
  await db.runTransaction(async tx => {
    const previous = await tx.get(ref);
    const count = previous.exists ? previous.data().count : 0;
    if (count >= maximum) throw new HttpsError("resource-exhausted", "Try again later.");
    tx.set(ref, { count: count + 1, expiresAt: Timestamp.fromMillis((window + 2) * 3600_000) });
  });
}
exports.registerDevice = onCall(options, async request => {
  const { uid, phone } = await identity(request);
  await rate(uid, "register", 60);
  const { token, online, visible } = request.data || {};
  if (typeof token !== "string" || token.length > 4096 ||
      typeof online !== "boolean" || typeof visible !== "boolean")
    throw new HttpsError("invalid-argument", "Invalid device settings.");
  const key = phoneKey(phone);
  await db.runTransaction(async tx => {
    const ref = db.doc("profiles/" + uid);
    const old = await tx.get(ref);
    const oldKey = old.exists ? old.data().phoneKey : null;
    if (oldKey && oldKey !== key) tx.delete(db.doc("directory/" + oldKey));
    // Latest verified session is the receiving device for this account.
    tx.set(ref, { token, online, visible, phoneKey: key });
    tx.set(db.doc("directory/" + key), { uid });
  });
  return { ok: true };
});
exports.findContacts = onCall(options, async request => {
  const { uid } = await identity(request);
  await rate(uid, "lookup", 12);
  const phones = request.data?.phones;
  if (!Array.isArray(phones) || phones.length < 1 || phones.length > 100 ||
      !phones.every(validPhone))
    throw new HttpsError("invalid-argument", "Choose up to 100 contacts.");
  const unique = [...new Set(phones)];
  const records = await db.getAll(...unique.map(phone => db.doc("directory/" + phoneKey(phone))));
  const matches = [];
  for (let i = 0; i < records.length; i++) {
    if (!records[i].exists || records[i].data().uid === uid) continue;
    const other = records[i].data().uid;
    const profile = await db.doc("profiles/" + other).get();
    if (profile.exists) {
      const p = profile.data();
      matches.push({ phone: unique[i], uid: other, online: p.visible ? p.online : null });
    }
  }
  return { matches };
});
exports.sendComfort = onCall(options, async request => {
  const { uid, phone } = await identity(request);
  const { recipients, text, kind, id } = request.data || {};
  if (!Array.isArray(recipients) || recipients.length < 1 || recipients.length > 10 ||
      !recipients.every(x => typeof x === "string" && /^[a-zA-Z0-9_-]{1,128}$/.test(x)) ||
      new Set(recipients).size !== recipients.length || recipients.includes(uid) ||
      !validateMessage(text, kind) || typeof id !== "string" ||
      !/^[a-f0-9-]{36}$/.test(id))
    throw new HttpsError("invalid-argument", "Invalid message.");
  await rate(uid, "send", 30);
  const marker = db.doc("sent/" + uid + "-" + id);
  const profiles = recipients.map(other => db.doc("profiles/" + other));
  const result = await db.runTransaction(async tx => {
    const old = await tx.get(marker);
    if (old.exists) return { queued: false, tokens: [] };
    const rows = await tx.getAll(...profiles);
    if (rows.some(row => !row.exists))
      throw new HttpsError("not-found", "A recipient is no longer registered.");
    const expiresAt = Timestamp.fromMillis(Date.now() + DAYS);
    const createdAt = Date.now();
    recipients.forEach(other => tx.create(db.doc("mail/" + other + "-" + uid + "-" + id),
      { recipient: other, sender: uid, phone, text: text.trim(), kind, createdAt, expiresAt }));
    tx.create(marker, { expiresAt });
    return { queued: true, tokens: rows.map(row => row.data())
      .filter(p => p.online && p.token).map(p => p.token) };
  });
  // A failed push leaves the durable queue available for a foreground sync.
  if (result.tokens.length) {
    try {
      await getMessaging().sendEachForMulticast({
        tokens: [...new Set(result.tokens)], data: { sync: "comfort" },
        android: { priority: "high", ttl: DAYS }
      });
    } catch (error) {
      console.warn("Push unavailable; message remains queued", error.code);
    }
  }
  return { ok: true };
});
exports.fetchInbox = onCall(options, async request => {
  const { uid } = await identity(request);
  await rate(uid, "inbox", 240);
  const profile = await db.doc("profiles/" + uid).get();
  if (!profile.exists || !profile.data().online) return { messages: [] };
  const rows = await db.collection("mail").where("recipient", "==", uid).limit(100).get();
  return { messages: rows.docs.filter(row => row.data().expiresAt.toMillis() > Date.now())
    .map(row => {
      const p = row.data();
      return { id: row.id, person: p.phone, text: p.text, kind: p.kind, time: p.createdAt };
    }) };
});
exports.acknowledge = onCall(options, async request => {
  const { uid } = await identity(request);
  await rate(uid, "ack", 240);
  const ids = request.data?.ids;
  if (!Array.isArray(ids) || ids.length > 100 || !ids.every(id =>
    typeof id === "string" && /^[a-zA-Z0-9_-]{1,350}$/.test(id)))
    throw new HttpsError("invalid-argument", "Invalid delivery receipt.");
  await db.runTransaction(async tx => {
    if (!ids.length) return;
    const rows = await tx.getAll(...ids.map(id => db.doc("mail/" + id)));
    rows.filter(row => row.exists).forEach(row => {
      if (row.data().recipient !== uid) throw new HttpsError("permission-denied", "Invalid receipt.");
      tx.delete(row.ref);
    });
  });
  return { ok: true };
});
exports.deleteAccount = onCall(options, async request => {
  const { uid, phone } = await identity(request);
  if (Date.now() / 1000 - request.auth.token.auth_time > 300)
    throw new HttpsError("failed-precondition", "Sign in again before deleting your account.");
  await rate(uid, "delete", 5);
  await db.doc("profiles/" + uid).delete();
  await db.doc("directory/" + phoneKey(phone)).delete();
  for (const field of ["sender", "recipient"]) {
    let rows;
    do {
      rows = await db.collection("mail").where(field, "==", uid).limit(400).get();
      const batch = db.batch();
      rows.forEach(row => batch.delete(row.ref));
      if (!rows.empty) await batch.commit();
    } while (rows.size === 400);
  }
  await getAuth().deleteUser(uid);
  return { ok: true };
});
exports.expireUndelivered = onSchedule(
  { region: "us-central1", schedule: "every 60 minutes", maxInstances: 1 },
  async () => {
    // Bounded work per invocation keeps cleanup costs predictable for the pilot.
    for (const collection of ["mail", "sent", "limits"]) {
      for (let page = 0; page < 10; page++) {
        const rows = await db.collection(collection)
          .where("expiresAt", "<=", Timestamp.now()).limit(400).get();
        if (rows.empty) break;
        const batch = db.batch();
        rows.forEach(row => batch.delete(row.ref));
        await batch.commit();
        if (rows.size < 400) break;
      }
    }
  });
