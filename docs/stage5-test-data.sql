USE springboot_demo;

-- Final integration seed data.
-- Login plaintext password for all four users: 123456
-- The password stored below is a BCrypt hash. Do not replace it with plaintext.

SET @demo_password_hash = '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiwvQvBhv81Zr7.cqTwHjqOTgiqKx1e';

INSERT INTO user (username, password, name, age, email, role)
VALUES
('tom', @demo_password_hash, 'Tom', 20, 'tom@example.com', 'USER'),
('alice', @demo_password_hash, 'Alice', 21, 'alice@example.com', 'USER'),
('staff', @demo_password_hash, 'Staff', 28, 'staff@example.com', 'STAFF'),
('admin', @demo_password_hash, 'Admin', 30, 'admin@example.com', 'ADMIN')
ON DUPLICATE KEY UPDATE
password = VALUES(password),
name = VALUES(name),
age = VALUES(age),
email = VALUES(email),
role = VALUES(role);

-- Rebuild only fixed acceptance-test records. Do not use these titles for real data.
DELETE tr
FROM ticket_reply tr
JOIN ticket t ON tr.ticket_id = t.id
WHERE t.title IN ('验收-登录失败', '验收-文件上传失败');

DELETE FROM ticket
WHERE title IN ('验收-登录失败', '验收-文件上传失败');

SET @tom_id = (SELECT id FROM user WHERE username = 'tom');
SET @alice_id = (SELECT id FROM user WHERE username = 'alice');
SET @staff_id = (SELECT id FROM user WHERE username = 'staff');

INSERT INTO ticket (title, content, status, priority, category, user_id)
VALUES
('验收-登录失败', '账号无法登录，重置密码后仍然失败。用于验证 tom 自己可见、staff 可处理、AI 回复建议。', 'OPEN', 'HIGH', 'ACCOUNT', @tom_id),
('验收-文件上传失败', '上传图片时提示格式错误。用于验证 alice 工单不能被 tom 越权访问。', 'OPEN', 'MEDIUM', 'UPLOAD', @alice_id);

SET @tom_ticket_id = (
    SELECT id FROM ticket
    WHERE title = '验收-登录失败' AND user_id = @tom_id
    ORDER BY id DESC
    LIMIT 1
);

INSERT INTO ticket_reply (ticket_id, user_id, content, reply_type)
VALUES
(@tom_ticket_id, @tom_id, '我已经尝试重置密码，但还是无法登录。', 'USER'),
(@tom_ticket_id, @staff_id, '已收到问题，请补充错误截图和发生时间。', 'STAFF');

-- Verification queries.
SELECT id, username, role
FROM user
WHERE username IN ('tom', 'alice', 'staff', 'admin')
ORDER BY username;

SELECT id, title, status, priority, category, user_id
FROM ticket
WHERE title IN ('验收-登录失败', '验收-文件上传失败')
ORDER BY id;

SELECT tr.id, tr.ticket_id, tr.user_id, tr.reply_type, tr.content
FROM ticket_reply tr
WHERE tr.ticket_id = @tom_ticket_id
ORDER BY tr.id;
