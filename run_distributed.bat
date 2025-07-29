@echo off
setlocal

:: Path to Jsoup JAR in Maven local repo
set JSOUP_JAR=C:\Users\vanja\.m2\repository\org\jsoup\jsoup\1.18.3\jsoup-1.18.3.jar

:: Path to MPJ installation (adjust if needed)
set MPJ_HOME=C:\path\mpj
set MPJ_JAR=%MPJ_HOME%\lib\mpj.jar

:: Run the distributed crawler using MPJ Express
%MPJ_HOME%\bin\mpjrun.bat -np 3 -cp "target\classes;%JSOUP_JAR%;%MPJ_JAR%" distributed.DistributedCrawler
