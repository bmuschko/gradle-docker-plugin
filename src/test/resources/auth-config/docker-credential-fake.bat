@echo off

if "%1%" == "get" (
    set /p null=

    echo { "ServerURL": "url", "Username": "username", "Secret": "secret" }
) else if "%1%" == "list" (
    echo { "registry.example.com": "username" }
) else (
    exit 1
)
