const test = require("node:test");
const assert = require("node:assert/strict");
const { validateMessage, validPhone } = require("./validation");
test("accepts Arabic and English short messages and the existing four-word preset", () => {
  for (const text of ["أنا معك", "Thinking of you", "I’m here for you"])
    assert.equal(validateMessage(text, "Comfort message"), true);
});
test("rejects unbounded text, empty text and custom requests", () => {
  for (const text of ["", "   ", "a b c d", "x".repeat(161)])
    assert.equal(validateMessage(text, "Comfort message"), false);
  assert.equal(validateMessage("Please help", "Comfort request"), false);
  assert.equal(validateMessage("معلشني", "Comfort request"), true);
});
test("requires international phone numbers", () => {
  assert.equal(validPhone("+971501234567"), true);
  for (const phone of ["0501234567", "+00000000", {}, "+971 50 1234567"])
    assert.equal(validPhone(phone), false);
});
