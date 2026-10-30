# PDF reading and AI handbook

Library and editorial PDF cards have a public **Читать** action. The reader supports page navigation, keyboard arrows, page-number entry, zoom, fit-to-width/page, fullscreen when supported, and local progress per document version. Downloads remain available.

Scanned PDFs render as original page images. Selection works only when the original PDF has a text layer; this feature does not add OCR.

`/api/java-library/{id}/content?version=N` returns inline PDF data. Single byte ranges, suffix and open-ended ranges are supported. Invalid/multiple ranges return 416; stale versions return 409. Repeatable-read transactions prevent replacement/deletion from mixing chunks within a response, and version matching protects across requests. Data is read one storage chunk at a time.

PDF.js 5.6.205 is pinned and bundled in `src/main/resources/pdfjs-5.6.205.zip`. The archive contains legacy main/worker modules, viewer CSS, character maps, standard fonts, WASM decoders and their licenses. Assets are served by exact archive entry names. No CDN, generic viewer, scripting sandbox, or uploaded archive is used. Eval and XFA are disabled. Rendering uses a single visible canvas with bounded backing resolution.

The handbook is supplied as a standalone file in `docs/ai-handbook/AI-Handbook-2026.pdf`. Upload it manually through the administrator’s book catalogue, choosing the AI section. It then uses the same read/download actions as other library books. It is not embedded in the top AI section or served as a separate built-in document.

## Handbook source and rebuilding

The editorial source is `docs/ai-handbook/book.md`. It contains 25 chapters, exercises with answers, a glossary, references and concluding credits. Corporate references have been removed; the last section contains `© 2026 Анцибор А.В.`. The cover uses vector graphics and the site's dark brown, cream and brass palette. Interior pages use warm paper and dark text for sustained reading.

Install ReportLab and DejaVu fonts, then run:

```sh
HANDBOOK_FONT_DIR=/path/to/dejavu python3 docs/ai-handbook/build.py
```

The builder writes `docs/ai-handbook/AI-Handbook-2026.pdf`, including linked contents and outline bookmarks. Body paragraphs, notes, glossary and reference text are justified; headings and the cover retain their designed alignment.

## Validation

- `JavaLibraryTest`: original downloads, public reader/assets, range responses, invalid ranges, version conflicts, and a range crossing the 1 MiB storage boundary.
- `node scripts/test-pdf-reader.mjs`: navigation, bounds, fit-page, rapid changes, keyboard, restored progress, loading failure and retry. This uses a renderer stub; it is not a browser layout test.
- PDF.js 5.6.205 separately rendered an image-only scan and handbook pages 1, 2 and 45 using a Node canvas.
- All 45 handbook pages were rasterized and visually reviewed; text bounds, chapter sequence, author metadata, final copyright and absence of removed corporate references were checked.
- Real browser/mobile/fullscreen verification could not run in this environment: local server binding is denied. It remains a recommended manual check, rather than a claimed passing test.
