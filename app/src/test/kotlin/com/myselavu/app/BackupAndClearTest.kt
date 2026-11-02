package com.myselavu.app

import com.myselavu.app.data.BackupManager
import com.myselavu.app.data.BudgetRepository
import com.myselavu.app.data.db.AppDatabase
import com.myselavu.app.data.db.BudgetPeriodEntity
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.PeriodCategoryEntity
import com.myselavu.app.data.db.PotEntryKind
import com.myselavu.app.data.db.SavingsPotEntryEntity
import com.myselavu.app.data.settings.SettingsRepository
import com.myselavu.app.time.AppClock
import com.myselavu.core.backup.BackupCodec
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.PaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BackupAndClearTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private val clock = AppClock.fixed(LocalDate.of(2026, 11, 18))

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = AppDatabase.inMemory(app)
        settings = SettingsRepository(app)
    }

    @After fun tearDown() { db.close() }

    private suspend fun seed() {
        val food = db.rulesDao().insertCategory(CategoryEntity(name = "Food", emoji = "🍛", color = 0xFFE07A2E, createdAt = 1))
        for (m in 0 until 4) {
            val start = LocalDate.of(2026, 8 + m, 1)
            db.periodDao().insertPeriod(BudgetPeriodEntity(start, start.plusMonths(1).minusDays(1), 5_000_000, closed = m < 3))
            db.periodDao().insertPeriodCategories(listOf(PeriodCategoryEntity(start, food, CategoryType.SPENDING, 800_000)))
            db.expenseDao().insert(ExpenseEntity(amountPaise = 1500L + m, categoryId = food, method = PaymentMethod.CASH,
                note = "Tea உணவு #$m", date = start.plusDays(3), createdAt = m.toLong()))
            db.planningDao().insertPotEntry(SavingsPotEntryEntity(date = start.plusMonths(1).minusDays(1), amountPaise = 100_000,
                kind = PotEntryKind.MONTH_END, createdAt = 1))
        }
    }

    @Test fun backupWipeRestoreKeepsEverything() = runBlocking {
        seed()
        settings.setSetupDone(true)
        settings.setProfileName("Abbas")
        val manager = BackupManager(RuntimeEnvironment.getApplication(), db, settings, clock)
        val out = ByteArrayOutputStream()
        manager.createBackup(out, "secret")
        val bytes = out.toByteArray()
        assertTrue(BackupCodec.isEncrypted(bytes))

        // Lose everything
        db.clearAllTables()
        settings.setProfileName("")
        assertEquals(0, db.expenseDao().count())

        // Wrong password is refused
        var refused = false
        try { manager.readBackup(bytes, "nope") } catch (e: BackupCodec.WrongPasswordException) { refused = true }
        assertTrue(refused)

        val preview = manager.readBackup(bytes, "secret")
        assertEquals(4, preview.expenseCount)
        assertEquals(4, preview.monthCount)
        manager.restore(preview)

        assertEquals(4, db.expenseDao().count())
        assertEquals(4, db.periodDao().allPeriods().size)
        assertEquals(400_000L, db.planningDao().potTotal())
        val notes = db.expenseDao().all().map { it.note }
        assertTrue(notes.contains("Tea உணவு #2"))
        assertEquals(PaymentMethod.CASH, db.expenseDao().all().first().method)
        assertEquals("Abbas", settings.current().profileName)
        assertTrue(settings.current().setupDone)
    }

    @Test fun clearingHistoryKeepsCurrentMonthAndPotTotal() = runBlocking {
        seed()
        val repo = BudgetRepository(db, settings, clock)
        // Clear everything before October 2026
        val result = repo.clearHistoryBefore(LocalDate.of(2026, 10, 15))
        assertEquals(LocalDate.of(2026, 10, 1), result.keptFrom)
        assertEquals(2, result.expensesDeleted)
        assertEquals(2, result.monthsDeleted)
        assertEquals(2, db.expenseDao().count())
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)), db.periodDao().allPeriods().map { it.startDate })
        // Savings pot total is unchanged
        assertEquals(400_000L, db.planningDao().potTotal())

        // "All past months" never deletes the current month
        repo.clearHistoryBefore(clock.today())
        assertEquals(listOf(LocalDate.of(2026, 11, 1)), db.periodDao().allPeriods().map { it.startDate })
        assertEquals(1, db.expenseDao().count())
        assertEquals(400_000L, db.planningDao().potTotal())
    }
}
