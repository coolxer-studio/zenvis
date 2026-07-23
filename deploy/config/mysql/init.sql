-- 创建数据库
CREATE DATABASE IF NOT EXISTS zenvis CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户并设置密码
CREATE USER IF NOT EXISTS 'zenvis'@'%' IDENTIFIED BY 'REeyWy5rC7NWtZZGtXwR';

-- 授权
GRANT ALL PRIVILEGES ON zenvis.* TO 'zenvis'@'%';

-- 刷新权限
FLUSH PRIVILEGES;
