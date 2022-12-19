@echo off

if not "%1%" == "get" (
  exit 1
)

set /p null=

@echo credentials not found in native keychain
