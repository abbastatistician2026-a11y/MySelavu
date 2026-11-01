package com.myselavu.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun epochDayToDate(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)
}

/**
 * The single on-device database. NO data ever leaves the phone (the app has no INTERNET permission).
 *
 * Updating the app must never wipe data: when the schema changes, bump [version], add a
 * Migration to [MIGRATIONS], and commit the new JSON in app/schemas/. There is deliberately
 * no fallbackToDestructiveMigration().
 */
@Database(
    entities = [
        SalaryRuleEntity::class,
        ExtraIncomeEntity::class,
        FixedExpenseEntity::class,
        FixedExpenseRuleEntity::class,
        CategoryEntity::class,
        CategoryRuleEntity::class,
        SubCategoryEntity::class,
        BudgetPeriodEntity::class,
        PeriodFixedExpenseEntity::class,
        PeriodCategoryEntity::class,
        CategoryTransferEntity::class,
        ExpenseEntity::class,
        QuickTemplateEntity::class,
        RecurringPaymentEntity::class,
        SavingsPotEntryEntity::class,
        SavingsGoalEntity::class,
        GoalContributionEntity::class,
        DebtEntity::class,
        DebtRepaymentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rulesDao(): RulesDao
    abstract fun periodDao(): PeriodDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun planningDao(): PlanningDao

    companion object {
        const val NAME = "myselavu.db"

        /** Add future migrations here, e.g. MIGRATION_1_2. */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
