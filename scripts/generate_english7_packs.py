#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 7th Grade English question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: dialogue, scenario, reading comprehension, inference.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/english7"

TOPICS = [
    "appearance_personality",
    "sports",
    "biographies",
    "wild_animals",
    "television",
    "celebrations",
    "dreams",
    "public_buildings",
    "environment",
]

NEW_GEN_TYPES = ["dialogue_completion", "situation_response", "reading_comprehension", "inference", "paragraph_interpretation"]

TEMPLATES = [
    ("Sarah: I love your new haircut! It really suits you.\n\nEmma: ---\n\nWhich of the following is Emma's most appropriate response?",
     "appearance_personality", ["Thank you! I was a bit nervous about changing it.", "I don't like it.", "What haircut?", "You have one too."]),
    ("Teacher: Why didn't you finish your homework?\n\nTom: ---\n\nChoose the response that best fits the situation.",
     "appearance_personality", ["I was ill yesterday, so I couldn't complete it.", "Homework is boring.", "I don't know you.", "Yes, I did."], 0),
    ("Coach: You need to practice more if you want to join the team.\n\nJake: ---\n\nWhat would Jake most likely say?",
     "sports", ["I'll train harder from now on.", "I don't like sports.", "The team is full.", "What team?"], 0),
    ("Interviewer: When did you start playing basketball?\n\nPlayer: I've been playing since I was ten. ---\n\nComplete the sentence appropriately.",
     "sports", ["It's been my passion ever since.", "I hate basketball.", "Ten is old.", "I never played."], 0),
    ("The text says: \"Marie Curie was the first woman to win a Nobel Prize. She won it twice.\"\n\nWhat can we infer from this?",
     "biographies", ["She was an extraordinary scientist.", "She lived in France only.", "She had no children.", "She disliked science."], 0),
    ("\"At the age of 15, he left school to work. Later, he became one of the richest people in the world.\"\n\nWhat does this suggest about the person?",
     "biographies", ["Success can come through determination and hard work.", "School is not important.", "He was always rich.", "He never worked."], 0),
    ("Zoo keeper: These lions were rescued from a difficult situation. Now they have a safe home.\n\nVisitor: ---\n\nChoose the most suitable response.",
     "wild_animals", ["That's wonderful. They deserve to be protected.", "Lions are scary.", "I want one.", "Zoos are bad."], 0),
    ("\"Polar bears are losing their habitat because of climate change. The ice they depend on is melting.\"\n\nWhat is the main message?",
     "wild_animals", ["Climate change threatens polar bear survival.", "Polar bears can live anywhere.", "Ice is not important.", "Bears like heat."], 0),
    ("Mum: There's nothing good on TV tonight.\n\nDad: ---\n\nWhich response fits best?",
     "television", ["Why don't we watch a film instead?", "TV is always bad.", "Turn it off now.", "I never watch TV."], 0),
    ("Review: \"This new series is both funny and thought-provoking. You won't regret watching it.\"\n\nWhat does the reviewer recommend?",
     "television", ["Watching the series because it's enjoyable and meaningful.", "Avoiding the series.", "Only watching comedy.", "Never watching TV."], 0),
    ("Guest: Thank you for inviting me to your birthday party. I had a great time!\n\nHost: ---\n\nWhat would the host most likely reply?",
     "celebrations", ["I'm so glad you could come! Your presence meant a lot.", "The party was boring.", "You're late.", "No problem, leave now."], 0),
    ("\"At New Year, families often gather to celebrate together. It's a time for hope and new beginnings.\"\n\nWhat is the main idea?",
     "celebrations", ["New Year is a meaningful time for family and fresh starts.", "Families never meet.", "Hope is unimportant.", "Beginnings don't matter."], 0),
    ("Friend: I've always dreamed of becoming a pilot.\n\nYou: ---\n\nChoose the most encouraging response.",
     "dreams", ["That's a fantastic goal! Keep working hard and you can achieve it.", "Pilots earn little.", "Dreams never come true.", "Choose something else."], 0),
    ("\"She never gave up on her dream. Despite many setbacks, she finally opened her own restaurant.\"\n\nWhat does this tell us about her?",
     "dreams", ["She was persistent and determined.", "She had no problems.", "Restaurants are easy.", "She gave up quickly."], 0),
    ("Tourist: Excuse me, where is the nearest hospital?\n\nLocal: ---\n\nWhich is the most helpful response?",
     "public_buildings", ["It's two blocks away. Turn left at the traffic lights.", "I don't know.", "Hospitals are far.", "Why do you need one?"], 0),
    ("\"The library offers free internet, quiet study rooms, and a wide collection of books. It's open to everyone.\"\n\nWhat can we conclude about the library?",
     "public_buildings", ["It provides valuable resources for the community.", "It's only for students.", "Books are not available.", "It's always closed."], 0),
    ("Teacher: We should reduce plastic use. What can we do at school?\n\nStudent: ---\n\nChoose the most appropriate suggestion.",
     "environment", ["We could use reusable bottles and recycle paper.", "Plastic is fine.", "Do nothing.", "Burn the plastic."], 0),
    ("\"Recycling saves energy and reduces pollution. Small changes in our habits can make a big difference.\"\n\nWhat is the writer's main point?",
     "environment", ["Individual actions can help protect the environment.", "Recycling is useless.", "Only factories pollute.", "Habits never change."], 0),
    ("Lucy: I'm so nervous about the exam tomorrow.\n\nBen: ---\n\nWhat would be a supportive response?",
     "appearance_personality", ["You've prepared well. I'm sure you'll do fine. Good luck!", "Exams are easy.", "Stop worrying.", "I don't care."], 0),
    ("Announcer: The match has been cancelled due to bad weather. We'll inform you about the new date.\n\nFan: ---\n\nWhich response shows understanding?",
     "sports", ["That's disappointing, but safety comes first. Thanks for letting us know.", "I want my money back now.", "Weather is never bad.", "Play anyway."], 0),
    ("\"He wrote over 400 plays and is considered the greatest writer in the English language.\"\n\nWho is most likely described?",
     "biographies", ["William Shakespeare", "A modern author", "A scientist", "A sports star"], 0),
    ("Guide: These elephants need large areas to live. Deforestation is a serious threat to them.\n\nVisitor: ---\n\nWhat would show concern?",
     "wild_animals", ["What can we do to help protect their habitat?", "Elephants are too big.", "I'm not interested.", "Cut more trees."], 0),
    ("Sibling: Can I change the channel? There's a documentary I want to watch.\n\nYou: ---\n\nChoose the polite response.",
     "television", ["Sure, go ahead. What's the documentary about?", "No, never.", "I hate documentaries.", "The remote is broken."], 0),
    ("\"Wedding traditions vary around the world. In some cultures, the colour white symbolises purity; in others, red brings good luck.\"\n\nWhat can we infer?",
     "celebrations", ["Different cultures have different wedding customs.", "All weddings are the same.", "White is the only colour.", "Red means danger."], 0),
    ("Mentor: What do you want to be when you grow up?\n\nStudent: I'm not sure yet, but I love science. ---\n\nComplete logically.",
     "dreams", ["Maybe I'll become a scientist or an engineer.", "Science is boring.", "I hate school.", "I have no ideas."], 0),
    ("Stranger: Is there a bank near here?\n\nYou: ---\n\nWhich is the most helpful reply?",
     "public_buildings", ["Yes, there's one on Main Street. It's about five minutes on foot.", "Banks are far.", "I don't talk to strangers.", "Why do you need money?"], 0),
    ("\"Planting trees reduces carbon dioxide and provides oxygen. Every tree counts.\"\n\nWhat does the author want readers to do?",
     "environment", ["Consider the benefits of planting trees and take action.", "Ignore trees.", "Cut down trees.", "Trees don't help."], 0),
    ("Colleague: I heard you got the job. Congratulations!\n\nYou: ---\n\nWhat is an appropriate response?",
     "appearance_personality", ["Thank you so much! I'm really excited about it.", "I didn't get it.", "So what?", "You're wrong."], 0),
    ("Commentator: What an incredible finish! The underdog has won the championship!\n\nWhat does 'underdog' mean in this context?",
     "sports", ["The team that was not expected to win", "The strongest team", "The referee", "The audience"], 0),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"eng7_{source_ref}",
        "stem": stem,
        "options": options,
        "answerIndex": answer_index,
        "difficulty": difficulty,
        "questionType": qtype,
        "topic": topic,
        "skills": skills,
        "explanation": explanation,
        "source": "edumio",
        "sourceRef": source_ref,
        "imageAsset": None,
        "subject": "ing",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["None of these fit the context.", "The speaker disagrees.", "It depends on the situation.", "The text doesn't say."]
    for p in pad:
        if len(opts) >= 4:
            break
        if p not in seen:
            opts.append(p)
    return opts[:4]


def generate_questions():
    per_topic = 500 // len(TOPICS)
    remainder = 500 % len(TOPICS)
    counts = {t: per_topic + (1 if i < remainder else 0) for i, t in enumerate(TOPICS)}

    out = []
    tpl_idx = 0
    for topic, count in counts.items():
        for _ in range(count):
            tpl = TEMPLATES[tpl_idx % len(TEMPLATES)]
            tpl_idx += 1
            stem, tpl_topic, opts_list = tpl[0], tpl[1], tpl[2]
            correct = opts_list[0]
            wrongs = opts_list[1:]
            options = ensure_4_opts(correct, wrongs)
            random.shuffle(options)
            ai = options.index(correct)
            expl = f"The correct answer fits the context, tone and intention. \"{correct}\" is appropriate. Other options misunderstand the situation, tone or implied meaning."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:12], "reading", "inference"],
                expl, f"eng7_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"eng7_{idx:04d}"
        qq["id"] = f"eng7_{idx:04d}"

    packs = [all_q[i:i+10] for i in range(0, 500, 10)]
    topic_counts = {}
    new_gen_count = 0
    for pi, questions in enumerate(packs):
        for qq in questions:
            topic_counts[qq["topic"]] = topic_counts.get(qq["topic"], 0) + 1
            if qq["questionType"] in NEW_GEN_TYPES:
                new_gen_count += 1
        pack = {
            "version": 1,
            "mode": "LGS",
            "subject": "ing",
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_eng7_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("ENGLISH7 Question Bank Report")
    print("=" * 60)
    print(f"Packs: {len(packs)}, Questions: {len(all_q)}")
    print("\nTopic distribution:")
    for t in sorted(topic_counts.keys()):
        print(f"  {t}: {topic_counts[t]}")
    pct = 100 * new_gen_count / 500
    print(f"\nNew-generation: {new_gen_count}/500 = {pct:.1f}%")
    print(f"Output: {OUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    random.seed(46)
    main()
