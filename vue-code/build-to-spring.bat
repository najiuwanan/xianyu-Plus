@echo off
echo ========================================
echo 正在构建 Vue 项目并部署到 Spring Boot...
echo ========================================

echo.
echo [1/3] 清理旧的构建文件...
if exist "..\src\main\resources\static" (
    rmdir /s /q "..\src\main\resources\static"
    echo 已清理旧文件
)

echo.
echo [2/3] 构建 Vue 项目...
call npm run build

if %errorlevel% neq 0 (
    echo.
    echo ❌ 构建失败！
    pause
    exit /b %errorlevel%
)

echo.
echo [3/3] 验证构建结果...
if exist "..\src\main\resources\static\index.html" (
    echo ✅ 构建成功！
    echo.
    echo 文件已部署到: src/main/resources/static/
    echo.
    echo 现在可以启动 Spring Boot 应用，访问 http://localhost:8080
) else (
    echo ❌ 构建文件未找到！
)

echo.
echo ========================================
echo 构建完成
echo ========================================
pause
