package eu.kanade.tachiyomi.network

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class SqliteStore(dbFile: File) : AutoCloseable {

    private val connection: Connection = run {
        dbFile.parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }

    init {
        connection.createStatement().use { st ->
            st.executeUpdate("PRAGMA journal_mode=WAL;")
            st.executeUpdate("PRAGMA busy_timeout=3000;")
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS kv_store (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )""",
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS cookies (
                    host       TEXT NOT NULL,
                    name       TEXT NOT NULL,
                    value      TEXT NOT NULL,
                    domain     TEXT NOT NULL,
                    path       TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    secure     INTEGER NOT NULL,
                    http_only  INTEGER NOT NULL,
                    host_only  INTEGER NOT NULL,
                    PRIMARY KEY (host, name)
                )""",
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS favorites (
                    source_id       TEXT NOT NULL,
                    url             TEXT NOT NULL,
                    title           TEXT NOT NULL,
                    thumbnail_url   TEXT,
                    type            TEXT NOT NULL,
                    added_at        INTEGER NOT NULL,
                    last_read_url   TEXT,
                    last_read_name  TEXT,
                    PRIMARY KEY (source_id, url)
                )""",
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS watch_history (
                    source_id        TEXT NOT NULL,
                    anime_url        TEXT NOT NULL,
                    ep_url           TEXT NOT NULL,
                    anime_title      TEXT NOT NULL,
                    ep_name          TEXT NOT NULL,
                    thumb            TEXT,
                    time_seconds     REAL NOT NULL,
                    duration_seconds REAL NOT NULL,
                    updated_at       INTEGER NOT NULL,
                    completed        INTEGER NOT NULL DEFAULT 0,
                    episode_number   INTEGER,
                    PRIMARY KEY (source_id, anime_url, ep_url)
                )""",
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS extensions (
                    pkg          TEXT PRIMARY KEY,
                    name         TEXT NOT NULL,
                    version_name TEXT NOT NULL,
                    version_code INTEGER NOT NULL,
                    is_nsfw      INTEGER NOT NULL,
                    is_anime     INTEGER NOT NULL,
                    apk_file     TEXT,
                    jar_file     TEXT,
                    source_count INTEGER NOT NULL DEFAULT 0,
                    installed_at INTEGER NOT NULL
                )""",
            )
        }
    }

    @Synchronized
    fun kvGet(key: String): String? =
        connection.prepareStatement("SELECT value FROM kv_store WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    @Synchronized
    fun kvSet(key: String, value: String) {
        connection.prepareStatement("INSERT OR REPLACE INTO kv_store (key, value) VALUES (?, ?)").use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun kvDelete(key: String) {
        connection.prepareStatement("DELETE FROM kv_store WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun kvAll(prefix: String = ""): List<Pair<String, String>> =
        connection.prepareStatement("SELECT key, value FROM kv_store WHERE key LIKE ?").use { ps ->
            ps.setString(1, "$prefix%")
            ps.executeQuery().use { rs ->
                val out = mutableListOf<Pair<String, String>>()
                while (rs.next()) out.add(rs.getString(1) to rs.getString(2))
                out
            }
        }

    @Synchronized
    fun cookieAll(): List<StoredCookie> =
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT host, name, value, domain, path, expires_at, secure, http_only, host_only FROM cookies",
            ).use { rs ->
                val out = mutableListOf<StoredCookie>()
                while (rs.next()) {
                    out.add(
                        StoredCookie(
                            host = rs.getString(1),
                            name = rs.getString(2),
                            value = rs.getString(3),
                            domain = rs.getString(4),
                            path = rs.getString(5),
                            expiresAt = rs.getLong(6),
                            secure = rs.getInt(7) != 0,
                            httpOnly = rs.getInt(8) != 0,
                            hostOnly = rs.getInt(9) != 0,
                        ),
                    )
                }
                out
            }
        }

    @Synchronized
    fun cookieUpsert(c: StoredCookie) {
        connection.prepareStatement(
            """INSERT OR REPLACE INTO cookies (host, name, value, domain, path, expires_at, secure, http_only, host_only)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        ).use { ps ->
            ps.setString(1, c.host)
            ps.setString(2, c.name)
            ps.setString(3, c.value)
            ps.setString(4, c.domain)
            ps.setString(5, c.path)
            ps.setLong(6, c.expiresAt)
            ps.setInt(7, if (c.secure) 1 else 0)
            ps.setInt(8, if (c.httpOnly) 1 else 0)
            ps.setInt(9, if (c.hostOnly) 1 else 0)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun cookieDelete(host: String, name: String) {
        connection.prepareStatement("DELETE FROM cookies WHERE host = ? AND name = ?").use { ps ->
            ps.setString(1, host)
            ps.setString(2, name)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun cookieDeleteAll() {
        connection.createStatement().use { it.executeUpdate("DELETE FROM cookies") }
    }

    @Synchronized
    fun favoriteAll(): List<Favorite> =
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT source_id, url, title, thumbnail_url, type, added_at, last_read_url, last_read_name FROM favorites",
            ).use { rs ->
                val out = mutableListOf<Favorite>()
                while (rs.next()) {
                    out.add(
                        Favorite(
                            sourceId = rs.getString(1),
                            url = rs.getString(2),
                            title = rs.getString(3),
                            thumbnailUrl = rs.getString(4),
                            type = rs.getString(5),
                            addedAt = rs.getLong(6),
                            lastReadUrl = rs.getString(7),
                            lastReadName = rs.getString(8),
                        ),
                    )
                }
                out
            }
        }

    @Synchronized
    fun favoriteGet(sourceId: String, url: String): Favorite? =
        connection.prepareStatement(
            "SELECT source_id, url, title, thumbnail_url, type, added_at, last_read_url, last_read_name FROM favorites WHERE source_id = ? AND url = ?",
        ).use { ps ->
            ps.setString(1, sourceId)
            ps.setString(2, url)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else Favorite(
                    sourceId = rs.getString(1),
                    url = rs.getString(2),
                    title = rs.getString(3),
                    thumbnailUrl = rs.getString(4),
                    type = rs.getString(5),
                    addedAt = rs.getLong(6),
                    lastReadUrl = rs.getString(7),
                    lastReadName = rs.getString(8),
                )
            }
        }

    @Synchronized
    fun favoriteUpsert(f: Favorite) {
        connection.prepareStatement(
            """INSERT OR REPLACE INTO favorites (source_id, url, title, thumbnail_url, type, added_at, last_read_url, last_read_name)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        ).use { ps ->
            ps.setString(1, f.sourceId)
            ps.setString(2, f.url)
            ps.setString(3, f.title)
            ps.setString(4, f.thumbnailUrl)
            ps.setString(5, f.type)
            ps.setLong(6, f.addedAt)
            ps.setString(7, f.lastReadUrl)
            ps.setString(8, f.lastReadName)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun favoriteDelete(sourceId: String, url: String) {
        connection.prepareStatement("DELETE FROM favorites WHERE source_id = ? AND url = ?").use { ps ->
            ps.setString(1, sourceId)
            ps.setString(2, url)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun favoriteSetProgress(sourceId: String, url: String, lastReadUrl: String?, lastReadName: String?) {
        connection.prepareStatement("UPDATE favorites SET last_read_url = ?, last_read_name = ? WHERE source_id = ? AND url = ?").use { ps ->
            ps.setString(1, lastReadUrl)
            ps.setString(2, lastReadName)
            ps.setString(3, sourceId)
            ps.setString(4, url)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun watchAll(): List<WatchEntry> =
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT source_id, anime_url, ep_url, anime_title, ep_name, thumb, time_seconds, duration_seconds, updated_at, completed, episode_number FROM watch_history ORDER BY updated_at DESC",
            ).use { rs ->
                val out = mutableListOf<WatchEntry>()
                while (rs.next()) {
                    val epNum = rs.getInt(11)
                    out.add(
                        WatchEntry(
                            sourceId = rs.getString(1),
                            animeUrl = rs.getString(2),
                            epUrl = rs.getString(3),
                            animeTitle = rs.getString(4),
                            epName = rs.getString(5),
                            thumb = rs.getString(6),
                            timeSeconds = rs.getDouble(7),
                            durationSeconds = rs.getDouble(8),
                            updatedAt = rs.getLong(9),
                            completed = rs.getInt(10) != 0,
                            episodeNumber = if (rs.wasNull()) null else epNum,
                        ),
                    )
                }
                out
            }
        }

    @Synchronized
    fun watchUpsert(w: WatchEntry) {
        connection.prepareStatement(
            """INSERT OR REPLACE INTO watch_history (source_id, anime_url, ep_url, anime_title, ep_name, thumb, time_seconds, duration_seconds, updated_at, completed, episode_number)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        ).use { ps ->
            ps.setString(1, w.sourceId)
            ps.setString(2, w.animeUrl)
            ps.setString(3, w.epUrl)
            ps.setString(4, w.animeTitle)
            ps.setString(5, w.epName)
            ps.setString(6, w.thumb)
            ps.setDouble(7, w.timeSeconds)
            ps.setDouble(8, w.durationSeconds)
            ps.setLong(9, w.updatedAt)
            ps.setInt(10, if (w.completed) 1 else 0)
            if (w.episodeNumber != null) ps.setInt(11, w.episodeNumber) else ps.setNull(11, java.sql.Types.INTEGER)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun watchDelete(sourceId: String, animeUrl: String, epUrl: String) {
        connection.prepareStatement("DELETE FROM watch_history WHERE source_id = ? AND anime_url = ? AND ep_url = ?").use { ps ->
            ps.setString(1, sourceId)
            ps.setString(2, animeUrl)
            ps.setString(3, epUrl)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun extensionAll(): List<StoredExtension> =
        connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT pkg, name, version_name, version_code, is_nsfw, is_anime, apk_file, jar_file, source_count, installed_at FROM extensions",
            ).use { rs ->
                val out = mutableListOf<StoredExtension>()
                while (rs.next()) {
                    out.add(
                        StoredExtension(
                            pkg = rs.getString(1),
                            name = rs.getString(2),
                            versionName = rs.getString(3),
                            versionCode = rs.getLong(4),
                            isNsfw = rs.getInt(5) != 0,
                            isAnime = rs.getInt(6) != 0,
                            apkFile = rs.getString(7),
                            jarFile = rs.getString(8),
                            sourceCount = rs.getInt(9),
                            installedAt = rs.getLong(10),
                        ),
                    )
                }
                out
            }
        }

    @Synchronized
    fun extensionUpsert(e: StoredExtension) {
        connection.prepareStatement(
            """INSERT OR REPLACE INTO extensions
               (pkg, name, version_name, version_code, is_nsfw, is_anime, apk_file, jar_file, source_count, installed_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        ).use { ps ->
            ps.setString(1, e.pkg)
            ps.setString(2, e.name)
            ps.setString(3, e.versionName)
            ps.setLong(4, e.versionCode)
            ps.setInt(5, if (e.isNsfw) 1 else 0)
            ps.setInt(6, if (e.isAnime) 1 else 0)
            ps.setString(7, e.apkFile)
            ps.setString(8, e.jarFile)
            ps.setInt(9, e.sourceCount)
            ps.setLong(10, e.installedAt)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun extensionDelete(pkg: String) {
        connection.prepareStatement("DELETE FROM extensions WHERE pkg = ?").use { ps ->
            ps.setString(1, pkg)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun reposGet(): List<String> {
        val raw = kvGet("user_repos") ?: return emptyList()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Synchronized
    fun reposSet(repos: List<String>) {
        val filtered = repos.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (filtered.isEmpty()) {
            kvDelete("user_repos")
        } else {
            kvSet("user_repos", filtered.joinToString("\n"))
        }
    }

    override fun close() {
        runCatching { connection.close() }
    }
}
