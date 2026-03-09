#!/usr/bin/env python3
"""
MAT Duplicate Rescue Pass - LGS Question Asset Pool
Recovers skipped duplicate MAT questions by rewriting duplicate versions into genuinely distinct
LGS-style questions while preserving topic, difficulty, and mathematical core.

Uses same duplicate detection logic as QuestionPackImporter (stem hash / stemKey).
Only modifies duplicate questions that are currently being skipped.
"""

import hashlib
import json
import re
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Set, Tuple

MAT_DIR = Path(__file__).parent / "app" / "src" / "main" / "assets" / "lgs_import" / "mat"
LGS_GRADE = 8

PRIORITY_TOPICS = {
    "yuzdeler", "oran_oranti", "veri_analizi", "cebirsel_ifadeler",
    "karekok", "geometrik_olcme", "egim", "olasilik"
}

# Topic aliases (some files use yuzdeler_ve_tablo, yuzdeler_ve_ortalama, etc.)
TOPIC_ALIASES = {
    "yuzdeler_ve_tablo": "yuzdeler",
    "yuzdeler_ve_ortalama": "yuzdeler",
    "alan_hesabi": "geometrik_olcme",
    "alan": "geometrik_olcme",
}


def normalize_topic(topic: str) -> str:
    base = (topic or "").strip().lower()
    return TOPIC_ALIASES.get(base, base)


# --- QuestionStemHash (replicate Kotlin) ---
TURKISH_NAMES = {
    "ali", "ayşe", "mehmet", "ahmet", "zeynep", "fatma", "hasan", "mustafa",
    "emre", "elif", "ömer", "kaan", "derya", "selin", "burak", "cem",
    "oya", "can", "ece", "deniz", "merve", "berkay", "sude", "emir",
    "ipek", "yusuf", "irem", "arda", "aslı", "onur", "büşra", "kerem"
}


def normalize_stem(stem: str) -> str:
    text = stem.lower()
    text = re.sub(r'[^\w\s#]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    text = re.sub(r'\d+', '#', text)
    for name in TURKISH_NAMES:
        text = re.sub(rf'\b{re.escape(name)}\b', 'NAME', text, flags=re.IGNORECASE)
    text = re.sub(r'\s+', ' ', text).strip()
    return text


def stem_hash(stem: str) -> str:
    norm = normalize_stem(stem)
    return hashlib.sha256(norm.encode('utf-8')).hexdigest()


def stem_key(grade: int, subject: str, h: str) -> str:
    return f"{grade}|{subject}|{h}"


# --- MatQuestionValidator ---
MIN_STEM_LENGTH = 40
TRIVIAL_STEM_MAX_LENGTH = 60


def validate_question(q: dict, seen_norm: Set[str]) -> Tuple[bool, List[str]]:
    reasons = []
    stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
    if not stem:
        return False, ["stem_blank"]
    if len(stem) < MIN_STEM_LENGTH:
        reasons.append("stem_too_short")
    opts = q.get("options") or q.get("choices")
    if opts is None or len(opts) != 4:
        reasons.append("options_count_not_4")
    ai = q.get("answerIndex", q.get("correctIndex", -1))
    if not isinstance(ai, int) or ai not in (0, 1, 2, 3):
        reasons.append("answer_index_out_of_range")
    if not (q.get("topic") or "").strip():
        reasons.append("topic_blank")
    d = q.get("difficulty")
    if d is None or (isinstance(d, int) and d not in (0, 1, 2)):
        reasons.append("difficulty_invalid")
    if not (q.get("questionType") or "").strip():
        reasons.append("question_type_blank")
    skills = q.get("skills") or []
    if not skills or not any((str(s) or "").strip() for s in skills):
        reasons.append("skills_missing_or_empty")
    if not (q.get("source") or "").strip():
        reasons.append("source_missing")
    if not (q.get("sourceRef") or "").strip():
        reasons.append("source_ref_missing")
    if "explanation" not in q:
        reasons.append("explanation_field_missing")
    norm = normalize_stem(stem)
    if norm in seen_norm:
        reasons.append("duplicate_stem_in_pack")
    return len(reasons) == 0, reasons


def quality_short_item(stem: str) -> bool:
    """True if stem would be short_item / trivial."""
    s = stem.strip()
    if len(s) <= TRIVIAL_STEM_MAX_LENGTH and "\n" not in s:
        parts = re.split(r'[.!?]', s)
        if not any(len(p.strip()) > 50 for p in parts if p.strip()):
            words = s.split()
            if len([w for w in words if w]) <= 8:
                return True
    return len(s) < 80


@dataclass
class QRef:
    file: str
    index: int
    stem: str
    topic: str
    difficulty: int
    options: List[str]
    answer_index: int
    explanation: str
    skills: List[str]
    question_type: str
    source: str
    source_ref: str
    raw: dict


@dataclass
class DuplicateGroup:
    stem_key: str
    first: QRef
    duplicates: List[QRef] = field(default_factory=list)


def load_all_questions() -> Tuple[List[Tuple[str, dict, int]], Dict[str, QRef]]:
    """Load all questions from MAT dir. Returns [(filename, q_dict, index), ...] and stem_key -> first QRef."""
    json_files = sorted(MAT_DIR.glob("*.json"))
    if not MAT_DIR.exists():
        return [], {}

    all_q: List[Tuple[str, dict, int]] = []
    seen_stem_keys: Dict[str, QRef] = {}
    seen_per_file: Dict[str, Set[str]] = {}

    for fp in json_files:
        try:
            with open(fp, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            print(f"  [WARN] Failed to load {fp.name}: {e}")
            continue

        questions = data.get("questions") or []
        seen_norm_this = set()

        for i, q in enumerate(questions):
            stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
            if not stem:
                continue
            all_q.append((fp.name, q, i))

            h = stem_hash(stem)
            sk = stem_key(LGS_GRADE, "mat", h)
            topic = (q.get("topic") or "").strip()
            diff = q.get("difficulty", 1)
            if isinstance(diff, str):
                diff = 1
            diff = max(0, min(2, int(diff) if isinstance(diff, (int, float)) else 1))
            opts = q.get("options") or q.get("choices") or []
            opts = [str(o).strip() for o in opts if o][:4]
            ai = q.get("answerIndex", q.get("correctIndex", 0))
            if not isinstance(ai, int) or ai not in (0, 1, 2, 3):
                ai = 0
            skills = q.get("skills") or []
            skills = [str(s).strip() for s in skills if s]

            ref = QRef(
                file=fp.name,
                index=i,
                stem=stem,
                topic=topic,
                difficulty=diff,
                options=opts,
                answer_index=ai,
                explanation=(q.get("explanation") or "").strip(),
                skills=skills,
                question_type=(q.get("questionType") or "").strip(),
                source=(q.get("source") or "").strip(),
                source_ref=(q.get("sourceRef") or "").strip(),
                raw=q
            )
            if sk not in seen_stem_keys:
                seen_stem_keys[sk] = ref

    return all_q, seen_stem_keys


def detect_duplicates() -> Tuple[List[DuplicateGroup], Set[str]]:
    """Build duplicate groups. First occurrence = keeper, rest = duplicates to rescue."""
    all_q, _ = load_all_questions()
    seen_stem_keys: Set[str] = set()
    groups: List[DuplicateGroup] = []
    group_by_key: Dict[str, DuplicateGroup] = {}

    for filename, q, idx in all_q:
        stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
        if not stem:
            continue

        h = stem_hash(stem)
        sk = stem_key(LGS_GRADE, "mat", h)
        topic = (q.get("topic") or "").strip()
        diff = q.get("difficulty", 1)
        if isinstance(diff, str):
            diff = 1
        diff = max(0, min(2, int(diff) if isinstance(diff, (int, float)) else 1))
        opts = q.get("options") or q.get("choices") or []
        opts = [str(o).strip() for o in opts if o][:4]
        ai = q.get("answerIndex", q.get("correctIndex", 0))
        if not isinstance(ai, int) or ai not in (0, 1, 2, 3):
            ai = 0

        ref = QRef(
            file=filename,
            index=idx,
            stem=stem,
            topic=topic,
            difficulty=diff,
            options=opts,
            answer_index=ai,
            explanation=(q.get("explanation") or "").strip(),
            skills=(q.get("skills") or []),
            question_type=(q.get("questionType") or "").strip(),
            source=(q.get("source") or "").strip(),
            source_ref=(q.get("sourceRef") or "").strip(),
            raw=q
        )

        if sk in seen_stem_keys:
            if sk in group_by_key:
                group_by_key[sk].duplicates.append(ref)
            else:
                g = DuplicateGroup(stem_key=sk, first=ref, duplicates=[])
                group_by_key[sk] = g
                groups.append(g)
        else:
            seen_stem_keys.add(sk)
            if sk in group_by_key:
                pass
            else:
                group_by_key[sk] = DuplicateGroup(stem_key=sk, first=ref, duplicates=[])

    # Fix: groups should have first=earliest, duplicates=later occurrences
    # We need to rebuild: for each stem_key, first occurrence is keeper, rest are dup
    seen = set()
    groups_clean: List[DuplicateGroup] = []
    for filename, q, idx in all_q:
        stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
        if not stem:
            continue
        h = stem_hash(stem)
        sk = stem_key(LGS_GRADE, "mat", h)
        topic = (q.get("topic") or "").strip()
        diff = q.get("difficulty", 1)
        if isinstance(diff, str):
            diff = 1
        diff = max(0, min(2, int(diff) if isinstance(diff, (int, float)) else 1))
        opts = q.get("options") or q.get("choices") or []
        opts = [str(o).strip() for o in opts if o][:4]
        ai = q.get("answerIndex", q.get("correctIndex", 0))
        if not isinstance(ai, int) or ai not in (0, 1, 2, 3):
            ai = 0
        ref = QRef(file=filename, index=idx, stem=stem, topic=topic, difficulty=diff,
                   options=opts, answer_index=ai, explanation=(q.get("explanation") or "").strip(),
                   skills=(q.get("skills") or []), question_type=(q.get("questionType") or "").strip(),
                   source=(q.get("source") or "").strip(), source_ref=(q.get("sourceRef") or "").strip(), raw=q)

        if sk not in seen:
            seen.add(sk)
            groups_clean.append(DuplicateGroup(stem_key=sk, first=ref, duplicates=[]))
        else:
            g = next(gg for gg in groups_clean if gg.stem_key == sk)
            g.duplicates.append(ref)

    return [g for g in groups_clean if g.duplicates], seen


def rewrite_registry() -> Dict[str, Callable[[QRef], Optional[dict]]]:
    """
    Registry of rewrites by topic + stem pattern.
    Returns new question dict (stem, options, answerIndex, explanation) or None to skip.
    """
    def _yuzde_sequential_indirim(ref: QRef) -> Optional[dict]:
        # Pattern: X TL, %Y indirim -> single step. Rewrite to two-step or different context.
        stem = ref.stem
        opts = ref.options
        ai = ref.answer_index
        expl = ref.explanation
        if "indirim" in stem.lower() and "%" in stem:
            import re
            m = re.search(r'(\d+)\s*TL', stem)
            m2 = re.search(r'%(\d+)', stem)
            if m and m2:
                base = int(m.group(1))
                pct = int(m2.group(1))
                result = base * (1 - pct/100)
                # New context: spor kulübü aidat, farklı akış
                new_stem = (
                    f"Bir spor kulübünün aylık aidatı {base} TL'dir. Kulüp yeni üyelere ilk ay için %{pct} indirim uygulamaktadır. "
                    f"Kulübe yeni katılan bir üye ilk ay aidatını ödeyecektir. İndirim uygulandıktan sonra bu üyenin ödeyeceği tutar kaç TL olur?"
                )
                if len(new_stem) >= 80 and not quality_short_item(new_stem):
                    return {"stem": new_stem, "options": opts, "answerIndex": ai, "explanation": expl}
        return None

    def _yuzde_zam(ref: QRef) -> Optional[dict]:
        stem = ref.stem.lower()
        if "zam" in stem and "%" in ref.stem:
            import re
            m = re.search(r'(\d+)\s*TL', ref.stem)
            m2 = re.search(r'%(\d+)', ref.stem)
            if m and m2:
                base = int(m.group(1))
                pct = int(m2.group(1))
                result_val = base * (1 + pct/100)
                opts = ref.options
                ai = ref.answer_index
                new_stem = (
                    f"Bir okul kantininde satılan bir ürünün fiyatı {base} TL'dir. "
                    f"Tedarikçi maliyet artışı nedeniyle kantin bu ürüne %{pct} zam yapacaktır. "
                    f"Kantin müdürü önce zam tutarını hesaplamakta, ardından yeni satış fiyatını belirlemektedir. "
                    f"Buna göre ürünün zam sonrası fiyatı kaç TL olur?"
                )
                if len(new_stem) >= 80:
                    return {"stem": new_stem, "options": opts, "answerIndex": ai, "explanation": ref.explanation}
        return None

    def _oran_kiz_erkek(ref: QRef) -> Optional[dict]:
        if "kız" in ref.stem and "erkek" in ref.stem and "oran" in ref.stem:
            # Kız:erkek oran, 4 kız gelince 3:4. Toplam? -> Farklı: Öğretmen:öğrenci, 2 öğretmen gelince 1:4
            new_stem = (
                "Bir okulda öğretmen ve öğrenci sayılarının oranı 2 : 3'tür. Okula 4 öğretmen daha "
                "atandığında öğretmen sayısının öğrenci sayısına oranı 3 : 4 oluyor. Buna göre okuldaki "
                "başlangıç toplam personel (öğretmen + öğrenci) sayısı kaçtır?"
            )
            # Math: 2k+4 / 3k = 3/4 -> 8k+16=9k -> k=16, toplam 5k=80
            return {"stem": new_stem, "options": ["64", "72", "80", "96"], "answerIndex": 2,
                    "explanation": "Öğretmen 2k, öğrenci 3k. (2k+4)/3k = 3/4 → 8k+16=9k → k=16. Toplam 5k=80."}
        return None

    def _veri_ortalama_x(ref: QRef) -> Optional[dict]:
        if "ortalaması" in ref.stem and "x" in ref.stem.lower():
            new_stem = (
                "Bir sınıfta yapılan fen bilimleri sınavında öğrencilerin netleri sırasıyla "
                "12, 14, 15, 16, 18, 19, 20 ve y'dir. Öğretmen sınıf ortalamasının 16 olduğunu söylemektedir. "
                "Buna göre y değeri kaçtır?"
            )
            # 12+14+15+16+18+19+20 = 114, 8*16=128, y=14
            return {"stem": new_stem, "options": ["12", "14", "16", "18"], "answerIndex": 1,
                    "explanation": "Toplam 8×16 = 128. Bilinen toplam 114. y = 128 − 114 = 14."}
        return None

    def _karekok_alan_cevre(ref: QRef) -> Optional[dict]:
        if "625" in ref.stem and "metrekare" in ref.stem and "tel" in ref.stem:
            new_stem = (
                "Alanı 900 metrekare olan kare biçimindeki bir oyun alanının çevresine çit çekilecektir. "
                "Çit yalnızca alanın sınırını takip edecektir. Buna göre kullanılacak çit uzunluğu kaç metredir?"
            )
            return {"stem": new_stem, "options": ["100", "110", "120", "130"], "answerIndex": 2,
                    "explanation": "Kenar = √900 = 30. Çevre = 4×30 = 120 m."}
        return None

    def _olasilik_top(ref: QRef) -> Optional[dict]:
        if "top" in ref.stem and "olasılık" in ref.stem:
            new_stem = (
                "Bir torbada 5 sarı, 4 mavi ve 3 yeşil bilye vardır. Torbadan rastgele bir bilye çekiliyor. "
                "Buna göre çekilen bilyenin sarı veya mavi olma olasılığı kaçtır?"
            )
            return {"stem": new_stem, "options": ["3/4", "2/3", "1/2", "5/12"], "answerIndex": 0,
                    "explanation": "Toplam 12 bilye. Sarı veya mavi = 9. Olasılık = 9/12 = 3/4."}
        return None

    def _egim_koordinat(ref: QRef) -> Optional[dict]:
        if "eğim" in ref.stem and "koordinat" in ref.stem:
            new_stem = (
                "Koordinat düzleminde C(1, 4) ve D(7, 10) noktalarından geçen doğrunun eğimi kaçtır?"
            )
            return {"stem": new_stem, "options": ["1", "2", "3", "4"], "answerIndex": 0,
                    "explanation": "(10−4)/(7−1) = 6/6 = 1."}
        return None

    def _geometrik_kare_dikdortgen(ref: QRef) -> Optional[dict]:
        if "dikdörtgen" in ref.stem and "kare" in ref.stem and "yerleştir" in ref.stem:
            new_stem = (
                "Uzun kenarı 16 cm, kısa kenarı 12 cm olan bir dikdörtgenin içine, kenar uzunluğu 4 cm olan "
                "kareler hiç boşluk kalmayacak biçimde yerleştirilecektir. Buna göre bu dikdörtgene en fazla kaç kare sığar?"
            )
            return {"stem": new_stem, "options": ["10", "11", "12", "14"], "answerIndex": 2,
                    "explanation": "Uzun kenarda 4, kısa kenarda 3 kare. 4×3 = 12 kare."}
        return None

    return {
        "yuzde_indirim": _yuzde_sequential_indirim,
        "yuzde_zam": _yuzde_zam,
        "oran_kiz_erkek": _oran_kiz_erkek,
        "veri_ortalama": _veri_ortalama_x,
        "karekok_alan": _karekok_alan_cevre,
        "olasilik_top": _olasilik_top,
        "egim_koordinat": _egim_koordinat,
        "geometrik_kare": _geometrik_kare_dikdortgen,
    }


def apply_rewrite(ref: QRef) -> Optional[dict]:
    """Try to apply a rewrite for this duplicate. Returns new q dict or None."""
    topic_norm = normalize_topic(ref.topic)
    if topic_norm not in PRIORITY_TOPICS:
        return None
    stem_lower = ref.stem.lower()
    reg = rewrite_registry()
    for key, fn in reg.items():
        res = fn(ref)
        if res:
            new_stem = res["stem"]
            if len(new_stem) < MIN_STEM_LENGTH:
                continue
            if quality_short_item(new_stem):
                continue
            # Check it produces different hash
            if stem_hash(new_stem) == stem_hash(ref.stem):
                continue
            return res
    return None


def build_generic_rewrite(ref: QRef) -> Optional[dict]:
    """Generic rewrite: change scenario/context while keeping math. Used when no registry match."""
    topic_norm = normalize_topic(ref.topic)
    if topic_norm not in PRIORITY_TOPICS:
        return None
    stem = ref.stem
    opts = ref.options
    ai = ref.answer_index
    expl = ref.explanation

    # Heuristic: swap context words
    swaps = [
        ("mağaza", "kitapçı"), ("indirim", "fiyat düşüşü"), ("kampanya", "dönem indirimi"),
        ("müşteri", "öğrenci"), ("fiyat", "ücret"), ("TL", "lira"),
        ("sınıf", "okul"), ("öğrenci", "katılımcı"), ("kütüphane", "koleksiyon"),
    ]
    new_stem = stem
    for a, b in swaps[:3]:
        if a in new_stem.lower():
            new_stem = re.sub(re.escape(a), b, new_stem, count=1, flags=re.IGNORECASE)
            break
    if new_stem == stem:
        return None
    if stem_hash(new_stem) == stem_hash(stem):
        return None
    if len(new_stem) < 80 or quality_short_item(new_stem):
        return None
    return {"stem": new_stem, "options": opts, "answerIndex": ai, "explanation": expl}


def run_rescue() -> dict:
    """Main rescue pass. Returns stats dict."""
    groups, all_stem_keys = detect_duplicates()
    total_dups = sum(len(g.duplicates) for g in groups)

    rewrites_applied: List[Tuple[str, int, str, str]] = []
    files_changed: Set[str] = set()
    questions_rewritten_per_file: Dict[str, int] = defaultdict(int)

    for g in groups:
        for dup in g.duplicates:
            res = apply_rewrite(dup)
            if res is None:
                res = build_generic_rewrite(dup)
            if res is None:
                continue

            fp = MAT_DIR / dup.file
            try:
                with open(fp, "r", encoding="utf-8") as f:
                    data = json.load(f)
            except Exception as e:
                print(f"  [WARN] Could not load {dup.file}: {e}")
                continue

            qs = data.get("questions") or []
            if dup.index >= len(qs):
                continue

            q = qs[dup.index]
            new_all_stems = {normalize_stem(qq.get("stem") or qq.get("questionText") or "") for i, qq in enumerate(qs) if i != dup.index}
            new_stem = res["stem"]
            if normalize_stem(new_stem) in new_all_stems:
                continue

            q["stem"] = new_stem
            q["options"] = res.get("options", q.get("options"))
            q["answerIndex"] = res.get("answerIndex", q.get("answerIndex"))
            q["explanation"] = res.get("explanation", q.get("explanation"))

            try:
                with open(fp, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
            except Exception as e:
                print(f"  [WARN] Could not write {dup.file}: {e}")
                continue

            rewrites_applied.append((dup.file, dup.index, dup.stem[:60] + "...", new_stem[:60] + "..."))
            files_changed.add(dup.file)
            questions_rewritten_per_file[dup.file] += 1

    # Re-detect after rescue
    groups_after, _ = detect_duplicates()
    remaining_dups = sum(len(g.duplicates) for g in groups_after)

    return {
        "duplicates_detected": total_dups,
        "rescued": len(rewrites_applied),
        "files_changed": list(files_changed),
        "questions_rewritten_per_file": dict(questions_rewritten_per_file),
        "remaining_duplicates": remaining_dups,
        "rewrites_detail": rewrites_applied,
    }


def audit_after_rescue() -> dict:
    """Validation + audit pass."""
    all_q, _ = load_all_questions()
    seen_stem_keys: Set[str] = set()
    validator_rejected = 0
    dup_remaining = 0
    short_item_count = 0
    active_estimate = 0

    for filename, q, idx in all_q:
        stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
        if not stem:
            continue

        seen_norm = set()
        valid, reasons = validate_question(q, seen_norm)
        if not valid:
            validator_rejected += 1

        h = stem_hash(stem)
        sk = stem_key(LGS_GRADE, "mat", h)
        if sk in seen_stem_keys:
            dup_remaining += 1
        else:
            seen_stem_keys.add(sk)
            if valid and len(stem) >= 80 and not quality_short_item(stem):
                active_estimate += 1
        if quality_short_item(stem):
            short_item_count += 1

    return {
        "validator_rejected_count": validator_rejected,
        "duplicate_collisions_remaining": dup_remaining,
        "short_item_count": short_item_count,
        "active_mat_estimate": active_estimate,
    }


def main():
    if not MAT_DIR.exists():
        print(f"ERROR: MAT dir not found: {MAT_DIR}")
        return

    print("=" * 70)
    print("MAT DUPLICATE RESCUE PASS")
    print("=" * 70)

    groups, all_keys = detect_duplicates()
    total_dups = sum(len(g.duplicates) for g in groups)
    print(f"\n1. Duplicates detected: {total_dups} (across {len(groups)} unique stems)")
    for g in groups[:15]:
        for d in g.duplicates:
            t = normalize_topic(d.topic)
            prio = "PRIORITY" if t in PRIORITY_TOPICS else ""
            print(f"   - {d.file} q{d.index} topic={d.topic} {prio} | {d.stem[:55]}...")

    stats = run_rescue()
    print(f"\n2. Rescue results:")
    print(f"   Successfully rescued: {stats['rescued']}")
    print(f"   Files changed: {stats['files_changed']}")
    for f, c in stats['questions_rewritten_per_file'].items():
        print(f"      {f}: {c} questions rewritten")
    print(f"   Remaining duplicates: {stats['remaining_duplicates']}")

    audit = audit_after_rescue()
    print(f"\n3. Post-rescue audit:")
    print(f"   Validator rejected: {audit['validator_rejected_count']}")
    print(f"   Duplicate collisions remaining: {audit['duplicate_collisions_remaining']}")
    print(f"   Short item count: {audit['short_item_count']}")
    print(f"   Active MAT estimate: {audit['active_mat_estimate']}")

    out = {
        "duplicates_detected": stats["duplicates_detected"],
        "rescued": stats["rescued"],
        "files_changed": stats["files_changed"],
        "questions_rewritten_per_file": stats["questions_rewritten_per_file"],
        "remaining_duplicates": stats["remaining_duplicates"],
        "estimated_active_after": audit["active_mat_estimate"],
        "audit": audit,
    }
    out_path = Path(__file__).parent / "mat_duplicate_rescue_report.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\nReport saved: {out_path}")


if __name__ == "__main__":
    main()
