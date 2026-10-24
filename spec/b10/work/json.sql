CREATE TABLE t(a INTEGER PRIMARY KEY, b TEXT, c REAL, d INTEGER, e TEXT);
INSERT INTO t(b, c, d, e) SELECT printf('k%07d', (x * 14) % 4183), x * 1.61, x % 91, printf('%x-%d', x, x % 13) FROM (WITH RECURSIVE s(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM s WHERE x < 4176) SELECT x FROM s);
SELECT sum(json_extract(j, '$.c')), count(j ->> '$.e') FROM (SELECT json_object('b', b, 'c', c, 'e', e, 'l', json_array(a, d)) AS j FROM t);
SELECT length(json_group_array(json_object('a', a, 'b', b))) FROM t;
SELECT count(*) FROM json_each((SELECT json_group_array(d) FROM t)) WHERE value > 77;
