#!/usr/bin/env bash
# Flags G1: reclaim periódico de heap (equivalente ZUncommit do ZGC)
export G1_RECLAIM_FLAGS="-XX:G1PeriodicGCInterval=30000 -XX:+G1PeriodicGCInvokesConcurrent -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30"
