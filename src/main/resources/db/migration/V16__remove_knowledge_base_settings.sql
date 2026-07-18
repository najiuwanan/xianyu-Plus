DELETE FROM xianyu_sys_setting
WHERE setting_key IN (
    'ai_embedding_api_key',
    'ai_embedding_base_url',
    'ai_embedding_model',
    'similarity_threshold'
);
