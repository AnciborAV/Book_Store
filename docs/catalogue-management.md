# Administrator library management

Sign in as ADMIN. In **Личный кабинет**, choose **Управление книгами и разделами**, or use the management toolbar above the catalogue.

## Sections

**Управлять разделами** creates, renames and orders sections. A section has a stable identifier separate from its display name, so renaming does not move or lose its books, files or favourites. Lower order numbers appear first. A section can be deleted after its books have been moved or deleted.

The existing catalogue sections, Java, civil law and AI are imported once. Their books appear in one catalogue with shared search and favourites. The upper AI atlas remains an independent feature.

## Books, covers and files

**Добавить книгу +** accepts a title, author, section and optional description, cover and PDF. Both files are optional: a cover-only card can be published first. Download a cover image you have chosen and upload its JPG/PNG file through the form. Images are uploaded as files; arbitrary remote image URLs are not fetched by the server.

Later, choose **Редактировать книгу** on the same card and upload its PDF. The card ID, chosen cover, section, favourites and collection membership are retained. If no custom cover was selected, the first PDF page becomes the cover. Upload a new cover to replace it. PDF files must be genuine unencrypted PDFs up to 500 MiB; covers use the existing 2 MiB image validation. Original PDFs are not rewritten.

Existing PDF books can also be moved between any sections and have their metadata, PDF or cover replaced. Card, description and collection controls apply to both legacy PDF books and ordinary catalogue books. The catalogue only shows **Читать** and **Скачать PDF** after a file exists. Both actions are public; the reader retains page/range/version handling.

For ordinary catalogue cards, **Убрать PDF** removes the file while retaining the card and its cover. For legacy PDF-only books, delete the card to remove the PDF.

## Collections

Use **Мои подборки → Управлять подборками**, or **Выбор редакции / комментарий** on a card. Enter your own list names, one per line, to group books from different sections. A book can belong to several collections. Remove a list name to remove the book from that collection; **Убрать из всех подборок** removes its recommendation membership. The existing description, comment and homepage-priority controls remain available. Attaching a PDF does not duplicate a book in collections.

## Deletion and history

**Удалить книгу** removes it from the public catalogue, recommendations, current carts/favourites and removes its uploaded PDF/cover. Already downloaded user copies cannot be removed from their devices. Legacy PDF records are deleted with their files. Catalogue records with historical order/stock references are retained privately as archived rows; historical order snapshots and stock movement names remain readable and outstanding orders can still be cancelled or shipped. The active title is freed so a new card can later reuse it.

Writes require ADMIN and CSRF. Book/file and section edits use versions, and a stale edit or delete returns a conflict. Text is rendered as text, not HTML. File processing happens before the write transaction; database updates, file links and membership cleanup commit together. Existing uploaded PDF blocks and safe image processing are reused.

## Implementation and verification

`store_sections` stores stable mappings to legacy category keys. `catalog_pdf` attaches a PDF record to an existing catalogue card without publishing a second card. `catalog_covers` preserves manually selected covers separately from the PDF. Sections, covers and PDFs live in the existing database and survive application restarts with that database.

- CatalogueManagementTest checks a cover-only card followed by PDF attachment, cover/collection/identity preservation, public read/download, no duplicate PDF card, rename/move/delete sections, moving legacy PDFs, detaching a PDF, stale versions, access controls, bad files, and deleting/recreating a title while preserving order history/cancellation.
- JavaLibraryTest, EditorialTest, CommerceTest and SelectionTest verify the existing library, recommendations, orders and selections.
- `node scripts/test-catalogue-manager.mjs` checks the editor's cover-first/PDF-later payloads, section selection, limits and role/logout behavior in a minimal DOM fixture. It is not a browser layout test.
- PostgreSQL/Testcontainers and real mobile/browser layout checks require infrastructure unavailable in the current execution environment.


## Цена и продажа книги

В форме «Добавить книгу» / «Редактировать книгу» поле «Цена, ₽» принимает сумму от 0,01 до 99 999 999,99 с точностью до копеек. Пустое поле убирает книгу из продажи. После сохранения цены карточка показывает стоимость и кнопку «В корзину +» для покупателей и администратора. Остаток задаётся отдельно: личный кабинет → «Управление магазином» → «Поступление на склад». Без доступного остатка оформить заказ нельзя.

Цена в оформленных заказах сохраняется независимо от последующих изменений. Корзина оформляется по текущей цене; после удаления цены оформление блокируется. Ранее загруженный отдельный PDF при первом назначении цены связывается с карточкой каталога: файл и обложка сохраняются, описание, подборки и избранное переносятся, дубликата в каталоге нет. Дальнейшие изменения цены доступны в этой карточке.

Указание цены использует существующий процесс заказа и не закрывает публичное чтение/скачивание PDF. Платный доступ к файлам и онлайн-оплата этим изменением не добавляются.
