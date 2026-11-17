package com.chrono.phantom

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EventStore(context: Context) : SQLiteOpenHelper(context, "phantom_events.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp INTEGER NOT NULL,
            type TEXT NOT NULL,
            source TEXT NOT NULL,
            detail TEXT NOT NULL,
            severity TEXT NOT NULL,
            fingerprint TEXT NOT NULL UNIQUE
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun add(event: PhantomEvent): Boolean {
        val values = ContentValues().apply {
            put("timestamp", event.timestamp); put("type", event.type)
            put("source", event.source); put("detail", event.detail)
            put("severity", event.severity)
            put("fingerprint", "${event.timestamp}|${event.type}|${event.source}|${event.detail}")
        }
        return writableDatabase.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    @Synchronized
    fun recent(limit: Int = 250, since: Long = 0L): List<PhantomEvent> {
        val result = mutableListOf<PhantomEvent>()
        readableDatabase.query("events", arrayOf("id","timestamp","type","source","detail","severity"),
            if (since > 0) "timestamp >= ?" else null,
            if (since > 0) arrayOf(since.toString()) else null,
            null, null, "timestamp DESC", limit.toString()).use { c ->
            while (c.moveToNext()) result += PhantomEvent(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))
        }
        return result
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM events", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun clear() { writableDatabase.delete("events", null, null) }
}
