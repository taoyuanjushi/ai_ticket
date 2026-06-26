USE springboot_demo;

-- Final integration seed data.
-- Login plaintext password for all users below: 123456
-- The stored password is a BCrypt hash. Do not replace it with plaintext.

SET @demo_password_hash = '$2a$10$s/qUxv6dcdxtz5brnEgJzexgVP0IbUhkHF3J5D6zX.6FYO5h6Bk1i';

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
WHERE t.title IN ('ACCEPTANCE-login-failure', 'ACCEPTANCE-upload-failure');

DELETE FROM ticket
WHERE title IN ('ACCEPTANCE-login-failure', 'ACCEPTANCE-upload-failure');

SET @tom_id = (SELECT id FROM user WHERE username = 'tom');
SET @alice_id = (SELECT id FROM user WHERE username = 'alice');
SET @staff_id = (SELECT id FROM user WHERE username = 'staff');

INSERT INTO ticket (title, content, status, priority, category, assigned_to, user_id)
VALUES
('ACCEPTANCE-login-failure', 'User cannot log in after password reset. Used for tom visibility, staff handling, and AI reply suggestion acceptance.', 'OPEN', 'HIGH', 'ACCOUNT_LOGIN', @staff_id, @tom_id),
('ACCEPTANCE-upload-failure', 'Image upload reports an invalid format. Used to verify tom cannot access alice ticket data.', 'OPEN', 'MEDIUM', 'FILE_UPLOAD', NULL, @alice_id);

SET @tom_ticket_id = (
    SELECT id FROM ticket
    WHERE title = 'ACCEPTANCE-login-failure' AND user_id = @tom_id
    ORDER BY id DESC
    LIMIT 1
);

INSERT INTO ticket_reply (ticket_id, user_id, content, reply_type)
VALUES
(@tom_ticket_id, @tom_id, 'I tried resetting the password, but I still cannot log in.', 'USER'),
(@tom_ticket_id, @staff_id, 'Received. Please provide a screenshot and the failure time.', 'STAFF');

-- Verification queries.
SELECT id, username, role
FROM user
WHERE username IN ('tom', 'alice', 'staff', 'admin')
ORDER BY username;

SELECT id, title, status, priority, category, assigned_to, user_id
FROM ticket
WHERE title IN ('ACCEPTANCE-login-failure', 'ACCEPTANCE-upload-failure')
ORDER BY id;

SELECT tr.id, tr.ticket_id, tr.user_id, tr.reply_type, tr.content
FROM ticket_reply tr
WHERE tr.ticket_id = @tom_ticket_id
ORDER BY tr.id;
