# Images

Two pictures can appear on a character card, and they answer different questions:

| Folder | What it is | Where it shows |
| --- | --- | --- |
| `web/static/img/chars/` | the character, in costume, in role | the big film-frame plate |
| `web/static/img/actors/` | the performer's own portrait | the round chip in front of the character's name |

## Adding one

Name the file after the character id and drop it in. Nothing else — no Java edit, no rebuild, no
restart. The server checks the folder on each request, so a new file shows up on the next reveal.

```bash
java -cp out-tools ImagePrep assets-raw-chars web/static/img/chars
```

Put originals in `assets-raw-chars/` (kept outside the web root, so they are never served), named
`<id>.jpg`. `ImagePrep` centre-crops to 360×480 with a slight upward bias so faces sit high in the
frame, and writes JPEGs at quality 0.86. `.jpeg`, `.png` and `.webp` are accepted too, in that
order of preference after `.jpg`.

## Character ids

| id | character | chars/ |
| --- | --- | --- |
| `spider-man` | Spider-Man | — |
| `iron-man` | Iron Man | — |
| `wednesday-addams` | Wednesday Addams | ✓ |
| `harry-potter` | Harry Potter | ✓ |
| `hermione-granger` | Hermione Granger | — |
| `star-lord` | Star-Lord | — |
| `steve-harrington` | Steve Harrington | — |
| `eleven` | Eleven | — |
| `munna-bhai` | Munna Bhai | — |
| `geet` | Geet | — |
| `po` | Po | — |
| `elsa` | Elsa | — |
| `moana` | Moana | — |
| `jack-sparrow` | Jack Sparrow | ✓ |
| `loki` | Loki | — |
| `merida` | Merida | — |

All 16 have an `actors/` portrait. Three have an in-character still.

## Why only three

In-costume stills for these roles are studio publicity material. Wikipedia displays them under fair
use and Wikimedia Commons will not host them, so there is no freely-licensed file to copy. The three
that exist came from Commons: a 1964 public-domain photo, and two free-licensed in-costume shots.

A card with no still is not broken. It draws the CSS character plate instead — duotone gradient from
the character's own two colours, film sprockets down both edges, the universe wordmark, a large
monogram, and the character's glyph. That is a designed visual, not a placeholder.

## Movie posters

`Movie.posterPath` is null for every entry, so movie cards render as styled poster placeholders. A
TMDB API key would fill these in properly — TMDB's image CDN needs no key to *serve* a poster, only
to *look up* its path. Set the path and `JsonView.posterUrl` builds the URL; no frontend change
needed. The same key exposes per-film stills and cast images, which is the only realistic route to
licensed in-character pictures for the other thirteen.
