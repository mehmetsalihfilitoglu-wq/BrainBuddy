#!/usr/bin/env node
/**
 * Build the app's bundled IMAT question asset from the source-of-truth JSON under content/imat/.
 *
 * Source of truth: content/imat/<subject>/imat_<year>_<type>.json  (verbatim official IMAT Qs)
 * Output (build artifact): app/src/main/assets/imat/imat_questions.json
 *
 * - Only USABLE questions are included (subject folders). needs_manual_review/ is EXCLUDED
 *   (figure-dependent, not usable until the figure is linked).
 * - The 6 content subjects are mapped to the 4 IMAT exam subjects used by the app
 *   (AdmissionExamRegistry.IMAT): biology, chemistry, physics_math, logic.
 * - Stable ids are preserved verbatim.
 * - correct_answer letter (A-E) -> answerIndex (0-4).
 * - imageAsset stays null for now; figure-completed questions get their asset path here later.
 *
 * Re-run whenever content/imat changes:  node scripts/build_imat_asset.js
 */
const fs = require('fs');
const path = require('path');

const REPO = path.resolve(__dirname, '..');
const SRC = path.join(REPO, 'content', 'imat');
const OUT_DIR = path.join(REPO, 'app', 'src', 'main', 'assets', 'imat');
const OUT = path.join(OUT_DIR, 'imat_questions.json');

// content subject -> app IMAT exam subject code (the 4 registry subjects)
const SUBJECT_MAP = {
  biology: 'biology',
  chemistry: 'chemistry',
  physics: 'physics_math',
  mathematics: 'physics_math',
  general_knowledge: 'logic',
  critical_thinking: 'logic',
};
const LETTER = { A: 0, B: 1, C: 2, D: 3, E: 4 };

const out = [];
const seenIds = new Set();
const skipped = [];
let filesRead = 0;

for (const subjectDir of Object.keys(SUBJECT_MAP)) {
  const dir = path.join(SRC, subjectDir);
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir)) {
    if (!f.endsWith('.json')) continue;
    filesRead++;
    const arr = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    for (const q of arr) {
      const reason = validate(q);
      if (reason) { skipped.push(`${q.id || f}: ${reason}`); continue; }
      if (seenIds.has(q.id)) { skipped.push(`${q.id}: duplicate id`); continue; }
      seenIds.add(q.id);
      const choices = ['A', 'B', 'C', 'D', 'E'].map((k) => String(q.choices[k]).trim());
      out.push({
        id: q.id,
        examSubject: SUBJECT_MAP[subjectDir],   // biology | chemistry | physics_math | logic
        contentSubject: q.subject,              // fine-grained origin subject (for future stats)
        section: q.section || null,
        topic: q.topic || null,
        year: q.source_year,
        sourceType: q.source_type,
        stem: q.question,
        choices: choices,
        answerIndex: LETTER[q.correct_answer],
        image: q.image || null,                 // reserved for figure-completed questions
        difficulty: 1,                          // MEDIUM default (no per-item difficulty in source)
      });
    }
  }
}

function validate(q) {
  if (!q.id) return 'missing id';
  if (!q.question || !String(q.question).trim()) return 'empty stem';
  if (!q.choices) return 'no choices';
  for (const k of ['A', 'B', 'C', 'D', 'E']) {
    if (q.choices[k] == null || String(q.choices[k]).trim() === '') return `empty choice ${k}`;
  }
  if (!(q.correct_answer in LETTER)) return `bad correct_answer ${q.correct_answer}`;
  return null;
}

out.sort((a, b) => (a.year - b.year) || a.id.localeCompare(b.id));

fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(out, null, 0) + '\n');

const bySubject = {};
for (const q of out) bySubject[q.examSubject] = (bySubject[q.examSubject] || 0) + 1;
console.log(`files read: ${filesRead}`);
console.log(`written: ${out.length} questions -> ${path.relative(REPO, OUT)}`);
console.log(`by exam subject: ${JSON.stringify(bySubject)}`);
console.log(`skipped: ${skipped.length}`);
if (skipped.length) console.log(skipped.slice(0, 20).join('\n'));
