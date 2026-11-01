# MySelavu release shrinking rules.
# Room, Compose and DataStore ship their own consumer rules, so very little is needed here.

# Keep enum names stable: they are stored in the database and in backups.
-keepclassmembers enum com.myselavu.** { *; }
