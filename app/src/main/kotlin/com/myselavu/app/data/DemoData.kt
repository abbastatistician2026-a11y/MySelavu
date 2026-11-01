package com.myselavu.app.data

import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.QuickTemplateEntity
import com.myselavu.app.data.settings.SettingsRepository
import com.myselavu.app.time.AppClock
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.money.Money
import java.time.LocalDate
import kotlin.random.Random

/** TEST app only: fills the app with ~3 months of realistic Indian expenses. */
class DemoData(
    private val repo: BudgetRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    suspend fun load() {
        repo.wipeAll()
        settings.setBudgetStartDay(1)
        val today = clock.today()
        val start = today.minusMonths(3).withDayOfMonth(1)
        // Rules effective from the first demo month.
        repo.completeSetupFrom(
            start = start,
            salary = Money.rupees(55000L),
            fixed = listOf(
                FixedInput("Rent", Money.rupees(14000L), 5),
                FixedInput("Family fund", Money.rupees(8000L), 1),
                FixedInput("EB bill", Money.rupees(1200L), 15),
                FixedInput("Internet", Money.rupees(700L), 10),
            ),
            categories = listOf(
                CategoryInput("Food", "🍛", 0xFFE07A2E, CategoryType.SPENDING, Money.rupees(8000L)),
                CategoryInput("Travel", "🛺", 0xFF2E86AB, CategoryType.SPENDING, Money.rupees(3000L)),
                CategoryInput("Personal", "🛍️", 0xFF8E5BB5, CategoryType.SPENDING, Money.rupees(4000L)),
                CategoryInput("Savings", "🏦", 0xFF2E7D32, CategoryType.SAVINGS, Money.rupees(10000L)),
                CategoryInput("Emergency", "🚑", 0xFFC62828, CategoryType.SAVINGS, Money.rupees(3000L)),
            ),
        )
        val cats = repo.db.rulesDao().categories().associateBy { it.name }
        val food = cats.getValue("Food").id
        val travel = cats.getValue("Travel").id
        val personal = cats.getValue("Personal").id

        val rnd = Random(42)
        val methods = listOf(PaymentMethod.UPI, PaymentMethod.UPI, PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.DEBIT_CARD)
        var d: LocalDate = start
        val expenses = ArrayList<ExpenseEntity>()
        fun add(cat: Long, rupees: Int, note: String, method: PaymentMethod = methods[rnd.nextInt(methods.size)]) {
            expenses += ExpenseEntity(amountPaise = rupees * 100L, categoryId = cat, method = method, note = note, date = d,
                createdAt = d.toEpochDay() * 86_400_000L + rnd.nextInt(80_000_000))
        }
        while (!d.isAfter(today)) {
            if (rnd.nextInt(10) < 8) add(food, listOf(15, 20, 25, 30).random(rnd), "Tea & snacks", PaymentMethod.CASH)
            if (rnd.nextInt(10) < 4) add(food, 80 + rnd.nextInt(180), listOf("Lunch", "Dinner", "Hotel saravana", "Biryani").random(rnd))
            if (d.dayOfWeek.value == 6) add(food, 900 + rnd.nextInt(900), "Groceries")
            if (rnd.nextInt(10) < 5) add(travel, listOf(30, 45, 60, 120, 180).random(rnd), listOf("Auto", "Bus", "Metro", "Ola").random(rnd))
            if (d.dayOfMonth == 20) add(travel, 500 + rnd.nextInt(300), "Petrol")
            if (rnd.nextInt(10) < 1) add(personal, 200 + rnd.nextInt(1500), listOf("Shirt", "Haircut", "Medicines", "Gift", "Movie").random(rnd))
            d = d.plusDays(1)
        }
        expenses.forEach { repo.restoreExpense(it) }
        repo.addExtraIncome(Money.rupees(5000L), start.plusMonths(1).plusDays(9), "Diwali bonus")
        repo.addTemplate(QuickTemplateEntity(name = "Tea", amountPaise = 1500, categoryId = food, method = PaymentMethod.CASH, note = "Tea"))
        repo.addTemplate(QuickTemplateEntity(name = "Bus", amountPaise = 3000, categoryId = travel, method = PaymentMethod.CASH, note = "Bus"))
        repo.addTemplate(QuickTemplateEntity(name = "Lunch", amountPaise = 12000, categoryId = food, method = PaymentMethod.UPI, note = "Lunch", sortOrder = 2))
        repo.bootstrapFrom(start)
    }
}
