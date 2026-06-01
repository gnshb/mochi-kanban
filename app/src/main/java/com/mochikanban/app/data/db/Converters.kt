package com.mochikanban.app.data.db

import androidx.room.TypeConverter
import com.mochikanban.app.domain.Column
import com.mochikanban.app.domain.OpType
import com.mochikanban.app.domain.SyncState

class Converters {
    @TypeConverter fun fromColumn(v: Column): String = v.name
    @TypeConverter fun toColumn(v: String): Column = Column.valueOf(v)

    @TypeConverter fun fromSyncState(v: SyncState): String = v.name
    @TypeConverter fun toSyncState(v: String): SyncState = SyncState.valueOf(v)

    @TypeConverter fun fromOpType(v: OpType): String = v.name
    @TypeConverter fun toOpType(v: String): OpType = OpType.valueOf(v)
}
