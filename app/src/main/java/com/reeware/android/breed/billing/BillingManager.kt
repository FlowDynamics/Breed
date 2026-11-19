package com.reeware.android.breed.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*
import com.reeware.android.breed.BuildConfig
import com.reeware.android.breed.data.LocalStorage
import com.reeware.android.breed.engine.GameLocale
import com.reeware.android.breed.engine.ShopProduct

/**
 * Offizielle Google Play Billing v7 Integration für optionale In-App-Käufe (Elite-Aliens & Bio-Waffen).
 * - Lädt reale lokale Preise von Google Play (queryProductDetailsAsync)
 * - Stellt vorhandene Käufe beim Start und manuell wieder her (queryPurchasesAsync)
 * - Bestätigt Käufe ordnungsgemäß (acknowledgePurchase)
 * - Release-Builds erlauben ausschließlich verifizierte Google-Play-Käufe.
 */
class BillingManager(
    context: Context,
    private val storage: LocalStorage,
    private val onEntitlementsUpdated: (Set<String>, String?) -> Unit
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val productDetailsMap = HashMap<String, ProductDetails>()

    @Volatile
    private var isConnected = false

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            isConnected = true
            queryProductsAndPurchases()
            return
        }
        runCatching {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        isConnected = true
                        queryProductsAndPurchases()
                    } else {
                        isConnected = false
                    }
                }

                override fun onBillingServiceDisconnected() {
                    isConnected = false
                }
            })
        }
    }

    fun endConnection() {
        isConnected = false
        runCatching { billingClient.endConnection() }
    }

    fun isPlayStoreReady(): Boolean = isConnected && productDetailsMap.isNotEmpty()

    fun formattedPrice(product: ShopProduct): String {
        val details = productDetailsMap[product.productId]
        return details?.oneTimePurchaseOfferDetails?.formattedPrice ?: product.fallbackPrice
    }

    fun restorePurchases(onFeedback: (String) -> Unit) {
        if (!billingClient.isReady) {
            startConnection()
            onFeedback(
                GameLocale.tr(
                    "Google Play wird verbunden – bitte kurz erneut versuchen.",
                    "Connecting to Google Play – please try again shortly."
                )
            )
            return
        }
        queryExistingPurchases { count ->
            val msg = if (count > 0) {
                GameLocale.tr(
                    "$count Kauf/Käufe aus Google Play wiederhergestellt.",
                    "Restored $count purchase(s) from Google Play."
                )
            } else {
                GameLocale.tr(
                    "Keine früheren Käufe im aktuellen Google-Play-Konto gefunden.",
                    "No previous purchases found on this Google Play account."
                )
            }
            mainHandler.post { onFeedback(msg) }
        }
    }

    fun launchPurchase(activity: Activity, product: ShopProduct, onError: (String) -> Unit) {
        val details = productDetailsMap[product.productId]
        if (billingClient.isReady && details != null) {
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
            val res = billingClient.launchBillingFlow(activity, flowParams)
            if (res.responseCode != BillingClient.BillingResponseCode.OK) {
                onError(
                    GameLocale.tr(
                        "Google-Play-Kauf konnte nicht gestartet werden (${res.debugMessage}).",
                        "Could not launch Google Play purchase (${res.debugMessage})."
                    )
                )
            }
            return
        }

        // Im Debug-Build optionaler lokaler Testkauf zum Ausprobieren der neuen Aliens & Bio-Waffen
        if (BuildConfig.DEBUG) {
            grantEntitlements(setOf(product.productId), GameLocale.tr(
                "[DEBUG] '${product.title}' zum Testen freigeschaltet.",
                "[DEBUG] Unlocked '${product.title}' for testing."
            ))
            return
        }

        startConnection()
        onError(
            GameLocale.tr(
                "Google Play Store nicht bereit oder Artikel noch nicht in der Play Console aktiviert.",
                "Google Play Store unavailable or product not yet active in Play Console."
            )
        )
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    handlePurchases(purchases, notifySuccess = true)
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryExistingPurchases {}
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                mainHandler.post {
                    onEntitlementsUpdated(
                        storage.loadOwnedProductIds(),
                        GameLocale.tr(
                            "Kauf abgebrochen/fehlgeschlagen (Code ${result.responseCode}).",
                            "Purchase canceled or failed (code ${result.responseCode})."
                        )
                    )
                }
            }
        }
    }

    private fun queryProductsAndPurchases() {
        val productList = ShopProduct.allProductIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsMap.clear()
                for (d in detailsList) {
                    productDetailsMap[d.productId] = d
                }
            }
            queryExistingPurchases {}
        }
    }

    private fun queryExistingPurchases(onDone: (Int) -> Unit) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases, notifySuccess = false)
                onDone(purchases.count { it.purchaseState == Purchase.PurchaseState.PURCHASED })
            } else {
                onDone(0)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, notifySuccess: Boolean) {
        val newlyOwned = mutableSetOf<String>()
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            newlyOwned.addAll(purchase.products)
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(ackParams) { /* bestätigt */ }
            }
        }
        if (newlyOwned.isNotEmpty()) {
            val msg = if (notifySuccess) {
                GameLocale.tr(
                    "Vielen Dank! Neue Brut-Erweiterung freigeschaltet.",
                    "Thank you! New brood expansion unlocked."
                )
            } else null
            grantEntitlements(newlyOwned, msg)
        }
    }

    private fun grantEntitlements(productIds: Set<String>, message: String?) {
        val merged = storage.loadOwnedProductIds() + productIds
        storage.saveOwnedProductIds(merged)

        // Frisch gekaufte Bio-Waffen automatisch ausrüsten, damit sie sofort wirken
        val equipped = storage.loadEquippedWeapons().toMutableSet()
        for (pid in productIds) {
            ShopProduct.byProductId(pid)?.unlockedWeapons?.let { equipped.addAll(it) }
        }
        storage.saveEquippedWeapons(equipped)

        mainHandler.post {
            onEntitlementsUpdated(merged, message)
        }
    }
}
