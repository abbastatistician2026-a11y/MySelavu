package com.myselavu.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.budget.UnspentMode
import java.time.LocalDate

/*
 * Database tables. Rules for this file:
 *  - All money is stored as Long paise (columns end in "Paise"). Never Float/Double.
 *  - Dates are stored as epoch days (LocalDate), timestamps as epoch millis.
 *  - Salary, fixed expenses and category allocations are "effective from" rule tables:
 *    an edit INSERTS a new row; rows are never updated in place.
 *  - When a budget month starts, its values are copied into period_* tables (snapshots), so
 *    closed months never change.
 *  - Any change here needs a new database version AND a migration (see AppDatabase).
 */

// ---------- Salary & income ----------

@Entity(tableName = "salary_rule", indices = [Index("effectiveFrom")])
data class SalaryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveFrom: LocalDate,
    val amountPaise: Long,
    val createdAt: Long,
)

/** One-off extra income for a month: bonus, refund, gift. Kept separate from salary. */
@Entity(tableName = "extra_income", indices = [Index("date")])
data class ExtraIncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val amountPaise: Long,
    val note: String = "",
    val createdAt: Long,
)

// ---------- Fixed monthly expenses ----------

@Entity(tableName = "fixed_expense")
data class FixedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(
    tableName = "fixed_expense_rule",
    foreignKeys = [ForeignKey(
        entity = FixedExpenseEntity::class, parentColumns = ["id"], childColumns = ["fixedExpenseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("fixedExpenseId"), Index("effectiveFrom")],
)
data class FixedExpenseRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fixedExpenseId: Long,
    val effectiveFrom: LocalDate,
    val name: String,
    val amountPaise: Long,
    /** Day of month it's due (1..31), optional. */
    val dueDay: Int? = null,
    /** false = deleted from [effectiveFrom] onwards. */
    val active: Boolean = true,
    val reminderEnabled: Boolean = false,
    val createdAt: Long,
)

// ---------- Categories ----------

@Entity(tableName = "category", indices = [Index("sortOrder")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    /** ARGB colour. */
    val color: Long,
    val type: CategoryType = CategoryType.SPENDING,
    val sortOrder: Int = 0,
    /** Soft delete: archived categories keep their history. */
    val archived: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "category_rule",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("categoryId"), Index("effectiveFrom")],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val effectiveFrom: LocalDate,
    val allocationPaise: Long,
    /** false = archived from [effectiveFrom] onwards. */
    val active: Boolean = true,
    val createdAt: Long,
)

@Entity(
    tableName = "sub_category",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("categoryId")],
)
data class SubCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

// ---------- Budget periods (frozen monthly snapshots) ----------

@Entity(tableName = "budget_period")
data class BudgetPeriodEntity(
    @PrimaryKey val startDate: LocalDate,
    val endDate: LocalDate,
    val salaryPaise: Long,
    val closed: Boolean = false,
    val closedAt: Long? = null,
    /** The unspent-money rule applied when this month was closed. */
    val closeMode: UnspentMode? = null,
    val moneyLeftAtClosePaise: Long? = null,
    val savingsDepositPaise: Long? = null,
    /** Set once the month-end "Report card" has been shown. */
    val reportCardSeen: Boolean = false,
)

@Entity(
    tableName = "period_fixed_expense",
    primaryKeys = ["periodStart", "fixedExpenseId"],
    foreignKeys = [ForeignKey(
        entity = BudgetPeriodEntity::class, parentColumns = ["startDate"], childColumns = ["periodStart"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("fixedExpenseId")],
)
data class PeriodFixedExpenseEntity(
    val periodStart: LocalDate,
    val fixedExpenseId: Long,
    val name: String,
    val amountPaise: Long,
    val dueDay: Int? = null,
    val paid: Boolean = false,
    val paidAt: Long? = null,
)

@Entity(
    tableName = "period_category",
    primaryKeys = ["periodStart", "categoryId"],
    foreignKeys = [ForeignKey(
        entity = BudgetPeriodEntity::class, parentColumns = ["startDate"], childColumns = ["periodStart"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("categoryId")],
)
data class PeriodCategoryEntity(
    val periodStart: LocalDate,
    val categoryId: Long,
    val type: CategoryType,
    val allocationPaise: Long,
    val carryInPaise: Long = 0,
)

/** "Move ₹500 from Personal to Food" inside one month. */
@Entity(tableName = "category_transfer", indices = [Index("periodStart")])
data class CategoryTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodStart: LocalDate,
    val fromCategoryId: Long,
    val toCategoryId: Long,
    val amountPaise: Long,
    val createdAt: Long,
)

// ---------- Expenses ----------

@Entity(
    tableName = "expense",
    indices = [Index("date"), Index("categoryId"), Index("method")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val categoryId: Long,
    val subCategoryId: Long? = null,
    val method: PaymentMethod = PaymentMethod.UPI,
    val note: String = "",
    val date: LocalDate,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** File name of the compressed receipt photo in private app storage. */
    val receiptFile: String? = null,
    /** Set when created from a recurring payment ("Mark paid"). */
    val recurringId: Long? = null,
    val templateId: Long? = null,
)

/** "Tea ₹15, Food, Cash": one-tap logging. */
@Entity(tableName = "quick_template", indices = [Index("sortOrder")])
data class QuickTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountPaise: Long,
    val categoryId: Long,
    val subCategoryId: Long? = null,
    val method: PaymentMethod = PaymentMethod.UPI,
    val note: String = "",
    val sortOrder: Int = 0,
    val useCount: Int = 0,
)

// ---------- Recurring payments & subscriptions ----------

enum class FrequencyKind { MONTHLY, EVERY_N_MONTHS, YEARLY }

@Entity(tableName = "recurring_payment", indices = [Index("nextDueDate")])
data class RecurringPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountPaise: Long,
    val categoryId: Long,
    val method: PaymentMethod = PaymentMethod.UPI,
    val frequency: FrequencyKind = FrequencyKind.MONTHLY,
    /** For EVERY_N_MONTHS. */
    val intervalMonths: Int = 1,
    /** First due date; all later due dates are calculated from it. */
    val anchorDate: LocalDate,
    val nextDueDate: LocalDate,
    val isSubscription: Boolean = true,
    val reminderEnabled: Boolean = true,
    val active: Boolean = true,
    val createdAt: Long,
)

// ---------- Savings pot & goals ----------

enum class PotEntryKind { MONTH_END, MANUAL_ADD, WITHDRAW, TO_GOAL, CARRIED_BALANCE }

@Entity(tableName = "savings_pot_entry", indices = [Index("date")])
data class SavingsPotEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    /** Positive = added to the pot, negative = taken out. */
    val amountPaise: Long,
    val kind: PotEntryKind,
    val periodStart: LocalDate? = null,
    val goalId: Long? = null,
    val note: String = "",
    val createdAt: Long,
)

@Entity(tableName = "savings_goal")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎯",
    val targetPaise: Long,
    val deadline: LocalDate? = null,
    val reachedAt: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "goal_contribution",
    foreignKeys = [ForeignKey(
        entity = SavingsGoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("goalId")],
)
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val date: LocalDate,
    val amountPaise: Long,
    /** true = taken from the Savings pot, false = a manual contribution. */
    val fromPot: Boolean,
    val createdAt: Long,
)

// ---------- Lent & borrowed ----------

@Entity(tableName = "debt", indices = [Index("settled")])
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: com.myselavu.core.debts.DebtDirection,
    /** Typed by hand. The app never reads contacts. */
    val person: String,
    val amountPaise: Long,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val note: String = "",
    val settled: Boolean = false,
    val settledAt: Long? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "debt_repayment",
    foreignKeys = [ForeignKey(
        entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("debtId")],
)
data class DebtRepaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val date: LocalDate,
    val amountPaise: Long,
    val note: String = "",
    val createdAt: Long,
)

/** Small result rows used by queries. */
data class CategorySum(
    @ColumnInfo(name = "categoryId") val categoryId: Long,
    @ColumnInfo(name = "totalPaise") val totalPaise: Long,
)

data class DaySum(
    @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "totalPaise") val totalPaise: Long,
)

data class DayCategorySum(
    @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "categoryId") val categoryId: Long,
    @ColumnInfo(name = "totalPaise") val totalPaise: Long,
)

data class MethodSum(
    @ColumnInfo(name = "method") val method: PaymentMethod,
    @ColumnInfo(name = "totalPaise") val totalPaise: Long,
)
