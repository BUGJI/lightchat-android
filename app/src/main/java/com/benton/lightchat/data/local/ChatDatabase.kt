package com.benton.lightchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.benton.lightchat.data.local.entity.ChannelEntity
import com.benton.lightchat.data.local.entity.DraftEntity
import com.benton.lightchat.data.local.entity.MessageEntity
import com.benton.lightchat.data.local.entity.PrivateChatEntity
import com.benton.lightchat.data.local.entity.PrivateContactEntity
import com.benton.lightchat.data.local.entity.SyncCursorEntity
import com.benton.lightchat.data.local.entity.SyncStateEntity

@Database(
    entities = [
        ChannelEntity::class,
        PrivateChatEntity::class,
        MessageEntity::class,
        PrivateContactEntity::class,
        SyncCursorEntity::class,
        SyncStateEntity::class,
        DraftEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        private const val NAME = "lightchat.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN status TEXT NOT NULL DEFAULT 'sent'")
                db.execSQL("ALTER TABLE messages ADD COLUMN clientMsgId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN fileId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN parentPreviewJson TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_messages_ownerKey_conversationType_conversationId_status " +
                        "ON messages (ownerKey, conversationType, conversationId, status)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_messages_ownerKey_status " +
                        "ON messages (ownerKey, status)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_fileUrl ON messages (fileUrl)")

                db.execSQL(
                    "ALTER TABLE sync_cursors ADD COLUMN lastReadMessageId INTEGER NOT NULL DEFAULT 0",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_state (" +
                        "ownerKey TEXT NOT NULL, " +
                        "channelCursor INTEGER NOT NULL, " +
                        "recallCursor INTEGER NOT NULL, " +
                        "privateChatId INTEGER NOT NULL, " +
                        "privateCursor INTEGER NOT NULL, " +
                        "lastSyncAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(ownerKey))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS drafts (" +
                        "ownerKey TEXT NOT NULL, " +
                        "conversationType TEXT NOT NULL, " +
                        "conversationId INTEGER NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "parentId INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(ownerKey, conversationType, conversationId))",
                )
            }
        }

        fun create(context: Context): ChatDatabase =
            Room.databaseBuilder(context.applicationContext, ChatDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
