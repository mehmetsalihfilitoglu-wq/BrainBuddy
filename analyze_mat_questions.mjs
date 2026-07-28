#!/usr/bin/env node
/** Analyze MAT LGS JSON files: parse failures, short_item (exact + heuristic). */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
const __dirname = path.dirname(fileURLToPath(import.meta.url));

const MAT_DIR = path.join(__dirname, "app/src/main/assets/lgs_import/mat");

const PROBLEM_KEYWORDS = [
  "problemi", "problem", "oran", "yüzde", "grafik", "tablo",
  "şekilde", "aşağıdaki", "metne göre", "parçaya göre"
];

function getStem(q) {
  return ((q.stem || q.questionText || "") + "").trim();
}

function parseLgsQuestion(o, index) {
  const stem = ((o.stem || o.questionText || "") + "").trim();
  if (!stem) return { fail: true, reason: "stem_blank" };

  const optionsRaw = o.options || o.choices;
  if (optionsRaw == null) return { fail: true, reason: "options_missing" };

  const arr = Array.isArray(optionsRaw) ? optionsRaw : [];
  const rawOpts = arr
    .map((x) => (x != null ? String(x).trim() : ""))
    .filter((s) => s.length > 0);

  if (rawOpts.length < 2) {
    return { fail: true, reason: `fewer_than_2_options_after_filter (${rawOpts.length} valid)` };
  }

  return { fail: false, stem, questionType: (o.questionType || "").trim() };
}

function hasProblemKeyword(stem) {
  const s = stem.toLowerCase();
  return PROBLEM_KEYWORDS.some((kw) => s.includes(kw));
}

function isShortItemHeuristic(stem, questionType) {
  const stemLen = stem.length;
  if (questionType === "short_item") return true;
  if (stemLen < 80) return true;
  if (stemLen < 120 && !hasProblemKeyword(stem)) return true;
  return false;
}

const parseFailures = [];
const shortItemExact = [];
const shortItemHeuristic = [];

const files = fs.readdirSync(MAT_DIR).filter((f) => f.endsWith(".json")).sort();

for (const fn of files) {
  const filepath = path.join(MAT_DIR, fn);
  const relPath = path.join("app/src/main/assets/lgs_import/mat", fn);
  let data;
  try {
    data = JSON.parse(fs.readFileSync(filepath, "utf8"));
  } catch (e) {
    parseFailures.push({
      file: relPath,
      index: -1,
      stem_preview: "",
      reason: `file_load_error: ${e.message}`,
    });
    continue;
  }

  const questions = data.questions || [];
  for (let i = 0; i < questions.length; i++) {
    const q = questions[i];
    const result = parseLgsQuestion(q, i);

    if (result.fail) {
      const stemPreview = (getStem(q) || "(no stem)").slice(0, 80);
      parseFailures.push({
        file: relPath,
        index: i,
        stem_preview: stemPreview,
        reason: result.reason,
      });
    } else {
      const fullQ = { ...q, file: relPath, index: i };
      if (result.questionType === "short_item") {
        shortItemExact.push(fullQ);
      }
      if (isShortItemHeuristic(result.stem, result.questionType)) {
        shortItemHeuristic.push({
          file: relPath,
          index: i,
          stem: result.stem,
          stem_length: result.stem.length,
          questionType: result.questionType,
          full_question: fullQ,
        });
      }
    }
  }
}

// Output
console.log("=".repeat(60));
console.log("1. PARSE-FAILING QUESTIONS (parseLgsQuestion returns null)");
console.log("=".repeat(60));
for (const p of parseFailures) {
  console.log(`  File: ${p.file}`);
  console.log(`  Index: ${p.index}`);
  console.log(`  Stem preview: ${(p.stem_preview || "").slice(0, 80)}`);
  console.log(`  Reason: ${p.reason}`);
  console.log();
}

console.log("=".repeat(60));
console.log("2. SHORT_ITEM (questionType == 'short_item' exact match)");
console.log("=".repeat(60));
for (const q of shortItemExact) {
  const stem = getStem(q);
  console.log(`  File: ${q.file}, Index: ${q.index}`);
  console.log(`  Stem: ${stem.slice(0, 100)}${stem.length > 100 ? "..." : ""}`);
  console.log(`  Full question:`, JSON.stringify(q, null, 2).slice(0, 500) + "...");
  console.log();
}

console.log("=".repeat(60));
console.log("3. SHORT_ITEM (scan_short_items heuristic)");
console.log("=".repeat(60));
for (const s of shortItemHeuristic) {
  console.log(`  File: ${s.file}, Index: ${s.index}`);
  console.log(`  Stem length: ${s.stem_length}, questionType: ${s.questionType}`);
  console.log(`  Stem: ${s.stem.slice(0, 100)}${s.stem.length > 100 ? "..." : ""}`);
  console.log();
}

const output = {
  parse_failures: parseFailures,
  short_item_exact: shortItemExact,
  short_item_heuristic: shortItemHeuristic,
};
const outPath = path.join(__dirname, "mat_analysis_output.json");
fs.writeFileSync(outPath, JSON.stringify(output, null, 2), "utf8");
console.log(`\nFull output saved to: ${outPath}`);
console.log(
  `Summary: ${parseFailures.length} parse failures, ${shortItemExact.length} short_item exact, ${shortItemHeuristic.length} short_item heuristic`
);
