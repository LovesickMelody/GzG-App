package de.gzgtracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import de.gzgtracker.ui.aktionen.AktionBearbeitenScreen
import de.gzgtracker.ui.aktionen.AktionDetailScreen
import de.gzgtracker.ui.formular.WebFormularScreen
import de.gzgtracker.ui.aktionen.AktionenScreen
import de.gzgtracker.ui.detail.DetailScreen
import de.gzgtracker.ui.einstellungen.EinstellungenScreen
import de.gzgtracker.ui.erfassen.ErfassenScreen
import de.gzgtracker.ui.konten.KontenScreen
import de.gzgtracker.ui.uebersicht.UebersichtScreen

/** Alle Ziele der App an einer Stelle, damit Routen nicht als Strings verstreuen. */
object Routes {
    const val UEBERSICHT = "uebersicht"
    const val AKTIONEN = "aktionen"
    const val KONTEN = "konten"
    const val EINSTELLUNGEN = "einstellungen"

    const val DETAIL = "detail/{id}"
    const val ERFASSEN = "erfassen?actionId={actionId}&ean={ean}&submissionId={submissionId}"
    const val AKTION_BEARBEITEN = "aktion-bearbeiten?actionId={actionId}"
    const val AKTION = "aktion/{actionId}"
    const val WEBFORMULAR = "webformular/{submissionId}"

    fun detail(id: Long) = "detail/$id"

    fun aktion(actionId: String) = "aktion/$actionId"

    fun webformular(submissionId: Long) = "webformular/$submissionId"

    fun erfassen(actionId: String? = null, ean: String? = null, submissionId: Long? = null): String =
        "erfassen?actionId=${actionId.orEmpty()}&ean=${ean.orEmpty()}" +
            "&submissionId=${submissionId ?: -1L}"

    fun aktionBearbeiten(actionId: String? = null) =
        "aktion-bearbeiten?actionId=${actionId.orEmpty()}"
}

private data class TabZiel(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

// Aktionen zuerst: Der Weg beginnt bei "was gibt es?", nicht bei "was habe ich
// schon eingereicht?". "Optionen" statt "Einstellungen", weil das lange Wort in
// der Leiste mitten im Wort umbrach.
private val TABS = listOf(
    TabZiel(Routes.AKTIONEN, "Aktionen", Icons.Outlined.LocalOffer),
    TabZiel(Routes.UEBERSICHT, "Belege", Icons.Outlined.ReceiptLong),
    TabZiel(Routes.KONTEN, "Konten", Icons.Outlined.CreditCard),
    TabZiel(Routes.EINSTELLUNGEN, "Optionen", Icons.Outlined.Settings),
)

@Composable
fun GzgApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val aktuelleRoute = backStackEntry?.destination
    val zeigeLeiste = TABS.any { tab ->
        aktuelleRoute?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (zeigeLeiste) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    TABS.forEach { tab ->
                        val ausgewaehlt =
                            aktuelleRoute?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = ausgewaehlt,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1) },
                            // Wo man ist, traegt den Akzent. Vorher unterschieden
                            // sich aktiver und schlafender Reiter nur durch einen
                            // Grauton — auf dem Geraet kaum zu sehen.
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innenAbstand ->
        NavHost(
            navController = navController,
            startDestination = Routes.AKTIONEN,
            modifier = Modifier.padding(innenAbstand),
        ) {
            composable(Routes.UEBERSICHT) {
                UebersichtScreen(
                    onEintragOeffnen = { id -> navController.navigate(Routes.detail(id)) },
                    onErfassen = { navController.navigate(Routes.erfassen()) },
                )
            }

            composable(Routes.AKTIONEN) {
                AktionenScreen(
                    onAktionOeffnen = { actionId ->
                        navController.navigate(Routes.aktion(actionId))
                    },
                    onAktionAnlegen = { navController.navigate(Routes.aktionBearbeiten(null)) },
                )
            }

            composable(
                route = Routes.AKTION,
                arguments = listOf(navArgument("actionId") { type = NavType.StringType }),
            ) {
                AktionDetailScreen(
                    onZurueck = { navController.popBackStack() },
                    onEinreichen = { actionId ->
                        navController.navigate(Routes.erfassen(actionId = actionId))
                    },
                    onBearbeiten = { actionId ->
                        navController.navigate(Routes.aktionBearbeiten(actionId))
                    },
                )
            }

            composable(
                route = Routes.WEBFORMULAR,
                arguments = listOf(navArgument("submissionId") { type = NavType.LongType }),
            ) {
                WebFormularScreen(onZurueck = { navController.popBackStack() })
            }

            composable(Routes.KONTEN) { KontenScreen() }

            composable(Routes.EINSTELLUNGEN) { EinstellungenScreen() }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                DetailScreen(
                    onZurueck = { navController.popBackStack() },
                    onBearbeiten = { id ->
                        navController.navigate(Routes.erfassen(submissionId = id))
                    },
                    onEinreichen = { id ->
                        navController.navigate(Routes.webformular(id))
                    },
                )
            }

            composable(
                route = Routes.ERFASSEN,
                arguments = listOf(
                    navArgument("actionId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("ean") { type = NavType.StringType; defaultValue = "" },
                    navArgument("submissionId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                ErfassenScreen(
                    onFertig = { navController.popBackStack() },
                    onAbbrechen = { navController.popBackStack() },
                    onEinreichen = { submissionId ->
                        // Das Formular ersetzt die Erfassung im Verlauf: Zurueck
                        // fuehrt dann zur Liste, nicht in ein Formular, das man
                        // gerade abgeschickt hat.
                        navController.navigate(Routes.webformular(submissionId)) {
                            popUpTo(Routes.ERFASSEN) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Routes.AKTION_BEARBEITEN,
                arguments = listOf(
                    navArgument("actionId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                AktionBearbeitenScreen(onFertig = { navController.popBackStack() })
            }
        }
    }
}
