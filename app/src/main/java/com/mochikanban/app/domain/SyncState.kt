package com.mochikanban.app.domain

enum class SyncState { IDLE, PENDING_PUSH, PUSHING, CONFLICT, ERROR }

enum class OpType { CREATE, UPDATE, DELETE }
