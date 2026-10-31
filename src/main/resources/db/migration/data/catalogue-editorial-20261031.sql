-- One-time, exact-key updates. Preserve all nonempty descriptions and unrelated fields.
UPDATE book_editorial SET description=(SELECT s.text FROM catalogue_description_seed s JOIN books b ON b.name=s.title AND b.author=s.author WHERE b.id=book_editorial.catalog_id), version=version+1
 WHERE catalog_id IN (SELECT b.id FROM books b JOIN catalogue_description_seed s ON b.name=s.title AND b.author=s.author WHERE NOT b.archived)
 AND TRIM(REPLACE(REPLACE(REPLACE(COALESCE(description,''),CHR(10),''),CHR(13),''),CHR(9),''))='';
INSERT INTO book_editorial(catalog_id,description)
 SELECT b.id,s.text FROM books b JOIN catalogue_description_seed s ON b.name=s.title AND b.author=s.author
 WHERE NOT b.archived AND NOT EXISTS (SELECT 1 FROM book_editorial e WHERE e.catalog_id=b.id);
UPDATE book_editorial SET description=(SELECT s.text FROM catalogue_description_seed s JOIN java_library b ON b.title=s.title AND b.author=s.author WHERE b.id=book_editorial.library_id), version=version+1
 WHERE library_id IN (SELECT b.id FROM java_library b JOIN catalogue_description_seed s ON b.title=s.title AND b.author=s.author WHERE TRIM(REPLACE(REPLACE(REPLACE(COALESCE(b.description,''),CHR(10),''),CHR(13),''),CHR(9),''))='')
 AND TRIM(REPLACE(REPLACE(REPLACE(COALESCE(description,''),CHR(10),''),CHR(13),''),CHR(9),''))='';
INSERT INTO book_editorial(library_id,description)
 SELECT b.id,s.text FROM java_library b JOIN catalogue_description_seed s ON b.title=s.title AND b.author=s.author
 WHERE TRIM(REPLACE(REPLACE(REPLACE(COALESCE(b.description,''),CHR(10),''),CHR(13),''),CHR(9),''))='' AND NOT EXISTS (SELECT 1 FROM book_editorial e WHERE e.library_id=b.id)
 AND NOT EXISTS (SELECT 1 FROM catalog_pdf p WHERE p.library_id=b.id);
UPDATE books SET cover_title=COALESCE(cover_title,name),
 name=(SELECT s.new_title FROM catalogue_metadata_seed s WHERE s.old_title=books.name AND s.old_author=books.author),
 author=(SELECT s.new_author FROM catalogue_metadata_seed s WHERE s.old_title=books.name AND s.old_author=books.author),
 management_revision=management_revision+1
 WHERE NOT archived AND EXISTS (SELECT 1 FROM catalogue_metadata_seed s WHERE s.old_title=books.name AND s.old_author=books.author);
UPDATE java_library SET
 title=(SELECT s.new_title FROM catalogue_metadata_seed s WHERE s.old_title=java_library.title AND s.old_author=java_library.author),
 author=(SELECT s.new_author FROM catalogue_metadata_seed s WHERE s.old_title=java_library.title AND s.old_author=java_library.author), version=version+1
 WHERE EXISTS (SELECT 1 FROM catalogue_metadata_seed s WHERE s.old_title=java_library.title AND s.old_author=java_library.author);
DROP TABLE catalogue_description_seed;
DROP TABLE catalogue_metadata_seed;
