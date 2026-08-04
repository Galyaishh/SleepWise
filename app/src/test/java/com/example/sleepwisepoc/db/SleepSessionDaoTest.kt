package com.example.sleepwisepoc.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sleepwisepoc.StageTick
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SleepSessionDaoTest {

    private lateinit var db: SleepWiseDatabase
    private lateinit var dao: SleepSessionDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, SleepWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun makeEntity(
        windowStart: String = "2026-08-04T00:30:00Z",
        windowEnd: String   = "2026-08-04T07:00:00Z",
        startedAt: String   = "2026-08-04T00:29:00Z",
        stages: List<StageTick> = emptyList(),
    ) = SleepSessionEntity(
        windowStart = windowStart,
        windowEnd   = windowEnd,
        startedAt   = startedAt,
        stages      = stages,
    )

    private val sampleTick = StageTick(
        t = "2026-08-04T02:00:00Z", stage = "Light", conf = 0.85f, stable = true
    )

    // ── insert ────────────────────────────────────────────────────────────────

    @Test
    fun insert_returnsPositiveId() = runTest {
        val id = dao.insert(makeEntity())
        assertTrue(id > 0)
    }

    @Test
    fun insert_defaultStatus_isPending() = runTest {
        val id = dao.insert(makeEntity())
        val row = dao.getById(id)
        assertEquals("PENDING", row?.uploadStatus)
    }

    @Test
    fun insert_persistsWindowFields() = runTest {
        val entity = makeEntity(
            windowStart = "2026-08-04T00:30:00Z",
            windowEnd   = "2026-08-04T07:00:00Z",
            startedAt   = "2026-08-04T00:29:00Z",
        )
        val id = dao.insert(entity)
        val row = dao.getById(id)

        assertNotNull(row)
        assertEquals(entity.windowStart, row!!.windowStart)
        assertEquals(entity.windowEnd,   row.windowEnd)
        assertEquals(entity.startedAt,   row.startedAt)
    }

    // ── getPending ────────────────────────────────────────────────────────────

    @Test
    fun getPending_returnsNewlyInsertedSession() = runTest {
        dao.insert(makeEntity())
        val pending = dao.getPending()
        assertEquals(1, pending.size)
    }

    @Test
    fun getPending_ignoresUploadedSessions() = runTest {
        val id = dao.insert(makeEntity())
        dao.markUploaded(id, "2026-08-04T07:01:00Z")
        assertTrue(dao.getPending().isEmpty())
    }

    @Test
    fun getPending_returnsMultiplePendingInOrder() = runTest {
        dao.insert(makeEntity(startedAt = "2026-08-04T00:00:00Z"))
        dao.insert(makeEntity(startedAt = "2026-08-05T00:00:00Z"))
        val pending = dao.getPending()
        assertEquals(2, pending.size)
    }

    // ── markUploaded ──────────────────────────────────────────────────────────

    @Test
    fun markUploaded_changesStatusToUploaded() = runTest {
        val id = dao.insert(makeEntity())
        dao.markUploaded(id, "2026-08-04T07:01:00Z")
        val row = dao.getById(id)
        assertEquals("UPLOADED", row?.uploadStatus)
    }

    @Test
    fun markUploaded_setsEndedAt() = runTest {
        val endedAt = "2026-08-04T07:01:00Z"
        val id = dao.insert(makeEntity())
        dao.markUploaded(id, endedAt)
        val row = dao.getById(id)
        assertEquals(endedAt, row?.endedAt)
    }

    // ── updateStages ──────────────────────────────────────────────────────────

    @Test
    fun updateStages_persistsTicksReadableAsEntity() = runTest {
        val id = dao.insert(makeEntity())
        val ticks = listOf(sampleTick)
        val json = Gson().toJson(ticks)

        dao.updateStages(id, json)

        val row = dao.getById(id)
        assertNotNull(row)
        assertEquals(1, row!!.stages.size)
        assertEquals("Light", row.stages[0].stage)
        assertEquals(0.85f,   row.stages[0].conf, 0.001f)
        assertEquals(true,    row.stages[0].stable)
    }

    @Test
    fun updateStages_emptyJson_givesEmptyList() = runTest {
        val id = dao.insert(makeEntity())
        dao.updateStages(id, "[]")
        val row = dao.getById(id)
        assertTrue(row!!.stages.isEmpty())
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    fun getById_nonexistentId_returnsNull() = runTest {
        assertNull(dao.getById(9999L))
    }
}
