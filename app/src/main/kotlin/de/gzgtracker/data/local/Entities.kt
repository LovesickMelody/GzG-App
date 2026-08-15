package de.gzgtracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ibanLast4: String?,
    val colorHex: String,
    val isActive: Boolean = true,
    val iban: String? = null,
    val vorname: String? = null,
    val nachname: String? = null,
    val strasse: String? = null,
    val hausnummer: String? = null,
    val plz: String? = null,
    val ort: String? = null,
    val telefon: String? = null,
    val email: String? = null,
    val anrede: String? = null,
    val geburtsdatum: LocalDate? = null,
    val bic: String? = null,
    val createdAt: Instant,
)

/**
 * Aktionen aus `data/actions.json` und von Hand angelegte.
 *
 * [lastSeenAt] haelt fest, wann eine Aktion zuletzt im Feed stand. Verschwindet sie
 * dort, bleibt sie trotzdem erhalten, solange Einreichungen daran haengen — sonst
 * stuenden alte Eintraege ohne Aktionsdaten da.
 */
@Entity(
    tableName = "promo_actions",
    indices = [Index("source"), Index("submissionDeadline")],
)
data class PromoActionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val brand: String?,
    val type: String,
    val maxRefundCents: Int?,
    val validFrom: LocalDate?,
    val validTo: LocalDate?,
    val submissionDeadline: LocalDate?,
    val url: String?,
    val submitUrl: String?,
    val requirements: List<String>,
    val retailers: List<String>,
    val eans: List<String>,
    val imageUrl: String?,
    val source: String,
    val isManual: Boolean = false,
    val lastSeenAt: Instant? = null,
)

@Entity(
    tableName = "submissions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            // Konten werden deaktiviert, nie geloescht — so bleibt die Historie heil.
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("actionId"),
        Index("status"),
        Index("purchaseDate"),
        Index("ean"),
    ],
)
data class SubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Bewusst ohne Fremdschluessel auf promo_actions: Aktionen kommen und gehen mit
    // dem Feed, eine Einreichung ueberlebt das Verschwinden ihrer Aktion.
    val actionId: String,
    val accountId: Long,
    val productName: String,
    val ean: String?,
    val pricePaidCents: Int,
    val purchaseDate: LocalDate,
    val retailer: String?,
    val receiptImagePath: String?,
    val productImagePath: String?,
    val comboImagePath: String?,
    val status: String,
    val submittedAt: LocalDate?,
    val refundedAt: LocalDate?,
    val refundedAmountCents: Int?,
    val note: String?,
    val createdAt: Instant,
)

/**
 * Die Merkliste — was beim naechsten Einkauf mitkommen soll.
 *
 * Bewusst eine eigene Tabelle statt einer Spalte an der Aktion: Aktionen werden
 * bei jedem Feed-Abgleich ersetzt und verschwundene weggeraeumt. Eine Merkung
 * darf das ueberleben; sie ist eine Entscheidung des Nutzers, keine Feed-Angabe.
 *
 * [imWagen] ist das Haekchen fuer den Einkauf selbst. Es steht in der Datenbank
 * und nicht nur im Bildschirmzustand, weil man im Laden zwischendurch die App
 * verlaesst — und danach nicht wieder von vorn anfangen will.
 */
@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val actionId: String,
    val imWagen: Boolean = false,
    val addedAt: Instant,
)
