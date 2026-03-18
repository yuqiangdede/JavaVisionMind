@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0download-models.ps1" %*
