CREATE TABLE IF NOT EXISTS sources (
    id BIGINT PRIMARY KEY,
    url VARCHAR(2048) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS reviews (
    source_id BIGINT NOT NULL,
    external_id VARCHAR(2048) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    author VARCHAR(1024) NOT NULL,
    title VARCHAR(2048),
    content CLOB NOT NULL,
    rating DECIMAL(10, 4) NOT NULL,
    published_at DATE NOT NULL,
    review_position INT NOT NULL,

    PRIMARY KEY (source_id, external_id),

    CONSTRAINT fk_reviews_source
        FOREIGN KEY (source_id)
        REFERENCES sources(id)
);
