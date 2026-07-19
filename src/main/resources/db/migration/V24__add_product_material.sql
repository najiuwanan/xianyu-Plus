CREATE TABLE xianyu_product_material (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_name VARCHAR(120) NOT NULL,
    title VARCHAR(60) NOT NULL,
    description TEXT NULL,
    price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    original_price DECIMAL(10,2) NULL,
    quantity INT NOT NULL DEFAULT 1,
    delivery_mode VARCHAR(20) NOT NULL DEFAULT 'FREE',
    post_fee DECIMAL(10,2) NULL,
    images_json TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_product_material_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
