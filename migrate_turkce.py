#!/usr/bin/env python3
"""
Migrate legacy Turkish LGS questions to active importer schema.
Source: app/src/main/assets/lgs_turkce.json
Target: app/src/main/assets/lgs_import/lgs_turkce.json
"""
import json
import re
import os

# Paths
BASE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(BASE, "app", "src", "main", "assets", "lgs_turkce.json")
TARGET = os.path.join(BASE, "app", "src", "main", "assets", "lgs_import", "lgs_turkce.json")

# Answer mapping
ANSWER_MAP = {"A": 0, "B": 1, "C": 2, "D": 3}

# Difficulty mapping
def map_difficulty(d):
    if d == "medium":
        return 1
    if d == "hard":
        return 2
    if d == "easy":
        return 1
    return 1  # default safe value

# Skill -> topic mapping (Turkish)
SKILL_TOPIC = {
    "ana_fikir": "Ana Düşünce",
    "çıkarım": "Çıkarım",
    "cikarim": "Çıkarım",
    "yorum": "Yorumlama",
    "paragraf": "Paragraf",
    "anlam": "Anlam",
    "dil_bilgisi": "Dil Bilgisi",
    "cümlede_anlam": "Cümlede Anlam",
    "cumlede_anlam": "Cümlede Anlam",
    "mantik": "Mantık",
    "grafik_yorum": "Grafik Yorumlama",
    "sıralama": "Paragraf Sıralama",
    "eşleştirme": "Mantık",
    "yazarin_amaci": "Yazarın Amacı",
    "yardimci_dusunce": "Yardımcı Düşünce",
    "karsilastirma": "Metin Karşılaştırma",
    "paragraf_tamamlama": "Paragraf Tamamlama",
    "paragraf_siralama": "Paragraf Sıralama",
    "sozcukte_anlam": "Sözcükte Anlam",
    "metin_karsilastirma": "Metin Karşılaştırma",
}

def skill_to_topic(skill, qtype, pack_title):
    if skill and skill in SKILL_TOPIC:
        return SKILL_TOPIC[skill]
    if qtype == "paragraf":
        return "Paragraf"
    return "Genel"

def skill_to_list(skill, qtype):
    skills = []
    if skill:
        s = skill.replace(" ", "_").lower()
        if s == "ana_fikir":
            skills = ["ana_dusunce", "cikarim"]
        elif s == "çıkarım" or s == "cikarim":
            skills = ["cikarim", "yorumlama"]
        elif s == "yorum":
            skills = ["yorumlama", "cikarim"]
        else:
            skills = [s]
    if not skills:
        skills = ["cikarim", "yorumlama"]
    return skills

def normalize_stem(stem):
    """Normalize stem for deduplication (trim, collapse whitespace)."""
    if not stem:
        return ""
    s = re.sub(r'\s+', ' ', stem.strip())
    return s

def stem_similar(a, b):
    """Check if stems are duplicates: exact or high overlap."""
    na = normalize_stem(a)
    nb = normalize_stem(b)
    if na == nb:
        return True
    # One contains the other (common with long paragraph stems)
    if len(na) > 50 and len(nb) > 50:
        if na in nb or nb in na:
            return True
    return False

def find_duplicate(stem, existing_stems):
    for ex in existing_stems:
        if stem_similar(stem, ex):
            return True
    return False

def generate_explanation(legacy, converted):
    """Generate a short, accurate explanation based on stem and correct option."""
    stem = legacy.get("question", "")
    options = legacy.get("options", [])
    ans_idx = converted.get("answerIndex", 0)
    if 0 <= ans_idx < len(options):
        correct = options[ans_idx]
        return f"Doğru cevap: {correct[:100]}{'...' if len(correct) > 100 else ''}"
    return "Parçadaki ana fikir/çıkarım doğru seçenekle örtüşmektedir."

def convert_question(legacy, source_ref):
    q = legacy.get("question", "").strip()
    opts = legacy.get("options", [])
    if len(opts) != 4:
        return None
    ans = legacy.get("answer", "A").upper()
    ans_idx = ANSWER_MAP.get(ans, 0)
    diff = map_difficulty(legacy.get("difficulty", "medium"))
    skill = legacy.get("skill", "")
    qtype = legacy.get("type", "paragraf")
    topic = skill_to_topic(skill, qtype, "")
    skills = skill_to_list(skill, qtype)
    explanation = generate_explanation(legacy, {"answerIndex": ans_idx})

    return {
        "stem": q,
        "options": opts,
        "answerIndex": ans_idx,
        "difficulty": diff,
        "questionType": "REASONING",
        "topic": topic,
        "skills": skills,
        "explanation": explanation,
        "source": "edumio",
        "sourceRef": source_ref,
    }

def main():
    with open(SOURCE, "r", encoding="utf-8") as f:
        source_data = json.load(f)

    with open(TARGET, "r", encoding="utf-8") as f:
        target_data = json.load(f)

    # Collect all legacy questions
    legacy_questions = []
    for pack in source_data.get("packs", []):
        for q in pack.get("questions", []):
            legacy_questions.append(q)

    # Existing stems and max sourceRef
    existing_questions = target_data.get("questions", [])
    existing_stems = [q.get("stem", "") for q in existing_questions]
    max_ref = 0
    for q in existing_questions:
        ref = q.get("sourceRef", "")
        m = re.search(r"lgs_edumio_tr_(\d+)", ref)
        if m:
            max_ref = max(max_ref, int(m.group(1)))

    next_ref = max_ref + 1
    converted = []
    skipped = 0

    for leg in legacy_questions:
        stem = leg.get("question", "").strip()
        if not stem or len(leg.get("options", [])) != 4:
            skipped += 1
            continue
        if find_duplicate(stem, existing_stems):
            skipped += 1
            continue
        ref_str = f"lgs_edumio_tr_{next_ref:04d}"
        c = convert_question(leg, ref_str)
        if c:
            converted.append(c)
            existing_stems.append(stem)
            next_ref += 1

    # Append to target
    target_data["questions"].extend(converted)

    with open(TARGET, "w", encoding="utf-8") as f:
        json.dump(target_data, f, ensure_ascii=False, indent=2)

    # Report
    print("=== Migration Report ===")
    print(f"Legacy questions found:    {len(legacy_questions)}")
    print(f"Converted and appended:    {len(converted)}")
    print(f"Skipped (duplicates/invalid): {skipped}")
    print(f"Final total in active file: {len(target_data['questions'])}")

if __name__ == "__main__":
    main()
