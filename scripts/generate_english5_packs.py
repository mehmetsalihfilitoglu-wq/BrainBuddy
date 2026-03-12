#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 5th Grade English question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: dialogue, scenario, reading comprehension, inference.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/english5"

TOPICS = [
    "hello",
    "my_town",
    "games_hobbies",
    "daily_routine",
    "health",
    "movies",
    "party_time",
    "fitness",
    "animal_shelter",
    "festivals",
]

NEW_GEN_TYPES = [
    "dialogue_completion",
    "situation_response",
    "reading_comprehension",
    "inference",
    "paragraph_interpretation",
    "visual_interpretation",
    "logical_response",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Hello
    ("Person A: Hello! Nice to meet you.\n\nPerson B: ---\n\nWhich response best fits the situation?",
     "hello", ["Nice to meet you too! I'm Alex.", "Goodbye.", "I don't know you.", "What do you want?"]),
    ("Teacher: Good morning, class! How are you today?\n\nStudents: ---\n\nChoose the most appropriate response.",
     "hello", ["We're fine, thank you! How are you?", "We're leaving.", "Class is boring.", "Nothing."]),
    ("\"When people meet for the first time, they often introduce themselves and shake hands. A smile shows friendliness.\"\n\nWhat can we infer from this?",
     "hello", ["Greetings and body language help create a positive first impression.", "Handshakes are rude.", "Smiles don't matter.", "People never introduce themselves."]),
    ("Stranger: Excuse me, what's your name?\n\nYou: ---\n\nWhat would be a polite and logical reply?",
     "hello", ["My name is Elif. And you?", "None of your business.", "I don't have a name.", "Why do you ask?"]),
    ("\"In English, we say 'How are you?' to ask about someone's well-being. The usual reply is 'I'm fine, thanks.'\"\n\nWhat is the main idea?",
     "hello", ["There are common phrases for greeting and asking about well-being.", "We never ask how someone is.", "Replies don't matter.", "Greetings are the same in every language."]),
    # My Town
    ("Tourist: Where is the library in this town?\n\nLocal: ---\n\nWhat would be the most helpful reply?",
     "my_town", ["It's on Main Street, next to the post office. You can walk there in five minutes.", "I don't know.", "The library is closed.", "Why do you need it?"]),
    ("\"Our town has a park, a school, and a hospital. The bus station is in the centre. Many people use it every day.\"\n\nWhat can we conclude about the town?",
     "my_town", ["It has basic facilities and public transport for residents.", "The town is empty.", "Nobody uses the bus.", "There is no school."]),
    ("Friend: What is there to do in your town at weekends?\n\nYou: ---\n\nChoose the most logical response.",
     "my_town", ["We can go to the cinema, visit the park, or have a snack at the café.", "Nothing.", "The town is boring.", "I stay at home only."]),
    ("\"The supermarket is opposite the bank. The pharmacy is next to the school.\"\n\nWhat does the text describe?",
     "my_town", ["The location of places in the town.", "The prices of things.", "How to drive.", "School subjects."]),
    # Games and Hobbies
    ("Friend: What do you like to do in your free time?\n\nYou: ---\n\nWhich response shows your hobby?",
     "games_hobbies", ["I enjoy playing football and reading comic books.", "I do nothing.", "School is my hobby.", "I hate free time."]),
    ("\"Playing board games with family can be fun. It helps people spend time together and talk.\"\n\nWhat is the main message?",
     "games_hobbies", ["Hobbies like board games can bring people together.", "Board games are boring.", "Families never play together.", "Talking is not important."]),
    ("Mum: Why don't you go outside and play with your friends?\n\nChild: ---\n\nWhat would be a reasonable response?",
     "games_hobbies", ["Good idea! I'll call them. We can play in the park.", "I don't have friends.", "Playing is boring.", "I'm too tired."]),
    ("\"Some people collect stamps, others draw or play music. Hobbies make us happy and relaxed.\"\n\nWhat can we infer?",
     "games_hobbies", ["Different hobbies suit different people and can improve mood.", "Only one hobby is good.", "Hobbies are a waste of time.", "Nobody has hobbies."]),
    # My Daily Routine
    ("Teacher: What time do you usually get up?\n\nStudent: ---\n\nChoose the most natural response.",
     "daily_routine", ["I get up at seven o'clock. Then I have breakfast.", "I never get up.", "I don't have a routine.", "Time doesn't matter."]),
    ("\"Every morning, Tom brushes his teeth, washes his face, and has breakfast. He leaves for school at eight.\"\n\nWhat is the text about?",
     "daily_routine", ["A typical morning routine before school.", "Tom's favourite food.", "School subjects.", "Evening activities."]),
    ("Mum: Have you done your homework yet?\n\nChild: ---\n\nWhat would show responsibility?",
     "daily_routine", ["Yes, I finished it after school. I'll show you.", "I'll do it later.", "Homework is boring.", "I forgot."]),
    ("\"She always has a shower before bed. It helps her sleep better.\"\n\nWhat does this suggest?",
     "daily_routine", ["Routines like a shower can help relaxation and sleep.", "Showers are bad.", "She never sleeps.", "Routines don't matter."]),
    # Health
    ("Doctor: Do you eat fruits and vegetables every day?\n\nPatient: ---\n\nChoose the honest and relevant response.",
     "health", ["Yes, I try to eat an apple and some vegetables at lunch.", "I never eat them.", "Food doesn't matter.", "Only on Sundays."]),
    ("\"Washing your hands before meals helps prevent germs. It's a simple habit that keeps you healthy.\"\n\nWhat is the writer encouraging?",
     "health", ["A simple habit to stay healthy.", "Eating more food.", "Ignoring germs.", "Washing only in the morning."]),
    ("Friend: I have a headache. What should I do?\n\nYou: ---\n\nWhat would be helpful advice?",
     "health", ["You could rest a bit and drink some water. If it continues, ask an adult.", "Do nothing.", "Play video games.", "Eat sweets."]),
    ("\"Getting enough sleep is important for children. It helps the body and mind grow.\"\n\nWhat can we infer?",
     "health", ["Sleep supports physical and mental development.", "Sleep is not important.", "Children don't need sleep.", "Sleep causes problems."]),
    # Movies
    ("Friend: Did you watch the new cartoon last night?\n\nYou: ---\n\nWhich response continues the conversation?",
     "movies", ["Yes! It was funny. The ending was surprising. Did you like it?", "No.", "I don't watch TV.", "So what?"]),
    ("\"The film is about a boy who finds a magic key. He goes on an adventure with his dog.\"\n\nWhat is the main idea?",
     "movies", ["The film tells an adventure story with a boy and his dog.", "The film is boring.", "Dogs can't act.", "There is no magic."]),
    ("Mum: Which film do you want to see?\n\nChild: ---\n\nChoose a logical response.",
     "movies", ["Can we watch the animation? I heard it's good for kids.", "I don't care.", "Any film.", "No films."]),
    ("\"In the cinema, we should be quiet during the film. We shouldn't use our phones.\"\n\nWhat is the writer explaining?",
     "movies", ["Good behaviour when watching a film in the cinema.", "How to buy tickets.", "Film prices.", "Cinema food."]),
    # Party Time
    ("Host: Thank you for coming to my birthday party!\n\nGuest: ---\n\nWhat would be a polite reply?",
     "party_time", ["Thank you for inviting me! I had a great time.", "The party was boring.", "I want to leave.", "No problem."]),
    ("\"At birthday parties, people often bring gifts, sing 'Happy Birthday', and eat cake. It's a time to celebrate.\"\n\nWhat is the main message?",
     "party_time", ["Birthday parties have common traditions and are a time to celebrate.", "Parties are always loud.", "No one brings gifts.", "Cake is unhealthy."]),
    ("Friend: Are you coming to my party on Saturday?\n\nYou: ---\n\nChoose the most appropriate response.",
     "party_time", ["Yes, I'd love to! What time does it start? Do I need to bring anything?", "Maybe.", "I'm busy.", "Parties are boring."]),
    ("\"We decorated the room with balloons and streamers. Everyone wore party hats.\"\n\nWhat does this describe?",
     "party_time", ["Party decorations and how people dressed.", "Food at the party.", "Party games.", "When the party ended."]),
    # Fitness
    ("Coach: How often do you exercise?\n\nStudent: ---\n\nChoose the relevant and honest response.",
     "fitness", ["I play football twice a week and sometimes ride my bike.", "I never exercise.", "Exercise is boring.", "I don't know."]),
    ("\"Running, swimming, and cycling are good for your heart. Even walking every day helps.\"\n\nWhat can we infer?",
     "fitness", ["Regular physical activity is beneficial for health.", "Only running is good.", "Swimming is dangerous.", "Exercise has no benefits."]),
    ("Friend: I want to get fit. What can I do?\n\nYou: ---\n\nWhat would be helpful advice?",
     "fitness", ["You could start with short walks or ride your bike. Do something you enjoy!", "Do nothing.", "Eat more sweets.", "Stay at home."]),
    ("\"Stretching before exercise helps prevent injuries. It warms up your muscles.\"\n\nWhat is the writer explaining?",
     "fitness", ["Why warming up before exercise is useful.", "How to run fast.", "What to eat.", "When to stop."]),
    # The Animal Shelter
    ("Volunteer: Would you like to help feed the dogs?\n\nYou: ---\n\nWhat would be an enthusiastic response?",
     "animal_shelter", ["Yes, I'd love to! Can you show me what to do?", "No, I'm scared.", "Dogs are dirty.", "I don't have time."]),
    ("\"The animal shelter cares for lost and homeless animals. Volunteers help clean, feed, and play with them.\"\n\nWhat is the main idea?",
     "animal_shelter", ["Shelters help animals in need with the help of volunteers.", "Animals are not important.", "Only dogs live in shelters.", "Volunteers are paid."]),
    ("Teacher: Why is it important to be kind to animals?\n\nStudent: ---\n\nChoose a thoughtful response.",
     "animal_shelter", ["Because they have feelings too. They need food, care, and love.", "Animals don't feel.", "It's not important.", "Only pets matter."]),
    ("\"Before adopting a pet, you should think about who will feed it and take it for walks every day.\"\n\nWhat is the writer suggesting?",
     "animal_shelter", ["Owning a pet requires responsibility and daily care.", "Pets are easy.", "You don't need to care for pets.", "Adoption is free and simple."]),
    # Festivals
    ("Friend: What do you do at the New Year festival?\n\nYou: ---\n\nChoose a logical response.",
     "festivals", ["We usually have a big dinner with family and watch fireworks at midnight.", "Nothing.", "I don't like festivals.", "We stay home and sleep."]),
    ("\"At the harvest festival, people celebrate the crops. There is music, dance, and special food.\"\n\nWhat is the text describing?",
     "festivals", ["A festival that celebrates farming and includes music and food.", "A sports event.", "A school day.", "A quiet day."]),
    ("Teacher: Why do we celebrate national holidays?\n\nStudent: ---\n\nWhat would be a correct understanding?",
     "festivals", ["To remember important events and feel proud of our country.", "To have a day off.", "There is no reason.", "Only adults celebrate."]),
    ("\"Children often dress up and go from door to door at Halloween. They say 'Trick or treat!' and get sweets.\"\n\nWhat does 'Trick or treat' mean in this context?",
     "festivals", ["A phrase used to ask for sweets on Halloween.", "A game name.", "A type of costume.", "A song title."]),
    # Extra templates for balance
    ("Stranger: Hi! I'm new here. Can you tell me your name?\n\nYou: ---\n\nWhat is a friendly reply?",
     "hello", ["Of course! I'm Zeynep. Welcome to our class!", "Go away.", "I won't tell you.", "Why?"]),
    ("\"The town square is the heart of the city. People meet there for concerts and markets.\"\n\nWhat can we infer about the town square?",
     "my_town", ["It is a central place for community events and gatherings.", "Nobody goes there.", "There are no events.", "Markets are banned."]),
    ("Dad: What game do you want to play?\n\nChild: ---\n\nChoose a natural response.",
     "games_hobbies", ["Can we play hide and seek? Or maybe a board game?", "No games.", "I'm bored.", "I want to sleep."]),
    ("\"She sets her alarm for six-thirty. She never hits the snooze button.\"\n\nWhat does this tell us about her?",
     "daily_routine", ["She is disciplined about waking up on time.", "She sleeps late.", "She doesn't use an alarm.", "She is lazy."]),
    ("Nurse: How many glasses of water do you drink a day?\n\nChild: ---\n\nChoose an appropriate response.",
     "health", ["I try to drink about four or five glasses. Sometimes I forget.", "I don't drink water.", "Only one.", "I don't know."]),
    ("Parent: What did you think of the film?\n\nChild: ---\n\nWhat would show engagement?",
     "movies", ["I liked the hero. He was brave. But the villain was scary!", "It was okay.", "I don't remember.", "Boring."]),
    ("Host: Would you like some cake?\n\nGuest: ---\n\nChoose a polite response.",
     "party_time", ["Yes, please! It looks delicious. Thank you.", "No.", "I'm full already.", "I don't like cake."]),
    ("PE Teacher: Why is warming up important before sports?\n\nStudent: ---\n\nWhat would be a correct answer?",
     "fitness", ["To prepare our muscles and avoid getting hurt.", "It's not important.", "To waste time.", "I don't know."]),
    ("\"The shelter has many cats and dogs waiting for a new home. Visitors can come and meet them.\"\n\nWhat is the purpose of the shelter?",
     "animal_shelter", ["To find new homes for animals and let people meet them.", "To keep animals forever.", "To sell animals.", "To train animals for shows."]),
    ("\"During the festival, the streets are full of lights and music. Everyone dances and has fun together.\"\n\nWhat mood does this create?",
     "festivals", ["A joyful, celebratory atmosphere.", "A sad mood.", "A quiet place.", "A scary event."]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"eng5_{idx:04d}",
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
                expl, f"eng5_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"eng5_{idx:04d}"
        qq["id"] = f"eng5_{idx:04d}"

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
        path = OUT_DIR / f"lgs_eng5_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("ENGLISH5 Question Bank Report")
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
    random.seed(53)
    main()
