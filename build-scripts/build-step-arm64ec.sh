#!/bin/bash
# build-step-arm64ec.sh
# ARM64EC Android 构建步骤 - 应用补丁并准备构建
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="${WINE_SOURCE_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"

cd "$SOURCE_DIR"

echo "=== 应用 ARM64EC Android 补丁 ==="

# 定义要应用的补丁列表 - 只包含实际存在的补丁
PATCHES=(
    # 主要补丁
    "dlls_winex11_drv_mouse_c.patch"
    "dlls_winex11_drv_window_c.patch"
    "shell32_shlfileop_init_path_components.patch"
    "explorer_startmenu_shutdown_latch.patch"
)

# test-bylaws 补丁
TEST_BYLAWS_PATCHES=(
    "test-bylaws/dlls_wow64_syscall_c.patch"
    "test-bylaws/dlls_ntdll_signal_arm64ec_c.patch"
)

# 定义标记映射表 (补丁名 -> 标记文本 -> 目标文件)
declare -A PATCH_MARKERS
PATCH_MARKERS["test-bylaws/dlls_ntdll_signal_arm64ec_c.patch"]="ARM64EC_NT_XCONTEXT|dlls/ntdll/signal_arm64ec.c"
PATCH_MARKERS["test-bylaws/dlls_wow64_syscall_c.patch"]="Wow64SuspendLocalThread|dlls/wow64/syscall.c"
PATCH_MARKERS["test-bylaws/dlls_wow64_syscall_c.patch"]="Wow64SuspendLocalThread|dlls/wow64/syscall.c"
PATCH_MARKERS["test-bylaws/include_winnt_h.patch"]="XSTATE_ARM64_SVE|include/winnt.h"
PATCH_MARKERS["test-bylaws/include_winternl_h.patch"]="THREAD_CREATE_FLAGS_BYPASS_PROCESS_FREEZE|include/winternl.h"
PATCH_MARKERS["test-bylaws/tools_makedep_c.patch"]="arch_install_dirs|tools/makedep.c"

# 应用普通补丁的函数
apply_patch() {
    local patch_name="$1"
    local patch_file="android/patches/$patch_name"
    
    if [ ! -f "$patch_file" ]; then
        echo "警告: 补丁不存在，跳过: $patch_file"
        return 0
    fi
    
    # 检查是否已经应用 (反向检查)
    if git apply --ignore-whitespace -C1 -R --check "$patch_file" 2>/dev/null; then
        echo "已应用 (跳过): $patch_name"
        return 0
    fi
    
    # 尝试标准应用
    if git apply --ignore-whitespace -C1 --check "$patch_file" 2>/dev/null \
       && git apply --ignore-whitespace -C1 "$patch_file"; then
        echo "已应用: $patch_name"
        return 0
    fi
    
    # 尝试 3-way 合并
    if git apply --3way --ignore-space-change "$patch_file" 2>/dev/null; then
        echo "已应用 (3way): $patch_name"
        return 0
    fi
    
    # 尝试 GNU patch
    if patch -p1 --forward --batch --ignore-whitespace -i "$patch_file" 2>/dev/null; then
        echo "已应用 (patch): $patch_name"
        return 0
    fi
    
    echo "错误: 补丁应用失败: $patch_name"
    return 1
}

# 应用 test-bylaws 补丁的函数 (使用标记检测)
apply_test_bylaws_patch() {
    local patch_name="$1"
    local patch_file="android/patches/$patch_name"
    
    if [ ! -f "$patch_file" ]; then
        echo "警告: test-bylaws 补丁不存在，跳过: $patch_file"
        return 0
    fi
    
    # 获取标记和目标文件
    local marker_info="${PATCH_MARKERS[$patch_name]:-}"
    if [ -z "$marker_info" ]; then
        echo "警告: 未找到标记映射: $patch_name"
        return 0
    fi
    
    local marker="${marker_info%%|*}"
    local target_file="${marker_info#*|}"
    
    # 检查标记是否存在 (如果存在，说明补丁已应用)
    if grep -qF "$marker" "$target_file" 2>/dev/null; then
        echo "已应用 (标记检测跳过): $patch_name"
        return 0
    fi
    
    # 尝试标准应用
    if git apply --ignore-whitespace -C1 --check "$patch_file" 2>/dev/null \
       && git apply --ignore-whitespace -C1 "$patch_file"; then
        echo "已应用: $patch_name"
        return 0
    fi
    
    # 尝试 3-way 合并
    if git apply --3way --ignore-space-change "$patch_file" 2>/dev/null; then
        echo "已应用 (3way): $patch_name"
        return 0
    fi
    
    # 尝试 GNU patch
    if patch -p1 --forward --batch --ignore-whitespace -i "$patch_file" 2>/dev/null; then
        echo "已应用 (patch): $patch_name"
        return 0
    fi
    
    # 反向检查
    if git apply --ignore-whitespace -C1 -R --check "$patch_file" 2>/dev/null; then
        echo "已应用 (反向检查跳过): $patch_name"
        return 0
    fi
    
    echo "错误: test-bylaws 补丁应用失败: $patch_name"
    return 1
}

echo ""
echo "=== 应用核心补丁 ==="
for patch in "${PATCHES[@]}"; do
    apply_patch "$patch" || echo "失败: $patch"
done

echo ""
echo "=== 应用 test-bylaws 补丁 ==="
for patch in "${TEST_BYLAWS_PATCHES[@]}"; do
    apply_test_bylaws_patch "$patch" || echo "失败: $patch"
done

echo ""
echo "=== 修复 IDL 文件问题 ==="
# 移除有问题的 amd_ags_x64 目录
if [ -d "dlls/amd_ags_x64" ]; then
    echo "移除 amd_ags_x64 目录..."
    rm -rf dlls/amd_ags_x64
fi

# 查找并修复所有缺少 @makedep 的 IDL 文件
echo "修复 IDL 文件..."
find dlls -name "*.idl" -type f 2>/dev/null | while read idl_file; do
    if ! grep -q "@makedep" "$idl_file" 2>/dev/null; then
        echo "添加 @makedep 到: $idl_file"
        sed -i '1i/* @makedep(ignore) */' "$idl_file"
    fi
done

echo ""
echo "=== 准备 Wine 构建系统 ==="
./tools/make_requests
./tools/make_specfiles
./tools/make_makefiles
autoreconf -f

# 运行配置状态
if [ -x ./config.status ]; then
    ./config.status
fi

echo ""
echo "=== ARM64EC 补丁应用完成 ==="
