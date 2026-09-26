package pl.przekaznik.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

/** Odbiera każdy przychodzący SMS (działa także, gdy aplikacja jest zamknięta). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Długi SMS przychodzi w kilku częściach – sklejamy je per nadawca
        val bySender = LinkedHashMap<String, StringBuilder>()
        for (p in parts) {
            if (p == null) continue
            val s = p.displayOriginatingAddress ?: p.originatingAddress ?: "?"
            bySender.getOrPut(s) { StringBuilder() }.append(p.displayMessageBody ?: p.messageBody ?: "")
        }
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                for ((sender, body) in bySender) Forwarder.handleIncoming(app, sender, body.toString())
            } catch (e: Exception) {
                Store.log(app, "❌ Błąd obsługi SMS: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object Forwarder {

    fun handleIncoming(ctx: Context, sender: String, body: String) {
        if (Store.paused(ctx)) return
        val matched = Store.rules(ctx).filter { it.matches(sender, body) }
        if (matched.isEmpty()) return

        for (rule in matched) {
            val smsTargets = Rule.splitList(rule.smsTargets)
            // Ochrona przed pętlą: nie przekazujemy SMS-a z powrotem do numeru, który jest odbiorcą
            val targets = smsTargets.filterNot { Rule.senderMatches(sender, it) }
            if (targets.size != smsTargets.size)
                Store.log(ctx, "↺ Pominięto odbiorcę będącego nadawcą (reguła „${rule.name}”)")
            if (targets.isEmpty()) {
                Store.log(ctx, "⚠ „${rule.name}”: SMS od $sender pasuje, ale brak odbiorców")
                continue
            }
            val text = rule.render(sender, body)
            Store.log(ctx, "📩 „${rule.name}”: SMS od $sender pasuje, przekazuję")
            for (n in targets) sendSms(ctx, n, text, "reguła „${rule.name}”")
        }
    }

    fun sendSms(ctx: Context, number: String, text: String, note: String): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Store.log(ctx, "❌ Brak uprawnienia do wysyłania SMS")
            return false
        }
        return try {
            val sm = smsManager(ctx)
            val parts = sm.divideMessage(text)
            sm.sendMultipartTextMessage(number, null, parts, null, null)
            Store.log(ctx, "✅ SMS → $number ($note)")
            true
        } catch (e: Exception) {
            Store.log(ctx, "❌ SMS → $number nieudany: ${e.message}")
            false
        }
    }

    /** Obsługa Dual SIM: domyślna karta SMS, a gdy jej brak („pytaj zawsze”) – pierwsza aktywna. */
    private fun smsManager(ctx: Context): SmsManager {
        var subId = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
            ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val sub = ctx.getSystemService(SubscriptionManager::class.java)
                subId = sub.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId ?: subId
            } catch (_: SecurityException) { }
        }
        return if (Build.VERSION.SDK_INT >= 31) {
            val base = ctx.getSystemService(SmsManager::class.java)
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(subId) else base
        } else {
            @Suppress("DEPRECATION")
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) SmsManager.getSmsManagerForSubscriptionId(subId)
            else SmsManager.getDefault()
        }
    }
}
