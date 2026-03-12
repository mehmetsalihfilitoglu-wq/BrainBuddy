#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 3rd Grade English question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: dialogue completion, situation-response, reading comprehension,
picture/situation interpretation, logical response selection.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/english3"

# 3rd grade curriculum units
TOPICS = [
    "greetings",
    "my_family",
    "people_i_love",
    "feelings",
    "toys_and_games",
    "my_house",
    "in_my_city",
    "transportation",
    "nature",
    "weather",
]

NEW_GEN_TYPES = [
    "dialogue_completion",
    "situation_response",
    "reading_comprehension",
    "situation_interpretation",
    "logical_response",
    "meaning_inference",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Greetings
    ("Teacher: Good morning, class!\n\nStudents: ---\n\nWhat is the best response?",
     "greetings", ["Good morning, teacher!", "Goodbye!", "I am fine.", "Nice to meet you."]),
    ("Your friend says: \"Hi! How are you?\"\n\nYou want to say you feel good. What do you say?",
     "greetings", ["I'm fine, thank you. And you?", "I am eight.", "I have a ball.", "This is my book."]),
    ("A new student comes to your class. The teacher says: \"Say hello to your new friend.\"\n\nWhat should you say?",
     "greetings", ["Hello! What's your name?", "Goodbye!", "Sit down.", "Open your book."]),
    ("You meet your neighbour in the morning. She says: \"Good morning!\"\n\nWhat do you reply?",
     "greetings", ["Good morning!", "Good night!", "Thank you.", "I am happy."]),
    ("Your mum says: \"Good night, dear. Sleep well.\"\n\nWhat is the best reply?",
     "greetings", ["Good night, mum. You too.", "Good morning!", "I am tired.", "Hello!"]),
    # My Family
    ("Teacher: How many people are in your family?\n\nYou: ---\n\nChoose a natural answer.",
     "my_family", ["There are four: my mum, my dad, my sister and me.", "I have a dog.", "My family is nice.", "I love them."]),
    ("\"This is my brother. He is seven. He likes football. We play together.\"\n\nWhat does the text tell us?",
     "my_family", ["The brother's age, hobby and that they play together.", "The brother is sad.", "Football is boring.", "There is no sister."]),
    ("Dad: Who is this in the photo?\n\nChild: ---\n\nWhat would be a clear answer?",
     "my_family", ["This is my grandfather. He lives in the village.", "He is old.", "I like him.", "A photo."]),
    ("Mum: Tell me about your sister.\n\nChild: ---\n\nChoose a logical response.",
     "my_family", ["She is five. She likes drawing. She goes to kindergarten.", "I have a sister.", "Sisters are nice.", "I play with her."]),
    ("\"My mum cooks dinner. My dad reads books. I help my mum in the kitchen.\"\n\nWhat is the main idea?",
     "my_family", ["Each family member does something; the child helps.", "Only mum cooks.", "Dad does nothing.", "The child is bored."]),
    # People I Love
    ("Friend: Who do you love?\n\nYou: ---\n\nWhat is a natural answer?",
     "people_i_love", ["I love my mum, my dad and my grandparents. I love my friends too.", "I love toys.", "Love is good.", "I don't know."]),
    ("\"My teacher is kind. She helps us learn. She smiles when we do well.\"\n\nWhat can we infer about the teacher?",
     "people_i_love", ["She is kind, helpful and encouraging.", "She is strict.", "She doesn't like children.", "She never smiles."]),
    ("Grandma: I made cookies for you!\n\nChild: ---\n\nWhat would show you are happy and thankful?",
     "people_i_love", ["Thank you, Grandma! I love your cookies. You are the best!", "I'm hungry.", "Cookies are nice.", "OK."]),
    ("\"My best friend helps me when I am sad. We share our toys. We never fight.\"\n\nWhat makes them good friends?",
     "people_i_love", ["Helping, sharing and not fighting.", "They have toys.", "They are sad.", "Friends always fight."]),
    ("A neighbour gives you a gift. What do you say?",
     "people_i_love", ["Thank you very much! That's so kind of you.", "I have many gifts.", "It's small.", "Goodbye."]),
    # Feelings
    ("Friend: I lost my favourite toy. I'm sad.\n\nYou: ---\n\nWhat would show you care?",
     "feelings", ["I'm sorry. Don't be sad. Maybe we can look for it together.", "Find it.", "Toys are not important.", "I'm happy."]),
    ("\"Today Tom is happy. He got a good grade. He smiles and jumps. His mum is proud.\"\n\nWhy is Tom happy?",
     "feelings", ["He got a good grade; his mum is proud.", "He jumps.", "He smiles.", "Today is a day."]),
    ("Teacher: How do you feel when you help someone?\n\nStudent: ---\n\nChoose the best answer.",
     "feelings", ["I feel good and happy. I like helping.", "I feel tired.", "I don't help.", "I feel nothing."]),
    ("Your friend is scared before a test. What do you say?",
     "feelings", ["Don't worry. You can do it! I believe in you.", "Tests are hard.", "Be scared.", "I'm scared too."]),
    ("\"When Emma is angry, she counts to ten. Then she feels calm. She talks to her mum.\"\n\nWhat does Emma do when she is angry?",
     "feelings", ["She counts, then feels calm and talks to her mum.", "She shouts.", "She runs away.", "She cries only."]),
    # Toys and Games
    ("Friend: What is your favourite toy?\n\nYou: ---\n\nChoose a natural response.",
     "toys_and_games", ["I like my red car. I play with it every day. Do you want to see it?", "I have toys.", "Toys are fun.", "A toy."]),
    ("\"In the picture, two children play with a ball. One throws, one catches. They laugh. It is sunny.\"\n\nWhat does the picture show?",
     "toys_and_games", ["Children playing ball together, having fun in the sun.", "A ball only.", "One child.", "They are sad."]),
    ("Mum: Let's play a game. What do you want to play?\n\nChild: ---\n\nWhat is a logical answer?",
     "toys_and_games", ["Can we play hide and seek? I love that game!", "No.", "Games are boring.", "I don't know."]),
    ("Friend: Can I play with your teddy bear?\n\nYou: ---\n\nWhat would be a friendly reply?",
     "toys_and_games", ["Sure! Be careful with it. It's my favourite.", "No.", "It's mine.", "Take it."]),
    ("\"The children play in the park. They have a kite, a ball and skipping ropes. Everyone is happy.\"\n\nWhat is the text about?",
     "toys_and_games", ["Children playing with different things in the park.", "Only a kite.", "Only a ball.", "The park is empty."]),
    # My House
    ("Teacher: Where do you live? Describe your house.\n\nYou: ---\n\nChoose a clear answer.",
     "my_house", ["I live in a flat. We have three rooms: a living room, a kitchen and two bedrooms.", "I have a house.", "My house is big.", "I live here."]),
    ("\"In my room I have a bed, a desk and a cupboard. My toys are on the shelf. I like my room.\"\n\nWhat is in the room?",
     "my_house", ["A bed, a desk, a cupboard and toys on the shelf.", "Only a bed.", "No toys.", "A kitchen."]),
    ("Mum: Where is your bag? Go and get it. We must leave.\n\nChild: ---\n\nWhat would be a responsible reply?",
     "my_house", ["It's in my room. I'll get it now.", "I don't know.", "You get it.", "It's lost."]),
    ("Friend: Is there a garden at your house?\n\nYou: ---\n\nChoose a natural response.",
     "my_house", ["Yes, we have a small garden. We have flowers and a tree.", "Yes.", "No garden.", "A house."]),
    ("\"The kitchen has a table, chairs, a fridge and a cooker. We eat breakfast there every morning.\"\n\nWhat is the kitchen for?",
     "my_house", ["Cooking and eating; they have breakfast there.", "Sleeping.", "Playing.", "Only a fridge."]),
    # In My City
    ("Visitor: What places are in your city?\n\nYou: ---\n\nWhat would be a clear answer?",
     "in_my_city", ["We have a park, a library, a supermarket and a hospital. I like the park.", "A city.", "Many places.", "I don't go out."]),
    ("\"The library is quiet. People read books there. Children can borrow books. It is open every day.\"\n\nWhat can you do at the library?",
     "in_my_city", ["Read and borrow books.", "Play football.", "Eat lunch.", "Sleep."]),
    ("Mum: We need bread. Where should we go?\n\nChild: ---\n\nWhat is the correct place?",
     "in_my_city", ["We should go to the supermarket or the bakery.", "To the hospital.", "To the park.", "To school."]),
    ("Friend: Where do you go when you are ill?\n\nYou: ---\n\nChoose the right answer.",
     "in_my_city", ["I go to the hospital or the doctor. The doctor helps me feel better.", "To the park.", "To school.", "I stay at home."]),
    ("\"In the picture, there is a big park. Children play. Parents sit on benches. There are trees and flowers.\"\n\nWhat does the picture show?",
     "in_my_city", ["A park with children playing, parents on benches, trees and flowers.", "A school.", "A hospital.", "Only trees."]),
    # Transportation
    ("Teacher: How do you come to school?\n\nStudent: ---\n\nChoose a natural answer.",
     "transportation", ["I come by bus. My mum takes me to the bus stop.", "I come.", "By car.", "School is far."]),
    ("\"The car has four wheels. The bus is big. It has many seats. Many people travel by bus.\"\n\nWhat does the text tell us?",
     "transportation", ["Cars and buses: how they look and that many people use the bus.", "Cars only.", "Buses only.", "Wheels only."]),
    ("Dad: We must go to the hospital. It's far. What should we use?\n\nChild: ---\n\nWhat is logical?",
     "transportation", ["We can go by car or by bus. The car is faster.", "We walk.", "We stay home.", "A bike."]),
    ("Friend: Do you have a bicycle?\n\nYou: ---\n\nWhat would be a natural reply?",
     "transportation", ["Yes, I have a blue bicycle. I ride it in the park at weekends.", "Yes.", "No.", "I like bikes."]),
    ("\"The train is long. It has many carriages. It goes fast. People use it to travel to other cities.\"\n\nWhy do people use the train?",
     "transportation", ["To travel to other cities; it is fast and has many carriages.", "To play.", "To sleep.", "Trains are slow."]),
    # Nature
    ("Teacher: What do you see in nature?\n\nStudent: ---\n\nChoose a good answer.",
     "nature", ["I see trees, flowers, birds, butterflies and the sky. I love nature.", "Nature is nice.", "Trees.", "I don't know."]),
    ("\"The tree has green leaves. Birds live in it. In autumn the leaves turn yellow and fall.\"\n\nWhat happens in autumn?",
     "nature", ["Leaves turn yellow and fall.", "Birds leave.", "The tree dies.", "Nothing changes."]),
    ("Mum: Look at the butterfly! It's beautiful.\n\nChild: ---\n\nWhat would show you notice and like it?",
     "nature", ["Yes! It's yellow and orange. I like butterflies. They fly from flower to flower.", "OK.", "I see it.", "Butterflies are small."]),
    ("\"In the garden there are roses, tulips and daisies. Bees and butterflies visit the flowers. The sun shines.\"\n\nWhat do bees and butterflies do?",
     "nature", ["They visit the flowers.", "They eat.", "They sleep.", "They hide."]),
    ("Friend: What animals do you like?\n\nYou: ---\n\nChoose a natural response.",
     "nature", ["I like birds and rabbits. Birds can fly. Rabbits are soft and cute.", "Animals.", "I like cats.", "Animals are nice."]),
    # Weather
    ("Teacher: What's the weather like today?\n\nStudent: ---\n\nWhat would be a clear answer?",
     "weather", ["It's sunny and warm today. The sky is blue. I can wear my T-shirt.", "It's nice.", "Weather.", "I don't know."]),
    ("\"It is raining. The children cannot play outside. They stay indoors. They draw and read books.\"\n\nWhy do the children stay indoors?",
     "weather", ["Because it is raining; they can't play outside.", "They are tired.", "They don't like playing.", "It is cold."]),
    ("Mum: It's cold and snowy. What should you wear?\n\nChild: ---\n\nWhat is the right answer?",
     "weather", ["I should wear my coat, hat, scarf and gloves. And warm boots.", "A T-shirt.", "Shorts.", "Nothing special."]),
    ("Friend: Is it hot in summer where you live?\n\nYou: ---\n\nChoose a natural reply.",
     "weather", ["Yes, it's very hot in summer. We go to the beach and swim. I love summer.", "Yes.", "No.", "Summer is long."]),
    ("\"In winter it snows. Children make snowmen. They wear thick coats. They drink hot chocolate when they come inside.\"\n\nWhat do children do in winter?",
     "weather", ["Make snowmen, wear thick coats and drink hot chocolate inside.", "They only stay inside.", "They swim.", "They wear T-shirts."]),
    # Extra for balance
    ("Someone says: \"Nice to meet you!\"\n\nYou reply: ---",
     "greetings", ["Nice to meet you too!", "Goodbye!", "I am eight.", "Thank you."]),
    ("\"My dad works in an office. He comes home at six. We have dinner together.\"\n\nWhen does the family have dinner?",
     "my_family", ["After dad comes home at six.", "In the morning.", "At school.", "Never."]),
    ("Friend: I'm so happy! I won the race!\n\nYou: ---\n\nWhat would show you share their joy?",
     "feelings", ["Wow! Congratulations! I'm so happy for you! You are fast!", "OK.", "So what?", "I don't care."]),
    ("Teacher: What do you do with your toys when you finish playing?\n\nStudent: ---\n\nWhat shows good habits?",
     "toys_and_games", ["I put them back in the box. My mum says a tidy room is nice.", "I leave them.", "I don't have toys.", "I break them."]),
    ("\"Our flat is on the third floor. We take the lift. Our neighbours are kind.\"\n\nHow do they go to their flat?",
     "my_house", ["They take the lift.", "They walk.", "They run.", "They fly."]),
    ("Dad: The museum is in the city centre. How can we get there?\n\nChild: ---\n\nWhat is logical?",
     "in_my_city", ["We can take the bus or we can walk if it's not far.", "We can't go.", "We fly.", "We stay home."]),
    ("\"The bus stops at the bus stop. People get on and off. The bus driver says hello.\"\n\nWhere do people get on the bus?",
     "transportation", ["At the bus stop.", "At home.", "In the park.", "At school only."]),
    ("\"The river is clear. Fish swim in it. Ducks float on the water. Trees grow on the bank.\"\n\nWhat is in the river?",
     "nature", ["Fish and ducks; trees on the bank.", "Only water.", "No fish.", "Boats only."]),
    ("\"When it's windy, kites fly high. Leaves move. We must hold our hats. It's fun to fly kites.\"\n\nWhat can you do when it's windy?",
     "weather", ["Fly kites; the wind makes kites go high.", "Swim.", "Make a snowman.", "Stay indoors only."]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"eng3_{idx:04d}",
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
    pad = [
        "None of these fit the context.",
        "It doesn't match the situation.",
        "The speaker would not say this.",
        "The text doesn't say this.",
    ]
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
            expl = (
                f'The correct answer fits the context, tone and situation. "{correct}" is appropriate. '
                'Other options misunderstand the situation, tone or implied meaning.'
            )
            out.append(q(
                len(out),
                stem,
                options,
                ai,
                random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES),
                topic,
                [topic[:10] if len(topic) >= 10 else topic, "reading", "inference"],
                expl,
                f"eng3_{topic}_{len(out):04d}",
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"eng3_{idx:04d}"
        qq["id"] = f"eng3_{idx:04d}"

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
        path = OUT_DIR / f"lgs_eng3_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("ENGLISH3 (3rd Grade) Question Bank Report")
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
    random.seed(38)
    main()
