@echo off
setlocal
set MVN_VERSION=3.9.9
set MVN_BASE=%USERPROFILE%\.m2\wrapper\dists
set MVN_HOME=%MVN_BASE%\apache-maven-%MVN_VERSION%
if not exist "%MVN_HOME%\bin\mvn.cmd" (
  if not exist "%MVN_BASE%" mkdir "%MVN_BASE%"
  powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VERSION%/apache-maven-%MVN_VERSION%-bin.zip' -OutFile '%TEMP%\maven.zip'; Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%MVN_BASE%' -Force; Remove-Item '%TEMP%\maven.zip'"
)
call "%MVN_HOME%\bin\mvn.cmd" %*
