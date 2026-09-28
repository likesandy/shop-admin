-- Acceptance fixture only; this module is not a dependency of admin-starter.
CREATE TABLE sys_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(64) NOT NULL,
    content VARCHAR(255),
    owner_id BIGINT NOT NULL,
    UNIQUE(owner_id, title),
    FOREIGN KEY(owner_id) REFERENCES sys_user(id)
);
