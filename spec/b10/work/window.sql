CREATE TABLE t(a INTEGER PRIMARY KEY, b TEXT, c REAL, d INTEGER, e TEXT);
INSERT INTO t(b, c, d, e) SELECT printf('k%07d', (x * 16) % 8305), x * 2.16, x % 91, substr('abcdefghijklmnopqrstuvwxyz', 1 + x % 26, 7) FROM (WITH RECURSIVE s(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM s WHERE x < 8298) SELECT x FROM s);
SELECT sum(r) FROM (SELECT row_number() OVER (PARTITION BY d ORDER BY c DESC) AS r FROM t);
SELECT max(s) FROM (SELECT sum(c) OVER (ORDER BY a ROWS BETWEEN 20 PRECEDING AND CURRENT ROW) AS s FROM t);
SELECT count(*) FROM (SELECT lag(b) OVER (ORDER BY b) AS p, b FROM t) WHERE p < b;
