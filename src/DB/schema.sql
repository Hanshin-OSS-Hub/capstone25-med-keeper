DROP DATABASE IF EXISTS pillbot;
CREATE DATABASE pillbot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pillbot;

CREATE TABLE user (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS drug (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) UNIQUE,
    name VARCHAR(255),
    manufacturer VARCHAR(255),
    efficacy TEXT,
    use_method TEXT,
    warning TEXT,
    precaution TEXT,
    interaction TEXT,
    side_effect TEXT,
    storage TEXT,
    openDe DATETIME,
    updateDe DATETIME,
    bizrno VARCHAR(50),
    image_url VARCHAR(500),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE favorite (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    drug_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (drug_id) REFERENCES drug(id) ON DELETE CASCADE,
    UNIQUE (user_id, drug_id)
);

CREATE TABLE log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    action VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL
);

ALTER TABLE drug
    MODIFY COLUMN name TEXT
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;
ALTER TABLE drug
    MODIFY COLUMN manufacturer TEXT
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE drug 
    ADD COLUMN composition TEXT NULL,
    ADD COLUMN additives TEXT NULL,
    ADD COLUMN appearance TEXT NULL,
    ADD COLUMN shape TEXT NULL;
    
    DESC drug;
    
    

        


