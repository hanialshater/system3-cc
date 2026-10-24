CREATE TABLE t(a INTEGER PRIMARY KEY, b TEXT, c REAL, d INTEGER, e TEXT);
INSERT INTO t(b, c, d, e) SELECT printf('k%07d', (x * 4) % 10701), x * 3.24, x % 91, hex(x) FROM (WITH RECURSIVE s(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM s WHERE x < 10694) SELECT x FROM s);
UPDATE t SET c = c * 1.5 + d WHERE d < 49;
UPDATE t SET e = e || '!' WHERE a % 4 = 0;
DELETE FROM t WHERE d > 80;
SELECT count(*), sum(c), sum(length(e)) FROM t;
