package com.example.poepart2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MenuActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var welcomeText: TextView
    private lateinit var totalBalanceText: TextView
    private lateinit var expensesText: TextView
    private lateinit var actualBalanceText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var mostExpenseText: TextView
    private lateinit var mostExpenseIcon: ImageView

    private lateinit var label_other_expenses: TextView
    private lateinit var perctext: TextView

    private lateinit var otherExpensesList: LinearLayout
    private lateinit var otherExpensesContainer: LinearLayout

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var balanceListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null
    private var expensesListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        drawerLayout = findViewById(R.id.main_drawer)
        val menuIcon = findViewById<ImageView>(R.id.menuIcon)
        welcomeText = findViewById(R.id.welcome_text)
        progressBar = findViewById(R.id.progress_bar)
        mostExpenseText = findViewById(R.id.most_expense_text)
        label_other_expenses = findViewById(R.id.label_other_expenses)
        perctext = findViewById(R.id.percentage_text)
        mostExpenseIcon = findViewById(R.id.most_expense_icon)
        otherExpensesList = findViewById(R.id.others_expense_list)
        otherExpensesContainer = findViewById(R.id.other_expenses_container)

        val balanceSection = findViewById<LinearLayout>(R.id.balance_section)
        totalBalanceText = balanceSection.findViewWithTag("Total Balance")
        expensesText = balanceSection.findViewWithTag("Expenses")
        actualBalanceText = balanceSection.findViewWithTag("Actual Balance")

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userId = currentUser.uid

            userListener = db.collection("users").document(userId)
                .addSnapshotListener { document, _ ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: "User"
                        welcomeText.text = "Hi $name,\nWelcome back"
                    }
                }

            balanceListener = db.collection("balances").document(userId)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val total = doc.getDouble("total") ?: 0.0
                        val expenses = doc.getDouble("expenses") ?: 0.0
                        val actual = total - expenses

                        // 👇 Ces labels restent inchangés
                        totalBalanceText.text = "Total Balance\nR $total"
                        expensesText.text = "Expenses\nR $expenses"
                        actualBalanceText.text = "Actual Balance\nR $actual"

                        // 👇 Seulement ici on change : calcul du pourcentage basé sur la somme des goals
                        db.collection("budget_goals").document(userId)
                            .collection("goals").get().addOnSuccessListener { goalsSnapshot ->
                                val totalGoal =
                                    goalsSnapshot.sumOf { it.getDouble("amount") ?: 0.0 }
                                val percentage =
                                    if (totalGoal != 0.0) ((expenses / totalGoal) * 100).toInt() else 0

                                progressBar.progress = percentage
                                perctext.text = "you are at $percentage% of your budget goal"
                            }

                    } else {
                        totalBalanceText.text = "Total Balance\nN/A"
                        expensesText.text = "Expenses\nN/A"
                        actualBalanceText.text = "Actual Balance\nN/A"
                        progressBar.progress = 0
                        perctext.text = "you are at 0% of your budget goal"
                    }
                }

            expensesListener = db.collection("transactions")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) {
                        mostExpenseText.text = "No expenses now"
                        mostExpenseIcon.setImageResource(0)


                        return@addSnapshotListener
                    }

                    val expenses = snapshot.documents.mapNotNull { it.data }
                    val sortedExpenses =
                        expenses.sortedByDescending { (it["amount"] as? Number)?.toDouble() ?: 0.0 }

                    val most = sortedExpenses.firstOrNull()
                    if (most != null) {
                        mostExpenseText.text = "${most["name"]}: ${most["amount"]} R"
                        val iconUrl = most["iconUrl"] as? String
                        if (!iconUrl.isNullOrEmpty()) {
                            Glide.with(this).load(iconUrl).into(mostExpenseIcon)
                        } else {
                            mostExpenseIcon.setImageResource(0)
                        }
                    }

                    // Update other expenses
                    otherExpensesContainer.removeAllViews()
                    val othersList = sortedExpenses.drop(1)
                    if (othersList.isEmpty()) {
                        val none = TextView(this)
                        none.text = "No other expenses"
                        none.setTextColor(resources.getColor(android.R.color.black))
                        otherExpensesContainer.addView(none)
                    } else {
                        label_other_expenses.text = "Other expenses"
                        for (expense in othersList) {
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(8, 8, 8, 8)
                            }

                            val image = ImageView(this)
                            image.layoutParams = LinearLayout.LayoutParams(40, 40)
                            val url = expense["iconUrl"] as? String
                            if (!url.isNullOrEmpty()) {
                                Glide.with(this).load(url).into(image)
                            }

                            val text = TextView(this)
                            text.text = "${expense["name"]}: ${expense["amount"]} R"
                            text.setPadding(12, 0, 0, 0)

                            row.addView(image)
                            row.addView(text)
                            otherExpensesContainer.addView(row)
                        }
                    }
                }
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        menuIcon.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        findViewById<TextView>(R.id.languageText).setOnClickListener {
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
        }

        findViewById<TextView>(R.id.contactSupport).setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }

        findViewById<TextView>(R.id.cat).setOnClickListener {
            startActivity(Intent(this, CategoryActivity::class.java))
        }

        findViewById<TextView>(R.id.acc).setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }

        findViewById<TextView>(R.id.curr).setOnClickListener {
            startActivity(Intent(this, CurrencySettingsActivity::class.java))
        }

        findViewById<TextView>(R.id.rank).setOnClickListener {
            startActivity(Intent(this, MyRankingActivity::class.java))
        }

        findViewById<ImageView>(R.id.navTransactions).setOnClickListener {
            startActivity(Intent(this, WalletScreenActivity::class.java))
        }

        findViewById<ImageView>(R.id.navAnalysis).setOnClickListener {
            startActivity(Intent(this, AnalyticsScreenActivity::class.java))
        }

        findViewById<ImageView>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(this, UserScreenActivity::class.java))
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        balanceListener?.remove()
        userListener?.remove()
        expensesListener?.remove()
    }
}

