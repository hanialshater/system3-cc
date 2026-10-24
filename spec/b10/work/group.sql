CREATE TABLE t(a INTEGER PRIMARY KEY, b TEXT, c REAL, d INTEGER, e TEXT);
INSERT INTO t(b, c, d, e) SELECT printf('k%07d', (x * 11) % 18353), x * 1.39, x % 91, hex(x) FROM (WITH RECURSIVE s(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM s WHERE x < 18346) SELECT x FROM s);
SELECT d, count(*), sum(c), avg(c), min(b), max(e) FROM t GROUP BY d ORDER BY d LIMIT 5;
SELECT d % 7, group_concat(a % 10, '') IS NOT NULL, total(c) FROM t GROUP BY 1 ORDER BY 1;
