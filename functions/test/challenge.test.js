// Pure server-logic tests. Run: npm --prefix functions test  (Node/mocha; not run by the Gradle build).
const assert = require("assert");
const { canonicalDay, isValidProposal, nextStreak } = require("../lib/challenge");

describe("canonicalDay", () => {
  const utcNoon = Date.UTC(2026, 6, 19, 12, 0, 0); // 2026-07-19T12:00Z

  it("returns the UTC date at offset 0", () => {
    assert.strictEqual(canonicalDay(utcNoon, 0), "2026-07-19");
  });

  it("a positive offset can roll to the next day", () => {
    const lateUtc = Date.UTC(2026, 6, 19, 23, 0, 0); // 23:00Z
    assert.strictEqual(canonicalDay(lateUtc, 180), "2026-07-20"); // +3h → 02:00 next day
  });

  it("a negative offset can roll to the previous day", () => {
    const earlyUtc = Date.UTC(2026, 6, 19, 1, 0, 0); // 01:00Z
    assert.strictEqual(canonicalDay(earlyUtc, -180), "2026-07-18"); // -3h → 22:00 prev day
  });
});

describe("isValidProposal", () => {
  it("accepts exactly 5 distinct ids", () => {
    assert.ok(isValidProposal(["a", "b", "c", "d", "e"]));
  });
  it("rejects wrong count, duplicates, blanks, non-arrays", () => {
    assert.ok(!isValidProposal(["a", "b", "c", "d"]));
    assert.ok(!isValidProposal(["a", "b", "c", "d", "e", "f"]));
    assert.ok(!isValidProposal(["a", "b", "c", "d", "a"]));
    assert.ok(!isValidProposal(["a", "b", "c", "d", ""]));
    assert.ok(!isValidProposal("nope"));
  });
});

describe("nextStreak", () => {
  it("consecutive day increments", () => {
    assert.deepStrictEqual(
      nextStreak({ current: 3, longest: 5, lastDay: "2026-07-18" }, "2026-07-19", "2026-07-18"),
      { current: 4, longest: 5, lastDay: "2026-07-19" });
  });
  it("gap resets to 1 but keeps longest", () => {
    assert.deepStrictEqual(
      nextStreak({ current: 9, longest: 9, lastDay: "2026-07-01" }, "2026-07-19", "2026-07-18"),
      { current: 1, longest: 9, lastDay: "2026-07-19" });
  });
  it("first ever completion is 1", () => {
    assert.deepStrictEqual(
      nextStreak(undefined, "2026-07-19", "2026-07-18"),
      { current: 1, longest: 1, lastDay: "2026-07-19" });
  });
});
