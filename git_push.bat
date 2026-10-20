@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

echo ========================================
echo        Git Push
echo ========================================
echo.
echo Текущая папка:
echo %CD%
echo.

set /p "GIT_URL=Введи ссылку на Git репозиторий: "
if "%GIT_URL%"=="" (
echo.
echo Ошибка: ссылка не введена.
pause
exit /b 1
)

echo.
set /p "COMMIT_NAME=Введи имя коммита: "
if "%COMMIT_NAME%"=="" (
echo.
echo Ошибка: имя коммита не введено.
pause
exit /b 1
)

echo.
echo ========================================
echo Выполняю Git команды...
echo ========================================
echo.

git init
if errorlevel 1 goto error

git add -A
if errorlevel 1 goto error

git commit -m "%COMMIT_NAME%"
if errorlevel 1 goto error

git branch -M main
if errorlevel 1 goto error

git remote remove origin >nul 2>&1
git remote add origin "%GIT_URL%"
if errorlevel 1 goto error

git push -f origin main
if errorlevel 1 goto error

echo.
echo ========================================
echo       Готово! Успешно отправлено.
echo ========================================
pause
exit /b 0

:error
echo.
echo ========================================
echo       ОШИБКА при выполнении Git.
echo ========================================
pause
exit /b 1