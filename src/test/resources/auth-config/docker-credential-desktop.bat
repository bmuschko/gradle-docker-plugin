@echo off

if "%1%" == "get" (
    set /p null=

    echo { "ServerURL":"https://index.docker.io/v1/", "Username":"mac_user", "Secret":"XXX" }
) else if "%1%" == "list" (
    echo { "https://index.docker.io/v1/": "mac_user" }
) else (
    exit 1
)
