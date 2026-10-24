CREATE TABLE t(a INTEGER PRIMARY KEY, b TEXT, c REAL, d INTEGER, e TEXT);
INSERT INTO t(b, c, d, e) SELECT printf('k%07d', (x * 16) % 13129), x * 2.98, x % 91, hex(x) FROM (WITH RECURSIVE s(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM s WHERE x < 13122) SELECT x FROM s);
SELECT count(*) FROM t WHERE b LIKE '%42%';
SELECT count(*) FROM t WHERE e GLOB '*f*';
SELECT sum(length(replace(upper(e), 'A', 'xyz'))), sum(instr(b, '1')) FROM t;
SELECT count(DISTINCT substr(b, 2, 4) || lower(e)) FROM t;
