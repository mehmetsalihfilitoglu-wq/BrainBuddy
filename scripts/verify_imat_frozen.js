#!/usr/bin/env node
/**
 * Freeze guard for the official IMAT bank.
 *
 * The official question files under content/imat/<subject>/*.json are READ-ONLY and must match the
 * source PDFs verbatim (question text, options, figures, answer keys). This script fingerprints them
 * and fails if any change since the frozen manifest — so an accidental edit is caught in review/CI.
 *
 *   node scripts/verify_imat_frozen.js          # verify against the manifest (exit 1 on drift)
 *   node scripts/verify_imat_frozen.js --write   # (re)generate the manifest — only for an intentional,
 *                                                 # reviewed change to official content
 *
 * Enrichment (difficulty, tags, explanations, notes) lives in content/imat/metadata/ and is NOT
 * covered by this manifest — that layer is meant to change.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const REPO = path.resolve(__dirname, '..');
const SRC = path.join(REPO, 'content', 'imat');
const MANIFEST = path.join(SRC, 'OFFICIAL_MANIFEST.sha256');
const SUBJECTS = ['biology', 'chemistry', 'physics', 'mathematics', 'general_knowledge', 'critical_thinking'];

// line-ending-normalized hash so autocrlf / platform differences don't cause false drift
function hashFile(f) {
  const norm = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
  return crypto.createHash('sha256').update(norm).digest('hex');
}

const rows = [];
for (const s of SUBJECTS) {
  const dir = path.join(SRC, s);
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.json')).sort()) {
    const rel = `${s}/${f}`;
    rows.push(`${hashFile(path.join(dir, f))}  ${rel}`);
  }
}
rows.sort();
const current = rows.join('\n') + '\n';

const write = process.argv.includes('--write');
if (write) {
  fs.writeFileSync(MANIFEST, current);
  console.log(`Wrote frozen manifest for ${rows.length} official files -> ${path.relative(REPO, MANIFEST)}`);
  process.exit(0);
}

if (!fs.existsSync(MANIFEST)) {
  console.error('No OFFICIAL_MANIFEST.sha256 found. Run with --write to create it.');
  process.exit(2);
}
const expected = fs.readFileSync(MANIFEST, 'utf8');
if (expected === current) {
  console.log(`OK: all ${rows.length} official IMAT files match the frozen manifest.`);
  process.exit(0);
}
// report the drift
const expMap = new Map(expected.trim().split('\n').map((l) => { const [h, ...r] = l.split(/\s+/); return [r.join(' '), h]; }));
const curMap = new Map(rows.map((l) => { const [h, ...r] = l.split(/\s+/); return [r.join(' '), h]; }));
const problems = [];
for (const [rel, h] of curMap) {
  if (!expMap.has(rel)) problems.push(`ADDED:    ${rel}`);
  else if (expMap.get(rel) !== h) problems.push(`CHANGED:  ${rel}`);
}
for (const rel of expMap.keys()) if (!curMap.has(rel)) problems.push(`REMOVED:  ${rel}`);
console.error('FROZEN CONTENT DRIFT — official IMAT files changed since the manifest:');
console.error(problems.join('\n'));
console.error('\nIf this change is intentional and reviewed, re-run with --write. Otherwise revert it.');
process.exit(1);
