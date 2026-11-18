package com.fcastellanos.voxlibris.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Exención de ahorro de batería. Sin ella, con la pantalla apagada el sistema le baja el CPU a la app
 * y la síntesis de voz deja de llegar a tiempo (en MIUI la lectura suena a tirones).
 */
object BatteryOptimization {

    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Diálogo del sistema de un solo toque que agrega la app a la lista de exentas; en MIUI deja
     * el ahorro de batería de la app en "Sin restricciones". Si no se puede mostrar, abre los ajustes.
     */
    fun request(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) ||
            openSettings(context)

    /** Pantalla de ajustes de batería de la app: la propia de MIUI si existe, si no la lista de Android. */
    fun openSettings(context: Context): Boolean =
        start(context, miuiAppBatteryIntent(context)) ||
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    private fun miuiAppBatteryIntent(context: Context): Intent = Intent()
        .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
        .putExtra("package_name", context.packageName)
        .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess
}
