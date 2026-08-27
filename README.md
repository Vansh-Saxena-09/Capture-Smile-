# Capture Smile

*Every smile has a character.*

A local web app that looks at your smile and casts you as a fictional character. Take a photo, get a
score out of ten, a vibe, a one-line read on your grin, and the character you match — plus a film of
theirs. Gather friends and it becomes a Smile Showdown: an overall ranking and six superlative awards,
so second place still wins something.

Written in plain Java with no dependencies, no build tool, and no framework. One `javac` call compiles
it; the HTTP server is the one that ships in the JDK.

## Running it

Requires **JDK 21 or newer** (the server uses virtual threads).

```
run.bat
```

Then open <https://capture-smile-backend.onrender.com>. To use a different port:

```
set PORT=9000 && run.bat
```

`run.bat` calls `build.bat` first, so there is no separate build step. If you want to compile without
running:

```
build.bat
```

The `-encoding UTF-8` flag in `build.bat` is not optional — the sources contain emoji and em-dashes,
and a default Windows (Cp1252) `javac` silently mangles them.

## How it works

The server binds to `127.0.0.1` only. That keeps the Windows firewall quiet and, more importantly,
means the browser treats the page as a secure context, which is what `getUserMedia` needs to open the
camera without HTTPS.

Two endpoints, both POST, both deliberately parser-free:

| Endpoint | Body | Returns |
| --- | --- | --- |
| `/api/analyze` | raw image bytes | the full analysis as JSON |
| `/api/showdown` | tab-delimited squad rows | ranking + awards as JSON |

Everything else is served as static files out of `web/`.

### The analysis is deterministic, not machine learning

There is no ML model here. `SmileAnalyzer` hashes the image bytes with SHA-256, seeds a
`SplittableRandom` from the first eight bytes of that digest, and draws every value from it in a fixed
order: score, then the five dimensions (radiance, confidence, playfulness, mystery, warmth), then
vibe, description, character ranking, and film.

Two consequences worth knowing:

The same photo always produces the same result, which is what makes the friends comparison fair —
nobody gets a luckier roll than anybody else. And the draw order is load-bearing. Reordering those
lines would silently change every existing result, so it is frozen on purpose.

Scores are bounded to 7.0–9.9 by construction. The app is never insulting.

### Nothing about your face is stored

`/api/analyze` reads the image into memory, hashes it, and lets it fall out of scope. It is never
written to disk and never logged.

The showdown goes further: its request body carries only already-derived numbers — name, score, match
percentage, vibe, character, and the five dimension values. No photo crosses the wire for a showdown
at all, so the server never sees a second face.

## Characters and images

Sixteen characters across Marvel, Harry Potter, Stranger Things, Wednesday, Pixar/Disney, and Hindi
cinema, tagged with vibes and spread across the five dimensions so different smiles surface different
faces.

Each card can show two pictures: a round chip with the performer's own portrait, and a large film-frame
plate with an in-costume still. All sixteen have a portrait. Only three have an in-costume still, and
that is a licensing wall rather than an omission — studio publicity stills are non-free, so there is no
copyable file for the other thirteen. Those cards render a designed CSS plate instead: duotone gradient
from the character's own colours, film sprockets, universe wordmark, monogram, and glyph.

Adding an image means dropping a correctly-named file into a folder. No Java edit, no rebuild, no
restart — `ImageAssets` resolves paths from the character id and the server checks the folder per
request. See [IMAGES.md](IMAGES.md) for the ids and the `ImagePrep` crop tool.

## Optional TMDB key

Movie posters render as styled placeholders by default. A [TMDB](https://www.themoviedb.org/) API key
fills them in for real: put it in `secrets/tmdb.key`, one line, no quotes.

That folder sits beside the web root rather than inside it, so `StaticHandler` cannot serve it and no
browser bundle can carry it. It is also gitignored. TMDB's image CDN needs no key to *serve* a poster,
only to *look up* its path, so the key stays server-side.

## Layout

```
capturesmile/     the whole server — handlers, analyzer, catalog, JSON writer
web/              index.html plus static css/js/img
tools/            standalone helpers (ImagePrep, a few parse tests)
secrets/          API keys — gitignored, never served
assets-raw*/      original images before cropping — outside the web root
build.bat run.bat  compile / compile-and-run
```

`Json.java` and `JsonView.java` are a hand-rolled JSON writer. There is no JSON *reader* anywhere,
because no request body is JSON — that is what keeps the dependency count at zero.
