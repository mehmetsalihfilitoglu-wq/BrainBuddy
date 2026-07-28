# FEN LGS Quality Improvement Pass — Report

## Files Modified

| File | Changes |
|------|---------|
| `lgs_fen_pack_dna_ve_genetik_kod_001.json` | Typo: "bitkesi" → "bitkisi" (2 occurrences) |
| `lgs_fen_pack_dna_ve_genetik_kod_007.json` | Q3: Rewrote stem (unsolvable data), fixed explanation, corrected answerIndex |
| `lgs_fen_pack_dna_ve_genetik_kod_008.json` | Q2: Corrected answerIndex (28 is correct, was 39) |
| `lgs_fen_pack_madde_ve_endustri_005.json` | Q6: Replaced weak distractor, improved option wording |
| `lgs_fen_pack_basinc_001.json` | Q7: Clarified explanation for açık hava basıncı |
| `lgs_fen_pack_mevsimler_ve_iklim_002.json` | Q5: Rewrote stem and options (near-duplicate), improved explanation |
| `lgs_fen_pack_elektrik_006.json` | Q10: Rewrote to avoid duplicate with elektrik_005 Q10 |

---

## Summary

| Metric | Count |
|--------|-------|
| **Files modified** | 7 |
| **Questions improved** | 7 |
| **answerIndex corrections** | 2 |
| **Explanations rewritten** | 5 |
| **Stems rewritten** | 3 |
| **Weak/duplicate questions rewritten** | 3 |

---

## Fix Details

### answerIndex corrections
1. **dna_008 Q2**: G=%22 → C=%22, A+T=56%, T=28. Correct option index 1 (was 2).
2. **dna_007 Q3**: Stem changed; correct answer 420 (index 1, was 0).

### Typo
- **dna_001**: "bezelye bitkesi" → "bezelye bitkisi" (2× in stem Q1).

### Scientifically incorrect / ambiguous
- **dna_007 Q3**: Original stem ("bir zincirinde 210 timin") did not allow a unique total A; rewritten with solvable data (210 adenin in each strand → total 420).
- **basinc_001 Q7**: Explanation clarified: atmospheric pressure from below balances water weight.

### Weak distractors
- **madde_005 Q6**: Removed "Son katman elektron sayısı azalır" (poor grammar, mismatched question). Replaced with "Metalik özellik" and "Atom yarıçapı" (both decrease left-to-right, so plausible distractors).

### Duplicates / near-duplicates
- **mevsimler_002 Q5**: Rewrote eğik ışın deneyi to focus on "birim alana düşen enerji" instead of generic "daha geniş alan."
- **elektrik_006 Q10**: Replaced duplicate "Elektrik akımı hangisinin hareketiyle oluşur?" with context-based stem (ampul yanıyor → electron flow).

---

## Quality Checks

- All modified files: valid JSON
- Schema preserved: stem, options, answerIndex, difficulty, questionType, topic, skills, explanation, source, sourceRef
- Pack counts unchanged
- No new questions added
- Scientific correctness verified for all edits
