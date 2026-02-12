-- Initialize roles for test environment (H2 compatible)
-- Using INSERT INTO which works in H2
INSERT INTO roles (id, name) VALUES ('550e8400-e29b-41d4-a716-446655440001', 'ROLE_USER');
INSERT INTO roles (id, name) VALUES ('550e8400-e29b-41d4-a716-446655440002', 'ROLE_MODERATOR');
INSERT INTO roles (id, name) VALUES ('550e8400-e29b-41d4-a716-446655440003', 'ROLE_ADMIN');
