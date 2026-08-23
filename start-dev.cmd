@echo off
REM ---------------------------------------------------------------------------
REM  PortfolioPilot AI - one-command dev launcher (Windows).
REM
REM  Double-click this, or run it from cmd/PowerShell:
REM      start-dev.cmd            start everything
REM      start-dev.cmd -Reseed    wipe and re-seed the database
REM      start-dev.cmd -Stop      stop everything
REM
REM  This wrapper exists because Windows blocks .ps1 files by default
REM  ("running scripts is disabled on this system"). Calling PowerShell with
REM  -ExecutionPolicy Bypass applies ONLY to this one invocation - it does not
REM  change any machine or user policy.
REM ---------------------------------------------------------------------------
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1" %*
