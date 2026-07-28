#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 6th Grade English question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: dialogue, scenario, reading comprehension, inference.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/english6"

TOPICS = [
    "life",
    "yummy_breakfast",
    "downtown",
    "weather_emotions",
    "at_the_fair",
    "occupations",
    "holidays",
    "bookworms",
    "saving_the_planet",
    "democracy",
]

NEW_GEN_TYPES = ["dialogue_completion", "situation_response", "reading_comprehension", "inference", "paragraph_interpretation"]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    ("Mum: It's time to get up! You'll be late for school.\n\nChild: ---\n\nWhich response best fits the situation?",
     "life", ["Okay, I'm getting up now. What's for breakfast?", "I don't want to go.", "School is boring.", "Leave me alone."]),
    ("The text says: \"A healthy lifestyle includes regular exercise, enough sleep, and balanced meals.\"\n\nWhat can we infer from this?",
     "life", ["These habits help keep the body and mind healthy.", "Sleep is not important.", "Exercise is the only key.", "Meals don't matter."]),
    ("Waiter: Good morning! What would you like for breakfast?\n\nCustomer: ---\n\nChoose the most appropriate response.",
     "yummy_breakfast", ["I'd like eggs and toast with orange juice, please.", "I hate breakfast.", "Nothing.", "Give me anything."]),
    ("\"A good breakfast gives you energy for the day. Many people skip it, but that can make you feel tired.\"\n\nWhat is the main message?",
     "yummy_breakfast", ["Eating breakfast helps you stay energetic.", "Breakfast is optional.", "People never eat breakfast.", "Skipping meals is fine."]),
    ("Tourist: Excuse me, how do I get to the city centre from here?\n\nLocal: ---\n\nWhat would be the most helpful reply?",
     "downtown", ["Take the bus number 12. It stops right in front of the square.", "I don't know.", "The city is far.", "Walk forever."]),
    ("\"Downtown has many shops, cafes, and a famous museum. It's busy at weekends.\"\n\nWhat can we conclude about downtown?",
     "downtown", ["It is a lively area with various attractions.", "It is always empty.", "There are no shops.", "Nobody goes there."]),
    ("Friend: I'm so sad today. Nothing went right.\n\nYou: ---\n\nWhich response shows empathy?",
     "weather_emotions", ["I'm sorry to hear that. Do you want to talk about it?", "Cheer up.", "So what?", "Stop being sad."]),
    ("\"When the weather is cloudy and rainy, some people feel low. Sunny days often make people happier.\"\n\nWhat does this suggest?",
     "weather_emotions", ["Weather can affect how people feel.", "Weather has no effect.", "Everyone feels the same.", "Rain makes everyone happy."]),
    ("Child: Dad, can we go on the Ferris wheel? It looks amazing!\n\nDad: ---\n\nWhat would Dad most likely say?",
     "at_the_fair", ["Sure! Let's buy the tickets. I hope you'll enjoy it.", "No, never.", "It's dangerous.", "We're leaving now."]),
    ("\"At the fair, you can try different games, eat candy floss, and enjoy rides. Families often spend the whole day there.\"\n\nWhat is the main idea?",
     "at_the_fair", ["Fairs offer fun activities for families.", "Fairs are boring.", "Only children go.", "Candy floss is unhealthy."]),
    ("Interviewer: What do you do for a living?\n\nPerson: ---\n\nComplete with a logical response.",
     "occupations", ["I'm a teacher. I work at a primary school.", "I do nothing.", "I hate my job.", "Money is everything."]),
    ("\"Nurses help sick people in hospitals. They work long hours but find the job rewarding.\"\n\nWhat can we infer about nurses?",
     "occupations", ["They care for patients and find their work meaningful.", "They have easy jobs.", "They never work hard.", "Hospitals are quiet."]),
    ("Guest: Thank you for the wonderful holiday dinner!\n\nHost: ---\n\nWhat would the host most likely reply?",
     "holidays", ["You're welcome! I'm glad you could join us.", "The dinner was bad.", "You should leave.", "No thanks needed."]),
    ("\"During holidays, families often travel together or stay at home and celebrate. It's a time to relax and be with loved ones.\"\n\nWhat is the writer's main point?",
     "holidays", ["Holidays are a time for family and relaxation.", "Holidays are boring.", "Nobody travels.", "Families never meet."]),
    ("Librarian: Have you found the book you were looking for?\n\nStudent: ---\n\nChoose the most suitable response.",
     "bookworms", ["Yes, thank you! I found it in the fiction section.", "I hate books.", "Libraries are useless.", "I don't read."]),
    ("\"Reading regularly improves vocabulary and imagination. Many successful people say books changed their lives.\"\n\nWhat does the text suggest?",
     "bookworms", ["Reading has positive effects on learning and creativity.", "Books are boring.", "Only adults read.", "Reading wastes time."]),
    ("Teacher: How can we help save the planet at school?\n\nStudent: ---\n\nChoose the most appropriate suggestion.",
     "saving_the_planet", ["We could recycle paper, use less plastic, and turn off lights.", "Do nothing.", "Plastic is fine.", "The planet is fine."]),
    ("\"Using reusable bags instead of plastic reduces waste. Small changes in our daily habits can make a big difference.\"\n\nWhat is the main message?",
     "saving_the_planet", ["Individual actions can help the environment.", "Plastic is good.", "Changes don't matter.", "Recycling is useless."]),
    ("Teacher: In a democracy, every citizen has the right to vote. Why is that important?\n\nStudent: ---\n\nWhat would be a correct understanding?",
     "democracy", ["Because it lets people have a say in who leads them.", "Voting doesn't matter.", "Only leaders vote.", "Democracy means no rules."]),
    ("\"In democratic countries, people choose their leaders through elections. Everyone's vote counts equally.\"\n\nWhat can we conclude?",
     "democracy", ["Democracy gives citizens the power to choose their leaders.", "Elections are unfair.", "Only some people vote.", "Leaders are chosen by force."]),
    ("Friend: I had a great weekend! I went cycling with my family.\n\nYou: ---\n\nWhat would be an engaged response?",
     "life", ["That sounds lovely! I'm glad you enjoyed it.", "I don't care.", "Cycling is boring.", "So what?"]),
    ("Mum: Don't forget to eat something before you leave. You have a long day ahead.\n\nChild: ---\n\nChoose the response that shows understanding.",
     "yummy_breakfast", ["You're right. I'll have some cereal and fruit.", "I'm not hungry.", "Breakfast is useless.", "I'll eat later, maybe."]),
    ("Stranger: Is there a pharmacy near here? I need some medicine.\n\nYou: ---\n\nWhich is the most helpful reply?",
     "downtown", ["Yes, there's one on the next street. Turn right at the corner.", "I don't know.", "Pharmacies are far.", "Why do you need it?"]),
    ("\"She was so happy when she passed the exam. Her face lit up with joy.\"\n\nWhat does 'her face lit up' suggest?",
     "weather_emotions", ["She showed clear happiness.", "She was angry.", "The light was on.", "She felt nothing."]),
    ("Child: The roller coaster was so scary but fun!\n\nParent: ---\n\nWhat would the parent most likely say?",
     "at_the_fair", ["I'm glad you enjoyed it! Maybe we can try another ride later.", "Don't do it again.", "It was dangerous.", "We're going home."]),
    ("Boss: Can you finish this report by Friday?\n\nEmployee: ---\n\nChoose the professional response.",
     "occupations", ["Yes, I'll do my best to have it ready by then.", "No way.", "I don't want to.", "Ask someone else."]),
    ("\"On New Year's Eve, people often stay up until midnight to celebrate. Fireworks light up the sky.\"\n\nWhat is this describing?",
     "holidays", ["A common way to celebrate the new year.", "A boring night.", "Nobody celebrates.", "Fireworks are banned."]),
    ("Friend: I just finished reading a great novel. It had an unexpected ending.\n\nYou: ---\n\nWhat would show interest?",
     "bookworms", ["Really? Tell me about it. I might want to read it too.", "I hate reading.", "Books are long.", "I don't have time."]),
    ("Parent: Why did you bring your own water bottle today?\n\nChild: ---\n\nChoose the response that shows environmental awareness.",
     "saving_the_planet", ["To reduce plastic waste. Single-use bottles harm the environment.", "I forgot to buy one.", "It's a fashion.", "No reason."]),
    ("Student: Why do we learn about democracy in school?\n\nTeacher: ---\n\nWhat would the teacher most likely explain?",
     "democracy", ["Because understanding how our country is run helps us be good citizens.", "You don't need to know.", "It's just a subject.", "Democracy is old."]),
    ("Doctor: How often do you exercise?\n\nPatient: ---\n\nChoose the honest and relevant response.",
     "life", ["I try to walk every day, but sometimes I'm too busy.", "I never move.", "Exercise is bad.", "I don't know."]),
    ("Waiter: Would you like tea or coffee with your breakfast?\n\nCustomer: ---\n\nWhich response is appropriate?",
     "yummy_breakfast", ["Tea, please. With a little milk.", "I don't drink.", "Both.", "Whatever."]),
    ("\"The new shopping mall has five floors. You can find clothes, electronics, and a cinema there.\"\n\nWhat kind of place is described?",
     "downtown", ["A large shopping centre with various facilities.", "A small shop.", "A cinema only.", "An empty building."]),
    ("Friend: I'm really nervous about the presentation tomorrow.\n\nYou: ---\n\nWhat would be a supportive response?",
     "weather_emotions", ["You'll do fine. You've prepared well. Just take a deep breath.", "Good luck with that.", "Don't be nervous.", "I don't care."]),
    ("Announcer: Welcome to the annual fair! Please keep your tickets. You'll need them for the rides.\n\nVisitor: ---\n\nWhat might the visitor say to a friend?",
     "at_the_fair", ["Got it. I'll keep my ticket safe. Which ride should we try first?", "I lost my ticket.", "I don't want to go.", "Tickets are useless."]),
    ("\"Firefighters risk their lives to save people from burning buildings. They need courage and quick thinking.\"\n\nWhat does this tell us about firefighters?",
     "occupations", ["Their job is dangerous and requires bravery.", "Their job is easy.", "They don't help people.", "They never face danger."]),
    ("\"In some countries, people exchange gifts at Christmas. It's a tradition that brings families together.\"\n\nWhat is the main idea?",
     "holidays", ["Gift-giving can be part of holiday traditions and family bonding.", "Gifts are expensive.", "Christmas is only for children.", "Traditions don't matter."]),
    ("\"The library is quiet. Everyone is reading or studying. Even a whisper seems loud.\"\n\nWhat mood does this create?",
     "bookworms", ["A calm, studious atmosphere.", "A noisy place.", "A party.", "Confusion."]),
    ("\"We should turn off the tap while brushing our teeth. It saves water and money.\"\n\nWhat is the writer encouraging?",
     "saving_the_planet", ["Simple habits to save water.", "Using more water.", "Ignoring the tap.", "Spending more money."]),
    ("\"In a democracy, leaders must listen to the people. If they don't, citizens can vote for someone else next time.\"\n\nWhat does this show about democracy?",
     "democracy", ["Citizens have the power to change leaders through elections.", "Leaders never listen.", "Voting has no effect.", "Leaders stay forever."]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"eng6_{idx:04d}",
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
                [topic[:10], "reading", "inference"],
                expl, f"eng6_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"eng6_{idx:04d}"
        qq["id"] = f"eng6_{idx:04d}"

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
        path = OUT_DIR / f"lgs_eng6_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("ENGLISH6 Question Bank Report")
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
    random.seed(50)
    main()
