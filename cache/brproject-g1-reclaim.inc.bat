@echo off
REM Flags G1: reclaim periodico de heap (equivalente ZUncommit/ZCollectionInterval do ZGC)
set G1_RECLAIM_FLAGS=-XX:G1PeriodicGCInterval=30000 -XX:+G1PeriodicGCInvokesConcurrent -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30
