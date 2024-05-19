CREATE TABLE jobs(
    id UUID DEFAULT gen_random_uuid(),
    date bigint NOT NULL,
    ownerEmail text NOT NULL,
    company text NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    externalUrl text NOT NULL,
    remote boolean NOT NULL DEFAULT false,
    location text,
    salaryLow integer,
    salaryHigh integer,
    currency text,
    country text,
    tags text[],
    image text,
    seniority text,
    other text,
    active boolean NOT NULL DEFAULT false
);

ALTER TABLE jobs
ADD CONSTRAINT pk_jobs PRIMARY KEY (id);

INSERT INTO Jobs(
    id,
    date,
    ownerEmail,
    company,
    title,
    description,
    externalUrl,
    remote,
    location,
    salaryLow,
    salaryHigh,
    currency,
    country,
    tags,
    image,
    seniority,
    other,
    active
) VALUES (
    '843df718-ec6e-4d49-9289-f799c0f40064',
    1659186086,
    'email@mail.com',
    'Awesome Company',
    'Tech Lead',
    'An awesome job in Berlin',
    'https://rockthejvm.com/awesomejob',
    false,
    'Berlin',
    2000,
    3000,
    'EUR',
    'Germany',
    ARRAY['scala', 'scala-3', 'cats'],
    NULL,
    'Senior',
    NULL,
    false
);