# BrainBuddy Question Generator

Generates 500 diverse questions per subject for grade 6 (2500 total).

## Requirements

- Python 3.7+

## Usage

```bash
# Generate 500 questions per subject into assets/packs
python question_generator.py --output-dir ../app/src/main/assets/packs

# Custom output directory
python question_generator.py --output-dir ./output

# Fewer questions (e.g. for testing)
python question_generator.py --per-subject 50 --output-dir ../app/src/main/assets/packs

# Custom random seed for reproducibility
python question_generator.py --seed 123
```

## Output

Produces JSON files compatible with `DbSeeder`:

- `grade6_mat.json` - Mathematics (word problems, ratio, multi-step)
- `grade6_turkce.json` - Turkish (paragraph comprehension, inference)
- `grade6_fen.json` - Science (experiments, cause-effect)
- `grade6_sosyal.json` - Social studies (history, maps)
- `grade6_ing.json` - English (reading, vocabulary in context)

## Design Rules

| Subject | Focus | Avoids |
|---------|-------|--------|
| MAT | Word problems, ratio, multi-step | Simple operations |
| TURKCE | Paragraph reading, inference, meaning | Simple grammar/definition |
| FEN | Experiment interpretation, cause-effect | Rote recall |
| SOSYAL | History, map reasoning, cause-effect | Simple fact recall |
| ING | Reading, sentence meaning, vocab | Simple translation |

## JSON Schema

Each question includes:

- `id`, `grade`, `subject`, `difficulty` (0–2)
- `questionType`, `stem`, `options`, `answer`, `correctIndex`
- `explanation`, `skills`
