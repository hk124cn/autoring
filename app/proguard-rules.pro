# 保留 Room 数据库与数据实体
-keep class com.example.autoring.data.* { *; }
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *

# 保留数据模型（防止混淆后序列化问题）
-keep class com.example.autoring.model.** { *; }
