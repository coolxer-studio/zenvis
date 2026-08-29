#!/bin/bash

# ============================================
# Vectum 测试数据上传脚本
# 用于创建测试数据文件并上传到 SFTP 服务
# ============================================

set -e

# 配置参数
LOOP_COUNT=${1:-50}  # 循环次数，默认50条数据
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DATA_FILE="test_data_${TIMESTAMP}.txt"

# SFTP 配置
SFTP_HOST="localhost"
SFTP_PORT="2222"
SFTP_USER="vectum"
SFTP_PASS="vectum123"
SFTP_UPLOAD_DIR="upload"

# 测试数据模板
NAMES=("张三" "李四" "王五" "赵六" "钱七" "孙八" "周九" "吴十" "郑十一" "王十二")
AGES=(20 21 22 23 24 25 26 27 28 29 30)
SCHOOLS=("清华大学" "北京大学" "复旦大学" "上海交大" "浙江大学" "南京大学" "中国科大" "人民大学")
MAJORS=("信息工程专业" "计算机科学" "软件工程" "电子工程" "自动化" "通信工程" "数据科学" "人工智能")

echo "============================================"
echo "  Vectum 测试数据上传脚本"
echo "============================================"
echo "  循环次数: $LOOP_COUNT"
echo "  输出文件: $DATA_FILE"
echo "============================================"

# 创建测试数据文件
echo ""
echo "[1/2] 创建测试数据文件..."

# 清空或创建文件
> "$DATA_FILE"

# 循环生成测试数据
for ((i=0; i<LOOP_COUNT; i++)); do
    # 随机选择数据
    name=${NAMES[$((RANDOM % ${#NAMES[@]}))]}
    age=${AGES[$((RANDOM % ${#AGES[@]}))]}
    school=${SCHOOLS[$((RANDOM % ${#SCHOOLS[@]}))]}
    major=${MAJORS[$((RANDOM % ${#MAJORS[@]}))]}
    
    # 写入文件（格式：姓名|年龄|学校|专业）
    echo "${name}|${age}|${school}|${major}" >> "$DATA_FILE"
done

echo "✓ 测试数据文件已创建: $DATA_FILE"
echo "文件内容:"
cat "$DATA_FILE"

# 上传到 SFTP
echo ""
echo "[2/2] 上传数据到 SFTP 服务..."

# 使用 heredoc 方式传递命令给 sftp
sftp -o StrictHostKeyChecking=no -P "$SFTP_PORT" "$SFTP_USER@$SFTP_HOST" << EOF
cd $SFTP_UPLOAD_DIR
put $DATA_FILE
bye
EOF

echo "✓ 数据文件已成功上传到 SFTP"

echo ""
echo "============================================"
echo "  操作完成！"
echo "  数据文件: $DATA_FILE"
echo "  记录条数: $LOOP_COUNT"
echo "  已上传到 SFTP 服务的 upload 目录"
echo "============================================"