# Editing “О магазине”

Sign in as an administrator. Open **Личный кабинет → Редактировать «О магазине»**, or use **Редактировать раздел** directly in the section.

The editor supports the heading, introduction, story, signature, catalogue button label and accessible photo description. Separate story paragraphs with a blank line. Empty fields are hidden. **Очистить весь текст** clears the draft fields; it takes effect only after saving. Cancel discards the draft.

Replace or remove the portrait and either decorative background separately. PNG/JPEG files may be up to 5 MiB, 6000 pixels per dimension and 20 million pixels in total. The server checks actual image contents, normalizes EXIF orientation, limits the long edge to 1600 pixels, strips metadata and stores a PNG in the database. The portrait retains its full aspect ratio; decorative backgrounds keep the existing cover-style layout.

Changes appear for visitors after **Сохранить изменения**. If another window saved first, the editor reports a conflict and preserves the draft. Reopen it to load the current version. Removing all text and the portrait hides the public section and its navigation link; the profile editor remains available to administrators.

The Liquibase migration seeds the existing text and initial image settings once. Later deployments do not overwrite edited content. Metadata and uploaded images are stored in `site_about` and `site_about_images` in the existing database. Removing a photo clears its uploaded database bytes and makes its section API return 404. Original bundled artwork files remain repository assets.

Public content is served by GET `/api/about`; public images by GET `/api/about/images/{slot}`. Editor GET and multipart POST `/api/admin/about` require ADMIN. Mutations require CSRF. Updates use an optimistic version and a transaction covering text and all image changes. Text is displayed through textContent, never interpreted as HTML. Initial personal text is not duplicated in index.html, so removed text does not reappear as a stale HTML fallback.

Validation: AboutSectionTest covers access control/CSRF, each text field, complete deletion, all image slots, invalid/oversized images, dimension bounds, resize, EXIF rotation, persistence and version conflicts. `node scripts/test-about-editor.mjs` checks editor behavior with a minimal DOM fixture: roles, literal text, draft cancellation, failed-save preservation, deletion and logout. This is a behavior check, not a real browser layout test. Browser/mobile layout testing was unavailable in this execution environment.
