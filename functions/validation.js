"use strict";
const presets = new Set(["معلش", "معلهش", "It’s okay", "It’ll pass", "Don’t worry",
  "أنا معك", "I’m here for you", "معلشني", "Comfort me"]);
function validateMessage(text, kind) {
  if (typeof text !== "string" || !text.trim() || text.length > 160) return false;
  if (!["Comfort message", "Comfort request"].includes(kind)) return false;
  return presets.has(text) || (kind === "Comfort message" && text.trim().split(/\s+/u).length <= 3);
}
function validPhone(phone) {
  return typeof phone === "string" && /^\+[1-9]\d{7,14}$/.test(phone);
}
module.exports = { validateMessage, validPhone };
