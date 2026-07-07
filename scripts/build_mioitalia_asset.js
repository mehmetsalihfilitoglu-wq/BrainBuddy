#!/usr/bin/env node
/**
 * Build the Mioitalia ORIGINAL question asset from content/mioitalia/.
 *
 * Source of truth: content/mioitalia/<subject>/*.json  (+ _calibration/*.json)  — original items.
 * Output (build artifact): app/src/main/assets/mioitalia/questions.json
 *
 * Kept fully SEPARATE from the official IMAT asset (app/src/main/assets/imat/imat_questions.json).
 * The app seeds these into Room as examType='MIOITALIA', a pool isolated from official IMAT and LGS.
 *
 * Re-run whenever content/mioitalia changes:  node scripts/build_mioitalia_asset.js
 */
const fs = require('fs');
const path = require('path');

const REPO = path.resolve(__dirname, '..');
const SRC = path.join(REPO, 'content', 'mioitalia');
const OUT_DIR = path.join(REPO, 'app', 'src', 'main', 'assets', 'mioitalia');
const OUT = path.join(OUT_DIR, 'questions.json');

// content subject -> app IMAT-style exam subject code (same 4 buckets as the official pool)
const SUBJECT_MAP = {
  biology: 'biology',
  chemistry: 'chemistry',
  physics: 'physics_math',
  mathematics: 'physics_math',
  critical_thinking: 'logic',
  general_knowledge: 'logic',
};
const DIFF = { medium: 1, hard: 2, very_hard: 3 };
const LETTERS = ['A', 'B', 'C', 'D', 'E'];

// gather all question files: per-subject dirs + the calibration slice
const files = [];
for (const sub of Object.keys(SUBJECT_MAP)) {
  const d = path.join(SRC, sub);
  if (fs.existsSync(d)) for (const f of fs.readdirSync(d)) if (f.endsWith('.json')) files.push(path.join(d, f));
}
const calDir = path.join(SRC, '_calibration');
if (fs.existsSync(calDir)) for (const f of fs.readdirSync(calDir)) if (f.endsWith('.json')) files.push(path.join(calDir, f));

const out = [];
const seen = new Set();
const skipped = [];
for (const f of files) {
  const arr = JSON.parse(fs.readFileSync(f, 'utf8'));
  for (const q of arr) {
    const reason = validate(q);
    if (reason) { skipped.push(`${q.id || path.basename(f)}: ${reason}`); continue; }
    if (seen.has(q.id)) { skipped.push(`${q.id}: duplicate id`); continue; }
    seen.add(q.id);
    const choices = LETTERS.map((k) => String(q.choices[k]).trim());
    out.push({
      id: q.id,
      origin: 'mioitalia',
      examSubject: SUBJECT_MAP[q.subject],
      contentSubject: q.subject,
      topic: q.topic || null,
      subtopic: q.subtopic || null,
      difficulty: DIFF[q.difficulty] || 2,
      difficultyLabel: q.difficulty,
      estimatedTimeSeconds: q.estimated_time_seconds || null,
      tags: q.tags || [],
      learningObjective: q.learning_objective || null,
      stem: q.question,
      choices,
      answerIndex: LETTERS.indexOf(q.correct_answer),
      image: q.image || null,
      explanation: q.explanation || null,
      optionAnalysis: q.option_analysis || null,
    });
  }
}

function validate(q) {
  if (!q.id || !/^mio_/.test(q.id)) return 'missing/invalid id';
  if (!SUBJECT_MAP[q.subject]) return `unknown subject ${q.subject}`;
  if (!q.question || !String(q.question).trim()) return 'empty stem';
  if (!q.choices) return 'no choices';
  for (const k of LETTERS) if (q.choices[k] == null || String(q.choices[k]).trim() === '') return `empty choice ${k}`;
  if (!LETTERS.includes(q.correct_answer)) return `bad correct_answer ${q.correct_answer}`;
  if (!q.difficulty || !DIFF[q.difficulty]) return `bad difficulty ${q.difficulty}`;
  return null;
}

out.sort((a, b) => a.id.localeCompare(b.id));
fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(out, null, 0) + '\n');

const bySubject = {};
const byDiff = {};
for (const q of out) { bySubject[q.examSubject] = (bySubject[q.examSubject] || 0) + 1; byDiff[q.difficultyLabel] = (byDiff[q.difficultyLabel] || 0) + 1; }
console.log(`files read: ${files.length}`);
console.log(`written: ${out.length} original questions -> ${path.relative(REPO, OUT)}`);
console.log(`by exam subject: ${JSON.stringify(bySubject)}`);
console.log(`by difficulty: ${JSON.stringify(byDiff)}`);
console.log(`skipped: ${skipped.length}`);
if (skipped.length) console.log(skipped.slice(0, 20).join('\n'));
