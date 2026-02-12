-- Initialize roles for test environment (H2 compatible)
-- Using MERGE INTO which works in H2
MERGE INTO roles KEY(name) VALUES ('550e8400-e29b-41d4-a716-446655440001', 'ROLE_USER');
MERGE INTO roles KEY(name) VALUES ('550e8400-e29b-41d4-a716-446655440002', 'ROLE_MODERATOR');
MERGE INTO roles KEY(name) VALUES ('550e8400-e29b-41d4-a716-446655440003', 'ROLE_ADMIN');

