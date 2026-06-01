package com.mochikanban.app.sync.engine

import com.mochikanban.app.domain.Column

object ColumnMapper {
    const val KEY_COLUMN = "column"
    const val KEY_COLOR_TAG = "colorTag"
    const val KEY_APP_VERSION = "appVersion"

    fun fromExtended(value: String?): Column = when (value?.uppercase()) {
        "TODO" -> Column.TODO
        "DOING" -> Column.DOING
        "DONE" -> Column.DONE
        else -> Column.TODO
    }

    fun toExtended(column: Column): String = column.name
}
