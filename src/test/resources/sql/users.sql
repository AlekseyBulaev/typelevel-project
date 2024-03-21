CREATE TABLE users (
    email text NOT NULL,
    hashedPassword text NOT NULL,
    firstName text,
    lastName text,
    company text,
    role text NOT NULL
);

ALTER TABLE users
ADD CONSTRAINT pk_users PRIMARY KEY (email);

INSERT INTO users (
    email,
    hashedPassword,
    firstName,
    lastName,
    company,
    role
) VALUES (
    'email@mail.com',
    '$2a$10$Sl9N0bw7x1XUab8tJdk5gubNgu4BuDQcXx1nMilWST8EH1h2P5lyu',
    'John',
    'Doe',
    'MAANG',
    'ADMIN'
);

INSERT INTO users (
    email,
    hashedPassword,
    firstName,
    lastName,
    company,
    role
) VALUES (
    'boss@mail.com',
    '$2a$10$6sug9p3tF9gktbuuK8W.JOA//eBes1Vp3W/SXMtgQ/aNQUBDWuuHW',
    'Bill',
    'Gates',
    'Microsoft',
    'RECRUITER'
);