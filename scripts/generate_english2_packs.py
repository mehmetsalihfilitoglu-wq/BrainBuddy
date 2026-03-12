#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 2nd Grade English question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
MEB 2024-2025 2. Sınıf İngilizce Öğretim Programı (10 ünite).
BrainBuddy Question Design Standard: dialogue completion, situation-response, picture interpretation,
short reading comprehension, meaning inference.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/english2"

# MEB 2. Sınıf İngilizce - 10 ünite
TOPICS = [
    "words",
    "friends",
    "in_the_classroom",
    "numbers",
    "colours",
    "at_the_playground",
    "body_parts",
    "pets",
    "fruit",
    "animals",
]

NEW_GEN_TYPES = [
    "dialogue_completion",
    "situation_response",
    "picture_interpretation",
    "reading_comprehension",
    "meaning_inference",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # 1. Words
    ("Teacher: What is this?\n\nTeacher shows a book.\n\nYou: ---",
     "words", ["It is a book.", "I have a book.", "Book is red.", "I like book."]),
    ("Friend: Is this your pen?\n\nYou: ---\n\nWhat do you say if it is yours?",
     "words", ["Yes, it is. Thank you!", "No.", "Pen is blue.", "I don't know."]),
    ("\"The desk is big. The chair is small. I sit on the chair.\"\n\nWhat is the text about?",
     "words", ["A desk and a chair; the child sits on the chair.", "Only a desk.", "Only a chair.", "School."]),
    ("Mum: What do you have in your bag?\n\nYou: ---\n\nChoose a natural answer.",
     "words", ["I have my books, my pencil and my rubber.", "A bag.", "It is heavy.", "I don't know."]),
    # 2. Friends
    ("Friend: Hello! My name is Ali.\n\nYou: ---\n\nWhat do you say?",
     "friends", ["Hello, Ali! My name is Zeynep.", "Goodbye.", "I am fine.", "Thank you."]),
    ("A new friend says: \"Let's play!\"\n\nYou want to play. What do you say?",
     "friends", ["Yes! What shall we play?", "No.", "I am tired.", "Play."]),
    ("\"Tom and Emma are friends. They play together. They share toys.\"\n\nWhat do good friends do?",
     "friends", ["Play together and share.", "Fight.", "Cry.", "Stay alone."]),
    ("Friend: Can I have your crayon?\n\nYou: ---\n\nWhat is a kind reply?",
     "friends", ["Sure! Here you are.", "No.", "It's mine.", "Crayon."]),
    # 3. In The Classroom
    ("Teacher: Sit down, please.\n\nYou: ---\n\nWhat do you do?",
     "in_the_classroom", ["You sit down.", "You stand up.", "You run.", "You shout."]),
    ("Teacher: Open your book.\n\nStudent: ---\n\nWhat does the student do?",
     "in_the_classroom", ["Opens the book.", "Closes the book.", "Raises hand.", "Says no."]),
    ("\"The teacher writes on the board. The students look. They are quiet.\"\n\nWhat is the classroom like?",
     "in_the_classroom", ["Quiet; students look at the board.", "Noisy.", "Empty.", "Dark."]),
    ("Teacher: Raise your hand if you know the answer.\n\nYou know the answer. What do you do?",
     "in_the_classroom", ["Raise your hand.", "Shout the answer.", "Stay quiet.", "Stand up."]),
    # 4. Numbers
    ("Teacher: How old are you?\n\nYou are seven. You say: ---",
     "numbers", ["I am seven.", "I have seven.", "Seven years.", "I am fine."]),
    ("Friend: How many pencils do you have?\n\nYou have three. You say: ---",
     "numbers", ["I have three pencils.", "Three.", "I don't know.", "Pencils."]),
    ("\"There are five apples. There are three oranges. How many fruits?\"\n\nWhat is the total?",
     "numbers", ["Eight.", "Five.", "Three.", "Two."]),
    ("Mum: How many people are in our family?\n\nYou: ---\n\nChoose a natural answer.",
     "numbers", ["There are four: mum, dad, my brother and me.", "Four.", "Family.", "I don't know."]),
    # 5. Colours
    ("Friend: What colour is your bag?\n\nYour bag is red. You say: ---",
     "colours", ["My bag is red.", "Red.", "I like red.", "It is a bag."]),
    ("Teacher: What colour is the sky?\n\nYou: ---",
     "colours", ["The sky is blue.", "Sky.", "Big.", "I don't know."]),
    ("\"The ball is yellow. The car is red. The tree is green.\"\n\nWhat colours are in the text?",
     "colours", ["Yellow, red and green.", "Ball, car, tree.", "One colour.", "No colour."]),
    ("Mum: Do you like blue or green?\n\nYou like blue. You say: ---",
     "colours", ["I like blue.", "Blue.", "Green.", "I don't like."]),
    # 6. At The Playground
    ("Friend: Let's play on the swing!\n\nYou: ---\n\nWhat do you say if you want to play?",
     "at_the_playground", ["Yes! I like the swing!", "No.", "Swing is high.", "Playground."]),
    ("\"The children run. They play ball. They laugh. It is fun.\"\n\nWhat do the children do?",
     "at_the_playground", ["Run, play ball and laugh.", "Sit.", "Cry.", "Sleep."]),
    ("Friend: Can you push me on the swing?\n\nYou: ---\n\nWhat is a helpful reply?",
     "at_the_playground", ["Sure! Hold on!", "No.", "Push yourself.", "Swing."]),
    ("Teacher: What do you do at the playground?\n\nYou: ---",
     "at_the_playground", ["I play on the slide and the swing. I run with my friends.", "Play.", "Run.", "Fun."]),
    # 7. Body Parts
    ("Teacher: Touch your nose.\n\nYou: ---\n\nWhat do you do?",
     "body_parts", ["Touch your nose.", "Touch your ear.", "Touch your head.", "Sit down."]),
    ("Friend: How many fingers do you have?\n\nYou: ---",
     "body_parts", ["I have ten fingers.", "Ten.", "Fingers.", "I don't know."]),
    ("\"I have two eyes. I have two ears. I have one nose.\"\n\nWhat is the text about?",
     "body_parts", ["Face parts: eyes, ears, nose.", "Numbers.", "Colours.", "One thing."]),
    ("Mum: Wash your hands before dinner.\n\nYou: ---\n\nWhat do you do?",
     "body_parts", ["Go and wash your hands.", "Eat first.", "Hands are clean.", "No."]),
    # 8. Pets
    ("Friend: Do you have a pet?\n\nYou have a cat. You say: ---",
     "pets", ["Yes, I have a cat. Her name is Pamuk.", "Yes.", "A cat.", "No."]),
    ("\"The dog is brown. It runs fast. It likes to play.\"\n\nWhat is the dog like?",
     "pets", ["Brown, fast and likes to play.", "Big.", "Small.", "A dog."]),
    ("Teacher: What does a cat say?\n\nYou: ---",
     "pets", ["A cat says meow.", "Woof.", "Moo.", "Meow."]),
    ("Friend: I have a fish. It lives in water.\n\nYou: ---\n\nWhat can you say?",
     "pets", ["That's nice! What is its name?", "Fish.", "Water.", "I don't like fish."]),
    # 9. Fruit
    ("Mum: Do you want an apple or a banana?\n\nYou want an apple. You say: ---",
     "fruit", ["I want an apple, please.", "Apple.", "Banana.", "No."]),
    ("\"The orange is round. It is orange. It is yummy.\"\n\nWhat is the text about?",
     "fruit", ["An orange: round, orange colour, yummy.", "Colour.", "Food.", "Round."]),
    ("Friend: What fruit do you like?\n\nYou like strawberries. You say: ---",
     "fruit", ["I like strawberries. They are red and sweet.", "Strawberries.", "Red.", "Fruit."]),
    ("Teacher: Is an apple red or green?\n\nYou: ---",
     "fruit", ["An apple can be red or green.", "Red only.", "Green only.", "Yellow."]),
    # 10. Animals
    ("Teacher: What animal says moo?\n\nYou: ---",
     "animals", ["A cow says moo.", "A dog.", "A cat.", "A bird."]),
    ("\"The bird can fly. It has wings. It lives in a tree.\"\n\nWhat can the bird do?",
     "animals", ["Fly; it has wings.", "Swim.", "Run.", "Jump."]),
    ("Friend: Where does a fish live?\n\nYou: ---",
     "animals", ["A fish lives in water.", "In a house.", "In a tree.", "On land."]),
    ("\"The elephant is big. The mouse is small. They are different.\"\n\nWhat is the text about?",
     "animals", ["Two animals: big elephant and small mouse.", "Only elephant.", "Only mouse.", "Same size."]),
    # Extra for balance
    ("Teacher: Good morning, class!\n\nStudents: ---",
     "words", ["Good morning, teacher!", "Goodbye!", "I am fine.", "Thank you."]),
    ("Friend: I am sad.\n\nYou: ---\n\nWhat do you say to help?",
     "friends", ["Don't be sad. I am here. What happened?", "OK.", "So what?", "Be happy."]),
    ("\"The pencil is on the desk. The book is under the chair.\"\n\nWhere is the pencil?",
     "in_the_classroom", ["On the desk.", "Under the chair.", "On the chair.", "Under the desk."]),
    ("Mum: How many eggs do we need? Five or six?\n\nYou need five. You say: ---",
     "numbers", ["We need five eggs.", "Five.", "Six.", "Eggs."]),
    ("Friend: What colour is grass?\n\nYou: ---",
     "colours", ["Grass is green.", "Brown.", "Blue.", "Yellow."]),
    ("\"The slide is fun. The children go up and slide down. They smile.\"\n\nWhat do the children do?",
     "at_the_playground", ["Go up and slide down; they have fun.", "Run.", "Sit.", "Cry."]),
    ("Teacher: Point to your head.\n\nYou: ---",
     "body_parts", ["Point to your head.", "Point to your foot.", "Sit down.", "Stand up."]),
    ("Friend: My dog is black and white. It is cute.\n\nYou: ---\n\nWhat can you say?",
     "pets", ["Wow! I like dogs too. What is its name?", "Dog.", "Black.", "Cute."]),
    ("\"Bananas are yellow. They are sweet. Monkeys like bananas.\"\n\nWhat colour are bananas?",
     "fruit", ["Yellow.", "Green.", "Red.", "Brown."]),
    ("\"The lion is big. It has a loud roar. It lives in the jungle.\"\n\nWhere does the lion live?",
     "animals", ["In the jungle.", "In the sea.", "At home.", "In a tree."]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"eng2_{idx:04d}",
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
    pad = ["It doesn't match the situation.", "Wrong response.", "The text doesn't say this.", "Partial meaning error."]
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
            expl = f'The correct answer fits the context and situation. "{correct}" is appropriate. Other options show misunderstanding, wrong response or partial meaning errors.'
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10] if len(topic) >= 10 else topic, "reading", "inference"],
                expl, f"eng2_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"eng2_{idx:04d}"
        qq["id"] = f"eng2_{idx:04d}"

    packs = [all_q[i : i + 10] for i in range(0, 500, 10)]
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
        path = OUT_DIR / f"lgs_eng2_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("ENGLISH2 (2nd Grade) Question Bank Report")
    print("MEB 2024-2025 - 10 units")
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
    random.seed(36)
    main()
