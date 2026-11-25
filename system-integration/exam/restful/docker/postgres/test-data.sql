-- Forudoprettede ressourcer til HTTP-testene. Kan køres flere gange.
INSERT INTO authors (name)
SELECT 'Anna Jensen'
WHERE NOT EXISTS (SELECT 1 FROM authors WHERE name = 'Anna Jensen');

INSERT INTO categories (name)
SELECT 'Programmering'
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Programmering');
