#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 4th Grade English question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: dialogue completion, situation-response, reading comprehension, inference.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/english4"

# 4th grade curriculum units
TOPICS = [
    "classroom_rules",
    "nationality",
    "cartoon_characters",
    "free_time",
    "my_day",
    "fun_with_science",
    "jobs",
    "my_clothes",
    "my_friends",
    "food_and_drinks",
]

NEW_GEN_TYPES = [
    "dialogue_completion",
    "situation_response",
    "reading_comprehension",
    "meaning_inference",
    "paragraph_interpretation",
    "visual_interpretation",
    "logical_response",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Classroom Rules
    ("Teacher: Please raise your hand before you speak.\n\nStudent: ---\n\nWhat is the best response?",
     "classroom_rules", ["Okay, I will. Thank you for reminding me.", "I don't want to.", "I always shout.", "Hands are boring."]),
    ("\"We must be quiet in the library. We must not run. We must listen to the teacher.\"\n\nWhat can we infer from these rules?",
     "classroom_rules", ["Rules help everyone learn in a calm and safe place.", "Rules are boring.", "Running is always good.", "Teachers never talk."]),
    ("Friend: Can I borrow your eraser?\n\nYou: ---\n\nChoose the most polite reply.",
     "classroom_rules", ["Sure! Here you are. Please give it back after class.", "No way.", "I don't have one.", "Ask someone else."]),
    ("Teacher: What should we do when the bell rings?\n\nStudent: ---\n\nWhat is the correct understanding?",
     "classroom_rules", ["We should stop what we are doing and listen.", "We should run outside.", "We should ignore it.", "The bell means nothing."]),
    # Nationality
    ("Visitor: Where are you from?\n\nYou: ---\n\nWhat would be a clear and polite answer?",
     "nationality", ["I am from Turkey. I am Turkish.", "I don't know.", "From here.", "Nowhere."]),
    ("\"Emma is from England. She is English. She speaks English. Her favourite food is fish and chips.\"\n\nWhat does the text tell us about Emma?",
     "nationality", ["Her country, language and something she likes.", "She is sad.", "She doesn't eat.", "She lives in Turkey."]),
    ("Friend: What language do you speak at home?\n\nYou: ---\n\nChoose a natural response.",
     "nationality", ["We speak Turkish at home. I learn English at school.", "Language is hard.", "I don't speak.", "Home is far."]),
    ("\"People from different countries have different flags, languages and traditions. It's nice to learn about them.\"\n\nWhat is the main idea?",
     "nationality", ["Countries have different cultures; learning about them is good.", "All countries are the same.", "Flags are not important.", "We should not travel."]),
    # Cartoon Characters
    ("Friend: Who is your favourite cartoon character?\n\nYou: ---\n\nWhich response continues the conversation?",
     "cartoon_characters", ["I like the blue robot. He is funny and brave. Do you watch that show?", "I don't like cartoons.", "Cartoons are for babies.", "No one."]),
    ("\"The cartoon is about a cat that can talk. She goes on adventures with her dog friend. Every episode has a new problem to solve.\"\n\nWhat is the cartoon mainly about?",
     "cartoon_characters", ["A talking cat and her dog friend having adventures.", "A real cat.", "Only dogs.", "No story."]),
    ("Mum: Which cartoon do you want to watch?\n\nChild: ---\n\nChoose a logical response.",
     "cartoon_characters", ["Can we watch the one about the little elephant? I like it.", "Any.", "I hate cartoons.", "Nothing."]),
    ("\"In the picture, the character is wearing a red cape. He is flying over the city. Children love him because he helps people.\"\n\nWhat does this describe?",
     "cartoon_characters", ["A superhero character's look and why children like him.", "A real person.", "A boring character.", "A city without people."]),
    # Free Time
    ("Friend: What do you do in your free time?\n\nYou: ---\n\nWhich answer shows your hobby?",
     "free_time", ["I like drawing and playing football. Sometimes I read comics.", "Nothing.", "School only.", "Free time is boring."]),
    ("\"On weekends, Tom plays in the park. He rides his bike. He also helps his mum in the garden.\"\n\nWhat can we conclude about Tom's weekends?",
     "free_time", ["He does fun and helpful activities.", "He does nothing.", "He only studies.", "He hates weekends."]),
    ("Dad: Do you want to play a game?\n\nChild: ---\n\nWhat would be an enthusiastic reply?",
     "free_time", ["Yes! Can we play cards? I'm good at it.", "No.", "Games are boring.", "I'm tired."]),
    ("\"Some children like singing. Others like dancing or painting. Hobbies make us happy.\"\n\nWhat is the writer saying?",
     "free_time", ["Different people like different hobbies; hobbies bring joy.", "Everyone likes the same thing.", "Hobbies are a waste of time.", "Children have no hobbies."]),
    # My Day
    ("Teacher: What time do you get up?\n\nStudent: ---\n\nChoose the most natural response.",
     "my_day", ["I get up at seven. Then I wash my face and have breakfast.", "I never get up.", "Time is not important.", "I don't know."]),
    ("\"Every morning, she brushes her teeth, gets dressed and has breakfast. She leaves for school at eight o'clock.\"\n\nWhat is the text about?",
     "my_day", ["A morning routine before school.", "Eating only.", "School subjects.", "Evening activities."]),
    ("Mum: Have you packed your school bag?\n\nChild: ---\n\nWhat shows responsibility?",
     "my_day", ["Yes, I packed it last night. I have my books and pencil case.", "I'll do it later.", "I forgot.", "You do it."]),
    ("\"He always has a glass of milk before bed. It helps him sleep. He goes to bed at nine.\"\n\nWhat can we infer?",
     "my_day", ["A bedtime habit and when he sleeps.", "Milk is bad.", "He never sleeps.", "Bedtime doesn't matter."]),
    # Fun with Science
    ("Teacher: What do plants need to grow?\n\nStudent: ---\n\nWhat is the correct answer?",
     "fun_with_science", ["They need water, sunlight and air.", "Only water.", "Nothing.", "They grow in the dark."]),
    ("\"We did an experiment. We put a plant in a dark room. After a week, the plant turned yellow. The plant in the window stayed green.\"\n\nWhat can we conclude?",
     "fun_with_science", ["Plants need light to stay healthy.", "Plants like dark rooms.", "Both plants were the same.", "The experiment failed."]),
    ("Friend: How do we make a shadow?\n\nYou: ---\n\nChoose the correct explanation.",
     "fun_with_science", ["When light cannot go through an object, we see a shadow.", "Shadows come from nothing.", "We need water for shadows.", "Shadows are always round."]),
    ("\"In the picture, the magnet attracts the nails. The plastic spoon does not attract them.\"\n\nWhat does this show?",
     "fun_with_science", ["A magnet attracts some metals; plastic does not.", "Plastic is a magnet.", "All things attract.", "Nails are magnets."]),
    # Jobs
    ("Visitor: What does your father do?\n\nYou: ---\n\nWhat would be a clear answer?",
     "jobs", ["He is a teacher. He works at a primary school.", "He works.", "I don't know.", "Nothing."]),
    ("\"The firefighter helps people in danger. She puts out fires. She wears a helmet and a uniform.\"\n\nWhat is the main idea?",
     "jobs", ["A firefighter's job and what she wears.", "Fires are good.", "Helmets are not important.", "She works in an office."]),
    ("Teacher: What do you want to be when you grow up?\n\nStudent: ---\n\nChoose a thoughtful response.",
     "jobs", ["I want to be a doctor. I like helping people feel better.", "I don't know.", "Nothing.", "A superhero."]),
    ("\"Doctors work in hospitals. They look after sick people. Teachers work in schools. They teach children.\"\n\nWhat does the text compare?",
     "jobs", ["Different jobs and where people work.", "Only doctors.", "Only teachers.", "Hospitals and schools are the same."]),
    # My Clothes
    ("Mum: What do you want to wear to the party?\n\nChild: ---\n\nChoose a logical response.",
     "my_clothes", ["Can I wear my blue shirt and black trousers? They are clean.", "I don't care.", "Nothing.", "Pajamas."]),
    ("\"In winter we wear warm clothes: a coat, a scarf and gloves. In summer we wear light clothes: a T-shirt and shorts.\"\n\nWhat is the text about?",
     "my_clothes", ["Clothes we wear in different seasons.", "Only winter.", "Only summer.", "We wear the same all year."]),
    ("Friend: I like your new shoes!\n\nYou: ---\n\nWhat would be a polite reply?",
     "my_clothes", ["Thank you! I got them last week. They are comfortable.", "So what?", "I don't like them.", "They are old."]),
    ("\"The boy is wearing a red cap, a white T-shirt and blue jeans. He is ready for sports.\"\n\nWhat does this describe?",
     "my_clothes", ["What the boy is wearing and that he is dressed for sport.", "His favourite colour.", "Only the cap.", "Nothing about clothes."]),
    # My Friends
    ("New student: Can I sit next to you?\n\nYou: ---\n\nWhat would be a friendly reply?",
     "my_friends", ["Of course! My name is Ali. What's your name?", "No.", "The seat is taken.", "I don't know you."]),
    ("\"Best friends help each other. They play together. They share their toys. They say sorry when they argue.\"\n\nWhat makes a good friend?",
     "my_friends", ["Helping, playing together, sharing and apologising.", "Friends never help.", "Friends don't play.", "Friends always argue."]),
    ("Friend: I'm sad. My pet is ill.\n\nYou: ---\n\nWhat would show empathy?",
     "my_friends", ["I'm sorry to hear that. I hope your pet gets better soon.", "So what?", "Pets are not important.", "Get a new one."]),
    ("\"Tom and Jake are best friends. They ride bikes together. They do homework together. They never leave each other alone when one is sad.\"\n\nWhat can we infer about their friendship?",
     "my_friends", ["They are close friends who support each other.", "They don't like each other.", "They only do homework.", "They are always sad."]),
    # Food and Drinks
    ("Mum: What would you like for lunch?\n\nChild: ---\n\nChoose a natural response.",
     "food_and_drinks", ["I'd like a sandwich and an apple, please. And some water.", "I don't know.", "Nothing.", "Sweets only."]),
    ("\"Breakfast is important. It gives us energy for the day. Milk, bread and fruit are good choices.\"\n\nWhat is the writer saying?",
     "food_and_drinks", ["Breakfast gives energy; some foods are good for it.", "Breakfast is not important.", "We should never eat breakfast.", "Only milk is good."]),
    ("Friend: Are you hungry? I have an extra sandwich.\n\nYou: ---\n\nWhat would be a polite reply?",
     "food_and_drinks", ["Yes, thank you! That's very kind of you. I forgot my lunch.", "No.", "Give me two.", "I don't like sandwiches."]),
    ("\"In the picture, the table has milk, bread, cheese and oranges. The family is having breakfast together.\"\n\nWhat does the picture show?",
     "food_and_drinks", ["A breakfast table with food and a family eating together.", "A dinner table.", "Only drinks.", "An empty table."]),
    # Extra templates for balance
    ("Teacher: Why should we listen when others speak?\n\nStudent: ---\n\nChoose the correct understanding.",
     "classroom_rules", ["To show respect and understand what they say.", "We don't need to listen.", "Only teachers speak.", "Listening is boring."]),
    ("\"London is the capital of England. Paris is the capital of France. Ankara is the capital of Turkey.\"\n\nWhat does the text tell us?",
     "nationality", ["Capital cities of different countries.", "All capitals are the same.", "Turkey has no capital.", "Only London matters."]),
    ("Friend: Did you see the new episode?\n\nYou: ---\n\nWhich response keeps the conversation going?",
     "cartoon_characters", ["Yes! The dog saved the cat. It was exciting. Did you see it?", "No.", "I don't watch.", "So what?"]),
    ("Mum: Don't forget to do your homework.\n\nChild: ---\n\nWhat shows good habits?",
     "my_day", ["I've already done it. I'll put it in my bag.", "I'll do it tomorrow.", "I forgot.", "Homework is boring."]),
    ("Teacher: Why does the moon look different each night?\n\nStudent: ---\n\nWhat is a correct answer?",
     "fun_with_science", ["Because the moon moves around the Earth and we see different parts of it.", "The moon changes shape.", "It doesn't.", "Magic."]),
    ("Dad: What does a pilot do?\n\nChild: ---\n\nChoose a correct answer.",
     "jobs", ["A pilot flies planes. He or she takes people to different places.", "A pilot drives a car.", "A pilot teaches.", "A pilot cooks."]),
    ("Friend: Your jacket is nice!\n\nYou: ---\n\nWhat is a polite and natural reply?",
     "my_clothes", ["Thank you! It's my favourite. I wear it when it's cold.", "It's old.", "I don't like it.", "So?"]),
    ("Classmate: I can't find my pencil. Can you help?\n\nYou: ---\n\nWhat would be a kind response?",
     "my_friends", ["Sure! You can use one of mine. I have two.", "No.", "Find it yourself.", "I don't have pencils."]),
    ("\"We should drink water every day. It keeps our body healthy. We should not drink too many fizzy drinks.\"\n\nWhat is the main message?",
     "food_and_drinks", ["Water is good for health; limit fizzy drinks.", "Fizzy drinks are best.", "We don't need water.", "Drinks don't matter."]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"eng4_{idx:04d}",
        "stem": stem,
        "options": options,
        "answerIndex": answer_index,
        "difficulty": difficulty,
        "questionType": qtype,
        "topic": topic,
        "skills": skills,
        "explanation": explanation,
        "source": "brainbuddy",
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
    pad = ["None of these fit the context.", "The speaker disagrees.", "It doesn't match the situation.", "The text doesn't say this."]
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
        tpls_for_topic = [t for t in TEMPLATES if t[1] == topic]
        if not tpls_for_topic:
            tpls_for_topic = TEMPLATES
        for _ in range(count):
            tpl = tpls_for_topic[tpl_idx % len(tpls_for_topic)]
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
                [topic[:10] if len(topic) >= 10 else topic, "reading", "inference"],
                expl, f"eng4_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"eng4_{idx:04d}"
        qq["id"] = f"eng4_{idx:04d}"

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
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_eng4_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("ENGLISH4 Question Bank Report")
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
    random.seed(47)
    main()
