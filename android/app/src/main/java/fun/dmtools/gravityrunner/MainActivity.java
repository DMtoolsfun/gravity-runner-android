package fun.dmtools.gravityrunner;

import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.aptoide.sdk.billing.AptoideBillingClient;
import com.aptoide.sdk.billing.AptoideBillingClient.BillingResponseCode;
import com.aptoide.sdk.billing.AptoideBillingClient.ProductType;
import com.aptoide.sdk.billing.BillingFlowParams;
import com.aptoide.sdk.billing.BillingResult;
import com.aptoide.sdk.billing.ConsumeParams;
import com.aptoide.sdk.billing.ProductDetails;
import com.aptoide.sdk.billing.Purchase;
import com.aptoide.sdk.billing.PurchasesUpdatedListener;
import com.aptoide.sdk.billing.QueryProductDetailsParams;
import com.aptoide.sdk.billing.QueryPurchasesParams;
import com.aptoide.sdk.billing.listeners.AptoideBillingClientStateListener;
import com.getcapacitor.BridgeActivity;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.samsung.android.sdk.iap.lib.constants.HelperDefine;
import com.samsung.android.sdk.iap.lib.helper.IapHelper;
import com.samsung.android.sdk.iap.lib.vo.AcknowledgeVo;
import com.samsung.android.sdk.iap.lib.vo.ErrorVo;
import com.samsung.android.sdk.iap.lib.vo.OwnedProductVo;
import com.samsung.android.sdk.iap.lib.vo.PurchaseVo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "GravityRunnerAds";
    private static final String IAP_TAG = "GravityRunnerIAP";
    private static final String APTOIDE_TAG = "GravityRunnerAptoide";
    private static final String CHANNEL_APTOIDE = "aptoide";
    private static final String CHANNEL_SAMSUNG = "samsung";
    private static final String REMOVE_ADS_PRODUCT_ID = "remove_ads";
    private static final String APTOIDE_REMOVE_ADS_30_DAYS_PRODUCT_ID = "remove_ads_30_days";
    private static final String APTOIDE_REMOVE_ADS_LIFETIME_PRODUCT_ID = "remove_ads_lifetime";
    private static final long APTOIDE_REMOVE_ADS_30_DAYS_MS = 30L * 24L * 60L * 60L * 1000L;
    // AdMob ad unit IDs come from Gradle build types: debug uses test IDs, release uses production IDs.
    private static final String BANNER_AD_UNIT_ID = BuildConfig.ADMOB_BANNER_ID;
    private static final String INTERSTITIAL_AD_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_ID;
    private static final String REWARDED_AD_UNIT_ID = BuildConfig.ADMOB_REWARDED_ID;
    private static final int BANNER_NAV_FALLBACK_MARGIN_DP = 64;
    private static final int BANNER_NAV_EXTRA_MARGIN_DP = 12;

    private FrameLayout bannerContainer;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private boolean isLoadingInterstitial;
    private boolean isLoadingRewarded;
    private IapHelper samsungIapHelper;
    private AptoideBillingClient aptoideBillingClient;
    private boolean aptoideBillingReady;
    private final Map<String, ProductDetails> aptoideProductDetails = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MobileAds.initialize(this, initializationStatus -> Log.d(TAG, "Mobile Ads initialized"));
        setupBannerContainer();
        getBridge().getWebView().addJavascriptInterface(new GravityRunnerNativeBridge(), "GravityRunnerNative");
        loadInterstitialAd();
        loadRewardedAd();
        if (isAptoideChannel()) {
            initializeAptoideBilling();
        } else {
            checkRemoveAdsOwnershipOnStartup();
        }
    }

    @Override
    public void onDestroy() {
        if (aptoideBillingClient != null) {
            aptoideBillingClient.endConnection();
        }
        super.onDestroy();
    }

    private void setupBannerContainer() {
        bannerContainer = new FrameLayout(this);
        bannerContainer.setVisibility(View.GONE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dpToPx(BANNER_NAV_FALLBACK_MARGIN_DP);
        addContentView(bannerContainer, params);

        bannerContainer.setOnApplyWindowInsetsListener((view, insets) -> {
            int navBarInset = 0;
            if (insets != null) {
                navBarInset = insets.getSystemWindowInsetBottom();
            }

            updateBannerBottomMargin(navBarInset + dpToPx(BANNER_NAV_EXTRA_MARGIN_DP));
            Log.d(TAG, "Banner bottom margin updated for nav inset: " + navBarInset);
            return insets;
        });
        bannerContainer.post(() -> {
            try {
                bannerContainer.requestApplyInsets();
            } catch (RuntimeException exception) {
                Log.w(TAG, "Unable to request banner window insets", exception);
            }
        });
    }

    private void updateBannerBottomMargin(int bottomMargin) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bannerContainer.getLayoutParams();
        params.bottomMargin = bottomMargin;
        bannerContainer.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private AdRequest newAdRequest() {
        return new AdRequest.Builder().build();
    }

    private void loadInterstitialAd() {
        if (isLoadingInterstitial || interstitialAd != null) {
            return;
        }

        isLoadingInterstitial = true;
        InterstitialAd.load(this, INTERSTITIAL_AD_UNIT_ID, newAdRequest(), new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(InterstitialAd ad) {
                isLoadingInterstitial = false;
                interstitialAd = ad;
                interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        interstitialAd = null;
                        loadInterstitialAd();
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        evaluateJavascriptSafely("window.GravityRunnerRewards && window.GravityRunnerRewards.markInterstitialShown()");
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(AdError adError) {
                        Log.w(TAG, "Interstitial failed to show: " + adError.getMessage());
                        interstitialAd = null;
                        loadInterstitialAd();
                    }
                });
                Log.d(TAG, "Interstitial loaded");
            }

            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                isLoadingInterstitial = false;
                interstitialAd = null;
                Log.w(TAG, "Interstitial failed to load: " + loadAdError.getMessage());
            }
        });
    }

    private void loadRewardedAd() {
        if (isLoadingRewarded || rewardedAd != null) {
            return;
        }

        isLoadingRewarded = true;
        RewardedAd.load(this, REWARDED_AD_UNIT_ID, newAdRequest(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd ad) {
                isLoadingRewarded = false;
                rewardedAd = ad;
                Log.d(TAG, "Rewarded loaded");
            }

            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                isLoadingRewarded = false;
                rewardedAd = null;
                Log.w(TAG, "Rewarded failed to load: " + loadAdError.getMessage());
            }
        });
    }

    private void showRewardedAd(String rewardJavascript) {
        if (rewardedAd == null) {
            Log.d(TAG, "Rewarded not loaded; requesting a new one");
            loadRewardedAd();
            return;
        }

        RewardedAd adToShow = rewardedAd;
        rewardedAd = null;
        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                loadRewardedAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                Log.w(TAG, "Rewarded failed to show: " + adError.getMessage());
                loadRewardedAd();
            }
        });
        adToShow.show(this, rewardItem -> evaluateJavascriptSafely(rewardJavascript));
    }

    private void evaluateJavascriptSafely(String javascript) {
        WebView webView = getBridge().getWebView();
        if (webView == null) {
            Log.w(TAG, "WebView unavailable for JavaScript callback");
            return;
        }

        webView.post(() -> webView.evaluateJavascript(javascript, null));
    }

    private boolean isAptoideChannel() {
        return CHANNEL_APTOIDE.equalsIgnoreCase(BuildConfig.DISTRIBUTION_CHANNEL);
    }

    private boolean isSamsungChannel() {
        return CHANNEL_SAMSUNG.equalsIgnoreCase(BuildConfig.DISTRIBUTION_CHANNEL);
    }

    private void initializeAptoideBilling() {
        if (BuildConfig.APTOIDE_PUBLIC_KEY == null || BuildConfig.APTOIDE_PUBLIC_KEY.trim().isEmpty()) {
            Log.w(APTOIDE_TAG, "Aptoide public key is not configured; billing remains unavailable and ads stay enabled.");
            return;
        }

        try {
            PurchasesUpdatedListener purchasesUpdatedListener = this::handleAptoidePurchasesUpdated;
            aptoideBillingClient = AptoideBillingClient.newBuilder(this)
                .setListener(purchasesUpdatedListener)
                .setPublicKey(BuildConfig.APTOIDE_PUBLIC_KEY)
                .build();
            aptoideBillingClient.startConnection(new AptoideBillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(BillingResult billingResult) {
                    if (!isAptoideSuccess(billingResult)) {
                        Log.w(APTOIDE_TAG, "Aptoide billing setup failed: " + describeAptoideResult(billingResult));
                        aptoideBillingReady = false;
                        return;
                    }

                    aptoideBillingReady = true;
                    Log.i(APTOIDE_TAG, "Aptoide billing ready");
                    queryAptoideProducts();
                    restoreAptoidePurchases();
                }

                @Override
                public void onBillingServiceDisconnected() {
                    aptoideBillingReady = false;
                    Log.w(APTOIDE_TAG, "Aptoide billing disconnected");
                }
            });
        } catch (RuntimeException exception) {
            aptoideBillingReady = false;
            Log.w(APTOIDE_TAG, "Aptoide billing initialization failed", exception);
        }
    }

    private void queryAptoideProducts() {
        if (!canUseAptoideBilling("query products")) {
            return;
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(List.of(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(APTOIDE_REMOVE_ADS_30_DAYS_PRODUCT_ID)
                    .setProductType(ProductType.INAPP)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(APTOIDE_REMOVE_ADS_LIFETIME_PRODUCT_ID)
                    .setProductType(ProductType.INAPP)
                    .build()
            ))
            .build();

        try {
            aptoideBillingClient.queryProductDetailsAsync(params, (billingResult, productDetailsResult) -> {
                if (!isAptoideSuccess(billingResult) || productDetailsResult == null) {
                    Log.w(APTOIDE_TAG, "Aptoide product query failed: " + describeAptoideResult(billingResult));
                    return;
                }

                aptoideProductDetails.clear();
                for (ProductDetails productDetails : productDetailsResult.getProductDetailsList()) {
                    if (productDetails != null) {
                        aptoideProductDetails.put(productDetails.getProductId(), productDetails);
                    }
                }
                Log.i(APTOIDE_TAG, "Aptoide products available: " + aptoideProductDetails.keySet());
            });
        } catch (RuntimeException exception) {
            Log.w(APTOIDE_TAG, "Aptoide product query failed", exception);
        }
    }

    private void purchaseAptoideRemoveAds30Days() {
        if (isSamsungChannel()) {
            purchaseRemoveAds();
            return;
        }

        startAptoidePurchase(APTOIDE_REMOVE_ADS_30_DAYS_PRODUCT_ID);
    }

    private void purchaseAptoideRemoveAdsLifetime() {
        if (isSamsungChannel()) {
            purchaseRemoveAds();
            return;
        }

        startAptoidePurchase(APTOIDE_REMOVE_ADS_LIFETIME_PRODUCT_ID);
    }

    private void startAptoidePurchase(String productId) {
        if (!isAptoideChannel()) {
            Log.w(APTOIDE_TAG, "Aptoide purchase requested outside Aptoide channel");
            showIapMessage("Purchase unavailable");
            return;
        }
        if (!canUseAptoideBilling("purchase")) {
            showIapMessage("Purchase unavailable");
            return;
        }

        ProductDetails productDetails = aptoideProductDetails.get(productId);
        if (productDetails == null) {
            Log.w(APTOIDE_TAG, "Aptoide product details unavailable for " + productId);
            queryAptoideProducts();
            showIapMessage("Purchase unavailable");
            return;
        }

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(List.of(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            ))
            .setDeveloperPayload("gravity-runner:" + productId)
            .build();

        Thread thread = new Thread(() -> {
            BillingResult billingResult = aptoideBillingClient.launchBillingFlow(MainActivity.this, billingFlowParams);
            runOnUiThread(() -> {
                if (!isAptoideSuccess(billingResult)) {
                    Log.w(APTOIDE_TAG, "Aptoide purchase flow failed for " + productId + ": " + describeAptoideResult(billingResult));
                    showIapMessage("Purchase unavailable");
                }
            });
        });
        thread.start();
    }

    private void restoreAptoidePurchases() {
        if (isSamsungChannel()) {
            restorePurchases();
            return;
        }

        if (!isAptoideChannel()) {
            Log.w(APTOIDE_TAG, "Aptoide restore requested outside Aptoide channel");
            return;
        }
        if (!canUseAptoideBilling("restore")) {
            showIapMessage("No purchase found");
            return;
        }

        try {
            aptoideBillingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(ProductType.INAPP)
                    .build(),
                (billingResult, purchases) -> {
                    if (!isAptoideSuccess(billingResult)) {
                        Log.w(APTOIDE_TAG, "Aptoide restore query failed: " + describeAptoideResult(billingResult));
                        showIapMessage("No purchase found");
                        return;
                    }

                    boolean restored = false;
                    if (purchases != null) {
                        for (Purchase purchase : purchases) {
                            restored |= processAptoidePurchase(purchase, "restore");
                        }
                    }

                    if (!restored) {
                        Log.i(APTOIDE_TAG, "No Aptoide remove ads ownership found");
                        showIapMessage("No purchase found");
                    }
                }
            );
        } catch (RuntimeException exception) {
            Log.w(APTOIDE_TAG, "Aptoide restore failed", exception);
            showIapMessage("No purchase found");
        }
    }

    private void handleAptoidePurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (!isAptoideSuccess(billingResult)) {
            Log.w(APTOIDE_TAG, "Aptoide purchase update failed or canceled: " + describeAptoideResult(billingResult));
            return;
        }
        if (purchases == null || purchases.isEmpty()) {
            Log.w(APTOIDE_TAG, "Aptoide purchase update returned no purchases");
            return;
        }

        for (Purchase purchase : purchases) {
            processAptoidePurchase(purchase, "purchase");
        }
    }

    private boolean processAptoidePurchase(Purchase purchase, String source) {
        if (purchase == null || purchase.getPurchaseState() != 0 || purchase.getProducts() == null) {
            return false;
        }

        boolean handled = false;
        for (String productId : purchase.getProducts()) {
            if (APTOIDE_REMOVE_ADS_LIFETIME_PRODUCT_ID.equals(productId)) {
                Log.i(APTOIDE_TAG, "Aptoide lifetime ownership confirmed from " + source);
                evaluateJavascriptSafely("window.GravityRunnerRewards && window.GravityRunnerRewards.setAdsRemoved(true)");
                handled = true;
            } else if (APTOIDE_REMOVE_ADS_30_DAYS_PRODUCT_ID.equals(productId)) {
                long expiresAt = System.currentTimeMillis() + APTOIDE_REMOVE_ADS_30_DAYS_MS;
                Log.i(APTOIDE_TAG, "Aptoide 30-day ownership confirmed from " + source + " until " + expiresAt);
                evaluateJavascriptSafely("window.GravityRunnerRewards && window.GravityRunnerRewards.setAdsRemovedUntil(" + expiresAt + ")");
                consumeAptoidePurchase(purchase, productId);
                handled = true;
            }
        }

        return handled;
    }

    private void consumeAptoidePurchase(Purchase purchase, String productId) {
        if (purchase.getPurchaseToken() == null || purchase.getPurchaseToken().trim().isEmpty()) {
            Log.w(APTOIDE_TAG, "Cannot consume Aptoide purchase without token for " + productId);
            return;
        }

        // Aptoide Connect must define remove_ads_30_days and remove_ads_lifetime before real testing.
        // The 30-day pass is consumed after delivery so it can be bought again after expiration.
        // Lifetime is intentionally not consumed here so it remains restorable and non-repeatable.
        try {
            aptoideBillingClient.consumeAsync(
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build(),
                (billingResult, purchaseToken) -> {
                    if (isAptoideSuccess(billingResult)) {
                        Log.i(APTOIDE_TAG, "Aptoide purchase consumed for " + productId);
                    } else {
                        Log.w(APTOIDE_TAG, "Aptoide consume failed for " + productId + ": " + describeAptoideResult(billingResult));
                    }
                }
            );
        } catch (RuntimeException exception) {
            Log.w(APTOIDE_TAG, "Aptoide consume failed for " + productId, exception);
        }
    }

    private boolean canUseAptoideBilling(String operation) {
        if (aptoideBillingClient == null || !aptoideBillingReady || !aptoideBillingClient.isReady()) {
            Log.w(APTOIDE_TAG, "Aptoide billing unavailable for " + operation);
            return false;
        }

        return true;
    }

    private boolean isAptoideSuccess(BillingResult billingResult) {
        return billingResult != null && billingResult.getResponseCode() == BillingResponseCode.OK;
    }

    private String describeAptoideResult(BillingResult billingResult) {
        if (billingResult == null) {
            return "null result";
        }

        return "responseCode=" + billingResult.getResponseCode();
    }

    private IapHelper getSamsungIapHelper() {
        if (samsungIapHelper == null) {
            samsungIapHelper = IapHelper.getInstance(this);
            HelperDefine.OperationMode operationMode = BuildConfig.DEBUG
                ? HelperDefine.OperationMode.OPERATION_MODE_TEST
                : HelperDefine.OperationMode.OPERATION_MODE_PRODUCTION;
            samsungIapHelper.setOperationMode(operationMode);
            samsungIapHelper.setShowErrorDialog(false);
            Log.i(IAP_TAG, "Samsung IAP SDK " + samsungIapHelper.getVersionName() + " initialized in " + operationMode + " mode");
        }

        return samsungIapHelper;
    }

    private void checkRemoveAdsOwnershipOnStartup() {
        queryRemoveAdsOwnership("startup");
    }

    private void purchaseRemoveAds() {
        if (isAptoideChannel()) {
            purchaseAptoideRemoveAdsLifetime();
            return;
        }
        if (!isSamsungChannel()) {
            Log.w(IAP_TAG, "Samsung purchase requested outside Samsung channel: " + BuildConfig.DISTRIBUTION_CHANNEL);
            showIapMessage("Purchase unavailable");
            return;
        }

        try {
            boolean started = getSamsungIapHelper().startPayment(REMOVE_ADS_PRODUCT_ID, this::handlePaymentResult);
            if (!started) {
                Log.w(IAP_TAG, "Samsung IAP purchase did not start for " + REMOVE_ADS_PRODUCT_ID);
                showIapMessage("Purchase unavailable");
            }
        } catch (RuntimeException exception) {
            Log.w(IAP_TAG, "Samsung IAP purchase failed to start", exception);
            showIapMessage("Purchase unavailable");
        }
    }

    private void restorePurchases() {
        if (isAptoideChannel()) {
            restoreAptoidePurchases();
            return;
        }
        if (!isSamsungChannel()) {
            Log.w(IAP_TAG, "Samsung restore requested outside Samsung channel: " + BuildConfig.DISTRIBUTION_CHANNEL);
            showIapMessage("No purchase found");
            return;
        }

        queryRemoveAdsOwnership("restore");
    }

    private void queryRemoveAdsOwnership(String source) {
        try {
            boolean started = getSamsungIapHelper().getOwnedList(HelperDefine.PRODUCT_TYPE_ITEM, (error, ownedProducts) -> {
                if (!isIapSuccess(error)) {
                    Log.w(IAP_TAG, "Owned product query failed from " + source + ": " + describeIapError(error));
                    if ("restore".equals(source)) {
                        showIapMessage("No purchase found");
                    }
                    return;
                }

                OwnedProductVo removeAdsProduct = findRemoveAdsOwnership(ownedProducts);
                if (removeAdsProduct == null) {
                    Log.i(IAP_TAG, "No Samsung ownership found for " + REMOVE_ADS_PRODUCT_ID + " from " + source);
                    if ("restore".equals(source)) {
                        showIapMessage("No purchase found");
                    }
                    return;
                }

                confirmOwnedProduct(removeAdsProduct, source);
            });

            if (!started) {
                Log.w(IAP_TAG, "Samsung IAP owned product query did not start from " + source);
            }
        } catch (RuntimeException exception) {
            Log.w(IAP_TAG, "Samsung IAP owned product query failed from " + source, exception);
        }
    }

    private void handlePaymentResult(ErrorVo error, PurchaseVo purchase) {
        if (!isIapSuccess(error)) {
            if (error != null && error.getErrorCode() == HelperDefine.IAP_ERROR_ALREADY_PURCHASED) {
                Log.i(IAP_TAG, REMOVE_ADS_PRODUCT_ID + " already purchased; querying ownership");
                queryRemoveAdsOwnership("already-purchased");
                return;
            }

            Log.w(IAP_TAG, "Samsung IAP payment failed: " + describeIapError(error));
            showIapMessage(error != null && error.getErrorCode() == HelperDefine.IAP_PAYMENT_IS_CANCELED
                ? "Purchase canceled"
                : "Purchase unavailable");
            return;
        }

        if (purchase == null || !REMOVE_ADS_PRODUCT_ID.equals(purchase.getItemId())) {
            Log.w(IAP_TAG, "Samsung IAP returned no matching purchase for " + REMOVE_ADS_PRODUCT_ID);
            return;
        }

        acknowledgePurchaseIfNeeded(purchase.getPurchaseId(), "purchase");
    }

    private OwnedProductVo findRemoveAdsOwnership(ArrayList<OwnedProductVo> ownedProducts) {
        if (ownedProducts == null) {
            return null;
        }

        for (OwnedProductVo ownedProduct : ownedProducts) {
            if (ownedProduct != null && REMOVE_ADS_PRODUCT_ID.equals(ownedProduct.getItemId())) {
                return ownedProduct;
            }
        }

        return null;
    }

    private void confirmOwnedProduct(OwnedProductVo ownedProduct, String source) {
        HelperDefine.AcknowledgedStatus acknowledgedStatus = ownedProduct.getAcknowledgedStatus();
        if (acknowledgedStatus == HelperDefine.AcknowledgedStatus.NOT_ACKNOWLEDGED) {
            acknowledgePurchaseIfNeeded(ownedProduct.getPurchaseId(), source);
            return;
        }

        grantRemoveAdsOwnership(source + " owned query");
    }

    private void acknowledgePurchaseIfNeeded(String purchaseId, String source) {
        if (purchaseId == null || purchaseId.trim().isEmpty()) {
            Log.w(IAP_TAG, "Cannot acknowledge " + REMOVE_ADS_PRODUCT_ID + " without a purchase ID from " + source);
            return;
        }

        try {
            boolean started = getSamsungIapHelper().acknowledgePurchases(purchaseId, (error, acknowledgedPurchases) -> {
                if (!isIapSuccess(error)) {
                    Log.w(IAP_TAG, "Samsung IAP acknowledge failed from " + source + ": " + describeIapError(error));
                    return;
                }

                if (!containsAcknowledgedPurchase(purchaseId, acknowledgedPurchases)) {
                    Log.w(IAP_TAG, "Samsung IAP acknowledge returned no matching purchase for " + REMOVE_ADS_PRODUCT_ID);
                    return;
                }

                grantRemoveAdsOwnership(source + " acknowledge");
            });

            if (!started) {
                Log.w(IAP_TAG, "Samsung IAP acknowledge did not start from " + source);
            }
        } catch (RuntimeException exception) {
            Log.w(IAP_TAG, "Samsung IAP acknowledge failed from " + source, exception);
        }
    }

    private boolean containsAcknowledgedPurchase(String purchaseId, ArrayList<AcknowledgeVo> acknowledgedPurchases) {
        if (acknowledgedPurchases == null) {
            return false;
        }

        for (AcknowledgeVo acknowledgedPurchase : acknowledgedPurchases) {
            if (acknowledgedPurchase != null && purchaseId.equals(acknowledgedPurchase.getPurchaseId())) {
                return true;
            }
        }

        return false;
    }

    private boolean isIapSuccess(ErrorVo error) {
        return error != null && error.getErrorCode() == HelperDefine.IAP_ERROR_NONE;
    }

    private String describeIapError(ErrorVo error) {
        if (error == null) {
            return "null error";
        }

        return error.getErrorCode() + " " + error.getErrorString();
    }

    private void grantRemoveAdsOwnership(String source) {
        Log.i(IAP_TAG, "Samsung ownership confirmed for " + REMOVE_ADS_PRODUCT_ID + " from " + source);
        evaluateJavascriptSafely("window.GravityRunnerRewards && window.GravityRunnerRewards.setAdsRemoved(true)");
    }

    private void showIapMessage(String message) {
        Log.d(IAP_TAG, message);
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private class GravityRunnerNativeBridge {
        @JavascriptInterface
        public String getDistributionChannel() {
            return BuildConfig.DISTRIBUTION_CHANNEL;
        }

        @JavascriptInterface
        public void purchaseRemoveAds() {
            runOnUiThread(MainActivity.this::purchaseRemoveAds);
        }

        @JavascriptInterface
        public void restorePurchases() {
            runOnUiThread(MainActivity.this::restorePurchases);
        }

        @JavascriptInterface
        public void purchaseAptoideRemoveAds30Days() {
            runOnUiThread(MainActivity.this::purchaseAptoideRemoveAds30Days);
        }

        @JavascriptInterface
        public void purchaseAptoideRemoveAdsLifetime() {
            runOnUiThread(MainActivity.this::purchaseAptoideRemoveAdsLifetime);
        }

        @JavascriptInterface
        public void restoreAptoidePurchases() {
            runOnUiThread(MainActivity.this::restoreAptoidePurchases);
        }

        @JavascriptInterface
        public void showInterstitialAd() {
            runOnUiThread(() -> {
                if (interstitialAd == null) {
                    Log.d(TAG, "Interstitial not loaded; requesting a new one");
                    loadInterstitialAd();
                    return;
                }

                interstitialAd.show(MainActivity.this);
            });
        }

        @JavascriptInterface
        public void showRewardedContinueAd() {
            runOnUiThread(() -> showRewardedAd("window.GravityRunnerRewards && window.GravityRunnerRewards.grantContinue()"));
        }

        @JavascriptInterface
        public void showRewardedRetryAd() {
            runOnUiThread(() -> showRewardedAd("window.GravityRunnerRewards && window.GravityRunnerRewards.grantRetries()"));
        }

        @JavascriptInterface
        public void showBannerAd() {
            runOnUiThread(() -> {
                if (bannerAdView == null) {
                    bannerAdView = new AdView(MainActivity.this);
                    bannerAdView.setAdUnitId(BANNER_AD_UNIT_ID);
                    bannerAdView.setAdSize(AdSize.BANNER);
                    FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    );
                    adParams.gravity = Gravity.CENTER_HORIZONTAL;
                    bannerContainer.addView(bannerAdView, adParams);
                    bannerAdView.loadAd(newAdRequest());
                }
                bannerContainer.setVisibility(View.VISIBLE);
            });
        }

        @JavascriptInterface
        public void hideBannerAd() {
            runOnUiThread(() -> {
                if (bannerContainer != null) {
                    bannerContainer.setVisibility(View.GONE);
                }
            });
        }
    }
}
