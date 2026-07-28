# Visual Question Support (Image / Graph / Table)

## Overview

Questions can include an image, graph, or table by setting the visual asset path in the JSON. The app loads the image from `assets/` and displays it above the question stem.

## Supported JSON Fields

Use **one** of these fields (first non-blank wins):

| Field         | Use case                          |
|---------------|-----------------------------------|
| `imageAsset`  | Primary field (recommended)       |
| `visualAsset` | Alias for graphs/visuals          |
| `graphicAsset`| Alias for graphics               |
| `tableAsset`  | Alias for tables                  |

**Format:** Path relative to the `assets/` folder. Example: `quiz_images/sample_breakfast.png`.

## Example Question

```json
{
  "id": "eng4_visual_demo_001",
  "stem": "[See the picture above.]\n\nIn the picture, the table has milk, bread, cheese and oranges. The family is having breakfast together.\n\nWhat does the picture show?",
  "options": [
    "An empty table.",
    "Only drinks.",
    "A dinner table.",
    "A breakfast table with food and a family eating together."
  ],
  "answerIndex": 3,
  "difficulty": 5,
  "questionType": "visual_interpretation",
  "topic": "food_and_drinks",
  "skills": ["food_and_drinks", "reading", "inference"],
  "explanation": "The picture shows a breakfast table with milk, bread, cheese, oranges and a family eating together.",
  "source": "brainbuddy",
  "sourceRef": "eng4_visual_demo_001",
  "imageAsset": "quiz_images/sample_breakfast.png"
}
```

## Asset Location

Place image files under:

```
app/src/main/assets/quiz_images/
```

Supported formats: PNG, JPG, JPEG, WebP (via Android BitmapFactory).

## Working Demo

- **Demo pack:** `app/src/main/assets/lgs_import/english4/lgs_eng4_visual_demo.json`
- **Sample image:** `app/src/main/assets/quiz_images/sample_breakfast.png`

Run LGS import, then start a quiz (English, grade 4). The demo question will display the image when shown.
