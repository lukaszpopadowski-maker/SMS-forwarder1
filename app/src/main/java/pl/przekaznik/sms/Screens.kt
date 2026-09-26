package pl.przekaznik.sms

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

// ------------------------------------------------------------------ pomocnicze widoki

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
private fun Context.isDark() =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(14), dp(16), dp(14))
    background = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(if (isDark()) Color.parseColor("#1E2622") else Color.parseColor("#FFFFFF"))
    }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { setMargins(0, 0, 0, dp(12)) }
}

private fun Context.title(t: String) = TextView(this).apply {
    text = t; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, dp(8))
}

private fun Context.small(t: String) = TextView(this).apply {
    text = t; textSize = 13f; alpha = 0.75f
}

private fun Context.button(t: String, onClick: () -> Unit) = Button(this).apply {
    text = t; isAllCaps = false; setOnClickListener { onClick() }
}

private fun Context.screen(): Pair<ScrollView, LinearLayout> {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(40))
    }
    val sv = ScrollView(this).apply {
        setBackgroundColor(if (isDark()) Color.parseColor("#0F1412") else Color.parseColor("#EEF3F0"))
        addView(col)
    }
    return sv to col
}

// ------------------------------------------------------------------ ekran główny

class MainActivity : Activity() {

    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (sv, c) = screen()
        col = c
        setContentView(sv)
        if (Store.rules(this).isEmpty()) {
            Store.saveRule(this, Rule(name = "Przykład – zmień lub usuń", enabled = false,
                senders = "600123456", keywords = "zlecenie"))
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(statusCard())
        col.addView(rulesCard())
        col.addView(toolsCard())
        col.addView(logCard())
    }

    // ---- uprawnienia i ustawienia telefonu
    private fun statusCard(): View = card().apply {
        addView(title("Gotowość"))
        val smsOk = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
            .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        addView(statusRow("Odbieranie i wysyłanie SMS", smsOk, "Zezwól") {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE), 1)
        })
        addView(statusRow("Bez ograniczeń baterii", batteryOk, "Zezwól") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        })
        addView(small("Samsung: dodatkowo Ustawienia → Bateria → Limity użycia w tle → Aplikacje nigdy nieusypiane → dodaj Przekaźnik SMS, żeby system nie ubijał aplikacji w tle."))

        val paused = Store.paused(this@MainActivity)
        addView(Switch(this@MainActivity).apply {
            text = "Przekazywanie włączone"
            isChecked = !paused
            textSize = 16f
            setPadding(0, dp(10), 0, 0)
            setOnCheckedChangeListener { _, on -> Store.setPaused(this@MainActivity, !on) }
        })
    }

    private fun statusRow(label: String, ok: Boolean, action: String, onFix: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(TextView(this@MainActivity).apply {
                text = (if (ok) "✅  " else "⚠️  ") + label
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!ok) addView(button(action, onFix))
        }

    // ---- reguły
    private fun rulesCard(): View = card().apply {
        addView(title("Reguły przekazywania"))
        val rules = Store.rules(this@MainActivity)
        if (rules.isEmpty()) addView(small("Brak reguł."))
        for (r in rules) {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = r.name; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
                    })
                    addView(small(r.summary()))
                    setOnClickListener { openRule(r.id) }
                })
                addView(Switch(this@MainActivity).apply {
                    isChecked = r.enabled
                    setOnCheckedChangeListener { _, on -> r.enabled = on; Store.saveRule(this@MainActivity, r) }
                })
            })
        }
        addView(button("+ Dodaj regułę") { openRule(null) })
    }

    private fun openRule(id: String?) {
        startActivity(Intent(this, RuleActivity::class.java).apply { if (id != null) putExtra("id", id) })
    }

    // ---- narzędzia
    private fun toolsCard(): View = card().apply {
        addView(title("Test"))
        addView(small("Symulacja SMS-a – sprawdza reguły i wykonuje prawdziwe przekazanie."))
        addView(button("Symuluj przychodzący SMS") { simulateDialog() })
    }

    private fun simulateDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val sender = EditText(this).apply { hint = "Nadawca (numer lub nazwa)"; inputType = InputType.TYPE_CLASS_TEXT }
        val body = EditText(this).apply { hint = "Treść SMS"; minLines = 2 }
        box.addView(sender); box.addView(body)
        AlertDialog.Builder(this)
            .setTitle("Symulacja SMS")
            .setView(box)
            .setPositiveButton("Przetwórz") { _, _ ->
                val s = sender.text.toString().trim(); val b = body.text.toString()
                val any = Store.rules(this).any { it.matches(s, b) }
                if (!any) Toast.makeText(this, "Żadna włączona reguła nie pasuje", Toast.LENGTH_LONG).show()
                Thread { Forwarder.handleIncoming(applicationContext, s, b); runOnUiThread { render() } }.start()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    // ---- dziennik
    private fun logCard(): View = card().apply {
        addView(title("Dziennik"))
        val entries = Store.logEntries(this@MainActivity)
        addView(TextView(this@MainActivity).apply {
            text = if (entries.isEmpty()) "Pusto" else entries.take(40).joinToString("\n")
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        addView(LinearLayout(this@MainActivity).apply {
            addView(button("Odśwież") { render() })
            addView(button("Wyczyść") { Store.clearLog(this@MainActivity); render() })
        })
    }
}

// ------------------------------------------------------------------ edycja reguły

class RuleActivity : Activity() {

    private lateinit var rule: Rule
    private val fields = mutableMapOf<String, EditText>()
    private lateinit var allKeys: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id")
        rule = Store.rules(this).firstOrNull { it.id == id } ?: Rule()
        title = if (id == null) "Nowa reguła" else "Edycja reguły"

        val (sv, col) = screen()
        setContentView(sv)

        col.addView(card().apply {
            addView(title("Nazwa"))
            addView(field("name", rule.name, "np. Zlecenia od klienta X"))
        })

        col.addView(card().apply {
            addView(title("KIEDY przekazać"))
            addView(small("Nadawcy – numery lub nazwy nadawcy, oddzielone przecinkami. Puste = każdy nadawca. Numery porównywane są po 9 ostatnich cyfrach (+48 nie ma znaczenia)."))
            addView(field("senders", rule.senders, "np. 600123456, ORLEN, InPost", InputType.TYPE_CLASS_TEXT))
            addView(small("Słowa kluczowe w treści – oddzielone przecinkami, wielkość liter bez znaczenia. Puste = każda treść."))
            addView(field("keywords", rule.keywords, "np. zlecenie, awaria"))
            allKeys = CheckBox(this@RuleActivity).apply { text = "Wymagaj wszystkich słów (domyślnie wystarczy jedno)"; isChecked = rule.allKeywords }
            addView(allKeys)
            addView(small("Gdy podasz i nadawców, i słowa – muszą być spełnione oba warunki."))
        })

        col.addView(card().apply {
            addView(title("DOKĄD przekazać"))
            addView(small("Numery, na które SMS ma trafić – oddzielone przecinkami."))
            addView(field("smsTargets", rule.smsTargets, "np. 600111222, 500333444", InputType.TYPE_CLASS_PHONE))
        })

        col.addView(card().apply {
            addView(title("Treść przekazywanej wiadomości"))
            addView(small("Znaczniki: {nadawca} {tresc} {czas} {regula}. Uwaga: polskie znaki i emoji skracają pojedynczy SMS do 70 znaków."))
            addView(field("template", rule.template, Rule.DEFAULT_TEMPLATE, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        })

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Zapisz") { save(); finish() })
            addView(button("Test wysyłki") { save(); testSend() })
            if (id != null) addView(button("Usuń") {
                AlertDialog.Builder(this@RuleActivity).setMessage("Usunąć regułę „${rule.name}”?")
                    .setPositiveButton("Usuń") { _, _ -> Store.deleteRule(this@RuleActivity, rule.id); finish() }
                    .setNegativeButton("Anuluj", null).show()
            })
        })
    }

    private fun field(key: String, value: String, hint: String, type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES): EditText =
        EditText(this).apply {
            setText(value); this.hint = hint; inputType = type
            if (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) { minLines = 3; gravity = Gravity.TOP }
            fields[key] = this
        }

    private fun v(key: String) = fields[key]?.text?.toString()?.trim() ?: ""

    private fun save() {
        rule.name = v("name").ifEmpty { "Reguła" }
        rule.senders = v("senders")
        rule.keywords = v("keywords")
        rule.allKeywords = allKeys.isChecked
        rule.smsTargets = v("smsTargets")
        rule.template = fields["template"]?.text?.toString()?.ifBlank { Rule.DEFAULT_TEMPLATE } ?: Rule.DEFAULT_TEMPLATE
        Store.saveRule(this, rule)
        if (rule.senders.isBlank() && rule.keywords.isBlank())
            Toast.makeText(this, "Uwaga: bez nadawcy i słów reguła przekaże KAŻDY SMS", Toast.LENGTH_LONG).show()
    }

    /** Wysyła wiadomość testową do wszystkich odbiorców reguły z pominięciem warunków. */
    private fun testSend() {
        val text = rule.render("TEST", "Wiadomość testowa z Przekaźnika SMS")
        val targets = Rule.splitList(rule.smsTargets)
        if (targets.isEmpty()) { Toast.makeText(this, "Reguła nie ma odbiorców", Toast.LENGTH_SHORT).show(); return }
        targets.forEach { Forwarder.sendSms(this, it, text, "test") }
        Toast.makeText(this, "Wysłano test – wynik w dzienniku", Toast.LENGTH_SHORT).show()
    }
}
