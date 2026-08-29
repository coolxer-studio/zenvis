CREATE DATABASE IF NOT EXISTS vectum;

CREATE TABLE IF NOT EXISTS vectum.file_data
(
    id UInt64 DEFAULT rowNumberInAllBlocks(),
    name String,
    age UInt8,
    school String,
    major String,
    insert_time DateTime DEFAULT now()
)
ENGINE = MergeTree()
ORDER BY id
SETTINGS index_granularity = 8192;