SELECT sum(x * 1.0 / 7), sum(x % 27), sum(round(x / 7.0, 2)), sum(abs(689 - x)), max(printf('%.3f', x / 3.0)) FROM (WITH RECURSIVE s(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM s WHERE x < 35039) SELECT x FROM s);
SELECT sum(CAST(printf('%d', x) AS INTEGER)), count(CAST(x AS TEXT)) FROM (WITH RECURSIVE s(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM s WHERE x < 16936) SELECT x FROM s);
