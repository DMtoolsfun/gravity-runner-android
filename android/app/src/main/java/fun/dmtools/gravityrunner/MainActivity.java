package fun.dmtools.gravityrunner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
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
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends BridgeActivity implements PurchasesUpdatedListener {
    private static final String TAG = "GravityRunnerAds";
    private static final String IAP_TAG = "GravityRunnerIAP";
    private static final String CHANNEL_PLAY = "play";
    private static final String CHANNEL_APKPURE = "apkpure";
    private static final String CHANNEL_DEMO = "demo";
    private static final String RETRY_BUNDLE_PRODUCT_ID = "retries_10";
    private static final int RETRY_BUNDLE_GRANT = 10;
    private static final String AD_PROVIDER_AUTO = "auto";
    private static final String AD_PROVIDER_ADMOB = "admob";
    private static final String AD_PROVIDER_UNITY = "unity";
    // AdMob ad unit IDs come from Gradle build types: debug uses test IDs, release uses production IDs.
    private static final String BANNER_AD_UNIT_ID = BuildConfig.ADMOB_BANNER_ID;
    private static final String INTERSTITIAL_AD_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_ID;
    private static final String REWARDED_AD_UNIT_ID = BuildConfig.ADMOB_REWARDED_ID;
    private static final String UNITY_GAME_ID_ANDROID = BuildConfig.UNITY_GAME_ID_ANDROID;
    private static final String UNITY_INTERSTITIAL_PLACEMENT = BuildConfig.UNITY_INTERSTITIAL_PLACEMENT;
    private static final String UNITY_REWARDED_PLACEMENT = BuildConfig.UNITY_REWARDED_PLACEMENT;
    private static final int BANNER_NAV_FALLBACK_MARGIN_DP = 64;
    private static final int BANNER_NAV_EXTRA_MARGIN_DP = 12;
    private static final long MIN_AD_RETRY_DELAY_MS = 15_000L;
    private static final long MAX_AD_RETRY_DELAY_MS = 120_000L;
    private static final long MIN_REWARDED_SHOW_LOAD_REQUEST_INTERVAL_MS = 30_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout bannerContainer;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private boolean isUnityInitialized;
    private boolean isUnityInitializing;
    private boolean isUnityInterstitialLoaded;
    private boolean isUnityRewardedLoaded;
    private boolean isLoadingInterstitial;
    private boolean isLoadingRewarded;
    private boolean isLoadingUnityInterstitial;
    private boolean isLoadingUnityRewarded;
    private BillingClient billingClient;
    private ProductDetails retryBundleProductDetails;
    private boolean isBillingConnecting;
    private boolean isBillingReady;
    private boolean isRetryBundleAvailable;
    private final Set<String> consumingPurchaseTokens = new HashSet<>();
    private long lastRewardedShowLoadRequestAt;
    private int bannerLoadFailures;
    private int interstitialLoadFailures;
    private int rewardedLoadFailures;
    private int unityInterstitialLoadFailures;
    private int unityRewardedLoadFailures;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getBridge().getWebView().addJavascriptInterface(new GravityRunnerNativeBridge(), "GravityRunnerNative");
        setupPlayBilling();
        if (!isDemoChannel()) {
            logAdProviderSelection();
            if (shouldUseAdMobAds()) {
                MobileAds.initialize(this, initializationStatus -> Log.d(TAG, "AdMob initialized"));
                setupBannerContainer();
                loadInterstitialAd();
                loadRewardedAd();
            }
            if (shouldUseUnityAds()) {
                initializeUnityAds();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isPlayChannel() && isBillingReady) {
            queryUnconsumedRetryPurchases();
        }
    }

    @Override
    public void onDestroy() {
        if (billingClient != null) {
            billingClient.endConnection();
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

    private String configuredAdProvider() {
        String provider = BuildConfig.AD_PROVIDER == null ? AD_PROVIDER_AUTO : BuildConfig.AD_PROVIDER.trim().toLowerCase();
        if (AD_PROVIDER_ADMOB.equals(provider) || AD_PROVIDER_UNITY.equals(provider)) {
            return provider;
        }
        return AD_PROVIDER_AUTO;
    }

    private boolean shouldUseAdMobAds() {
        return !AD_PROVIDER_UNITY.equals(configuredAdProvider());
    }

    private boolean shouldUseUnityAds() {
        return !AD_PROVIDER_ADMOB.equals(configuredAdProvider()) && isUnityConfigured();
    }

    private boolean isUnityConfigured() {
        return isNonBlank(UNITY_GAME_ID_ANDROID)
            && isNonBlank(UNITY_INTERSTITIAL_PLACEMENT)
            && isNonBlank(UNITY_REWARDED_PLACEMENT);
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isUnityTestMode() {
        return BuildConfig.DEBUG || BuildConfig.UNITY_ADS_TEST_MODE;
    }

    private void logAdProviderSelection() {
        Log.i(TAG, "Ad provider selected: " + configuredAdProvider()
            + " (AdMob primary=" + shouldUseAdMobAds()
            + ", Unity fallback=" + shouldUseUnityAds()
            + ", Unity configured=" + isUnityConfigured()
            + ", Unity testMode=" + isUnityTestMode() + ")");
    }

    private AdRequest newAdRequest() {
        return new AdRequest.Builder().build();
    }

    private long getAdRetryDelayMs(int failureCount) {
        long multiplier = 1L << Math.min(Math.max(failureCount - 1, 0), 3);
        return Math.min(MAX_AD_RETRY_DELAY_MS, MIN_AD_RETRY_DELAY_MS * multiplier);
    }

    private String describeLoadAdError(LoadAdError loadAdError) {
        if (loadAdError == null) {
            return "unknown load error";
        }

        return "code=" + loadAdError.getCode()
            + ", domain=" + loadAdError.getDomain()
            + ", message=" + loadAdError.getMessage()
            + ", responseInfo=" + loadAdError.getResponseInfo();
    }

    private void scheduleAdLoadRetry(String adFormat, int failureCount, Runnable retryAction) {
        if (isDemoChannel()) {
            return;
        }

        long retryDelayMs = getAdRetryDelayMs(failureCount);
        Log.i(TAG, adFormat + " retry scheduled in " + retryDelayMs + "ms after " + failureCount + " load failure(s)");
        mainHandler.postDelayed(retryAction, retryDelayMs);
    }

    private void initializeUnityAds() {
        if (!shouldUseUnityAds() || isUnityInitialized || isUnityInitializing) {
            return;
        }

        isUnityInitializing = true;
        Log.i(TAG, "Unity Ads initializing with gameId=" + UNITY_GAME_ID_ANDROID
            + ", interstitialPlacement=" + UNITY_INTERSTITIAL_PLACEMENT
            + ", rewardedPlacement=" + UNITY_REWARDED_PLACEMENT
            + ", testMode=" + isUnityTestMode());
        UnityAds.initialize(getApplicationContext(), UNITY_GAME_ID_ANDROID, isUnityTestMode(), new IUnityAdsInitializationListener() {
            @Override
            public void onInitializationComplete() {
                isUnityInitializing = false;
                isUnityInitialized = true;
                Log.i(TAG, "Unity Ads initialized");
                loadUnityInterstitialAd();
                loadUnityRewardedAd();
            }

            @Override
            public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                isUnityInitializing = false;
                isUnityInitialized = false;
                Log.w(TAG, "Unity Ads initialization failed: [" + error + "] " + message);
            }
        });
    }

    private void loadUnityInterstitialAd() {
        if (!shouldUseUnityAds()) {
            return;
        }
        if (!isUnityInitialized) {
            initializeUnityAds();
            return;
        }
        if (isLoadingUnityInterstitial || isUnityInterstitialLoaded) {
            return;
        }

        isLoadingUnityInterstitial = true;
        Log.d(TAG, "Unity Ads interstitial load requested");
        UnityAds.load(UNITY_INTERSTITIAL_PLACEMENT, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                isLoadingUnityInterstitial = false;
                unityInterstitialLoadFailures = 0;
                isUnityInterstitialLoaded = true;
                Log.d(TAG, "Unity Ads interstitial loaded: " + placementId);
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                isLoadingUnityInterstitial = false;
                isUnityInterstitialLoaded = false;
                unityInterstitialLoadFailures += 1;
                Log.w(TAG, "Unity Ads interstitial failed to load: " + placementId + " [" + error + "] " + message);
                scheduleAdLoadRetry("Unity interstitial", unityInterstitialLoadFailures, MainActivity.this::loadUnityInterstitialAd);
            }
        });
    }

    private void loadUnityRewardedAd() {
        if (!shouldUseUnityAds()) {
            return;
        }
        if (!isUnityInitialized) {
            initializeUnityAds();
            return;
        }
        if (isLoadingUnityRewarded || isUnityRewardedLoaded) {
            return;
        }

        isLoadingUnityRewarded = true;
        Log.d(TAG, "Unity Ads rewarded load requested");
        UnityAds.load(UNITY_REWARDED_PLACEMENT, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                isLoadingUnityRewarded = false;
                unityRewardedLoadFailures = 0;
                isUnityRewardedLoaded = true;
                Log.d(TAG, "Unity Ads rewarded loaded: " + placementId);
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                isLoadingUnityRewarded = false;
                isUnityRewardedLoaded = false;
                unityRewardedLoadFailures += 1;
                Log.w(TAG, "Unity Ads rewarded failed to load: " + placementId + " [" + error + "] " + message);
                scheduleAdLoadRetry("Unity rewarded", unityRewardedLoadFailures, MainActivity.this::loadUnityRewardedAd);
            }
        });
    }

    private void loadInterstitialAd() {
        if (!shouldUseAdMobAds()) {
            return;
        }
        if (isLoadingInterstitial || interstitialAd != null) {
            return;
        }

        isLoadingInterstitial = true;
        Log.d(TAG, "AdMob interstitial load requested");
        InterstitialAd.load(this, INTERSTITIAL_AD_UNIT_ID, newAdRequest(), new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(InterstitialAd ad) {
                isLoadingInterstitial = false;
                interstitialLoadFailures = 0;
                interstitialAd = ad;
                interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        Log.d(TAG, "AdMob interstitial dismissed");
                        interstitialAd = null;
                        loadInterstitialAd();
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        Log.i(TAG, "AdMob interstitial showed");
                        evaluateJavascriptSafely("window.GravityRunnerRewards && window.GravityRunnerRewards.markInterstitialShown()");
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(AdError adError) {
                        Log.w(TAG, "AdMob interstitial failed to show: " + adError.getMessage());
                        interstitialAd = null;
                        loadInterstitialAd();
                        loadUnityInterstitialAd();
                    }
                });
                Log.d(TAG, "AdMob interstitial loaded");
            }

            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                isLoadingInterstitial = false;
                interstitialAd = null;
                interstitialLoadFailures += 1;
                Log.w(TAG, "AdMob interstitial failed to load: " + describeLoadAdError(loadAdError));
                loadUnityInterstitialAd();
                scheduleAdLoadRetry("AdMob interstitial", interstitialLoadFailures, MainActivity.this::loadInterstitialAd);
            }
        });
    }

    private void loadRewardedAd() {
        if (!shouldUseAdMobAds()) {
            return;
        }
        if (isLoadingRewarded || rewardedAd != null) {
            return;
        }

        isLoadingRewarded = true;
        Log.d(TAG, "AdMob rewarded load requested");
        RewardedAd.load(this, REWARDED_AD_UNIT_ID, newAdRequest(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd ad) {
                isLoadingRewarded = false;
                rewardedLoadFailures = 0;
                rewardedAd = ad;
                Log.d(TAG, "AdMob rewarded loaded");
            }

            @Override
            public void onAdFailedToLoad(LoadAdError loadAdError) {
                isLoadingRewarded = false;
                rewardedAd = null;
                rewardedLoadFailures += 1;
                Log.w(TAG, "AdMob rewarded failed to load: " + describeLoadAdError(loadAdError));
                loadUnityRewardedAd();
                scheduleAdLoadRetry("AdMob rewarded", rewardedLoadFailures, MainActivity.this::loadRewardedAd);
            }
        });
    }

    private void loadBannerAd() {
        if (bannerAdView == null || isDemoChannel() || !shouldUseAdMobAds()) {
            return;
        }

        Log.d(TAG, "AdMob banner load requested");
        bannerAdView.loadAd(newAdRequest());
    }

    private boolean isDemoChannel() {
        return BuildConfig.IS_DEMO_DISTRIBUTION || CHANNEL_DEMO.equalsIgnoreCase(BuildConfig.DISTRIBUTION_CHANNEL);
    }

    private boolean isPlayChannel() {
        return CHANNEL_PLAY.equalsIgnoreCase(BuildConfig.DISTRIBUTION_CHANNEL);
    }

    private void setupPlayBilling() {
        if (!isPlayChannel()) {
            isRetryBundleAvailable = false;
            return;
        }

        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build();
        startBillingConnection();
    }

    private void startBillingConnection() {
        if (!isPlayChannel() || billingClient == null || isBillingReady || isBillingConnecting) {
            publishRetryBundleAvailability();
            return;
        }

        isBillingConnecting = true;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                isBillingConnecting = false;
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    Log.w(IAP_TAG, "Billing setup failed: " + billingResult.getDebugMessage());
                    isBillingReady = false;
                    isRetryBundleAvailable = false;
                    publishRetryBundleAvailability();
                    return;
                }

                isBillingReady = true;
                queryRetryBundleProductDetails();
                queryUnconsumedRetryPurchases();
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(IAP_TAG, "Billing service disconnected");
                isBillingConnecting = false;
                isBillingReady = false;
                isRetryBundleAvailable = false;
                publishRetryBundleAvailability();
            }
        });
    }

    private void queryRetryBundleProductDetails() {
        if (!isPlayChannel() || billingClient == null || !isBillingReady) {
            isRetryBundleAvailable = false;
            publishRetryBundleAvailability();
            return;
        }

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(RETRY_BUNDLE_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(product))
            .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, queryProductDetailsResult) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                Log.w(IAP_TAG, "Retry bundle product query failed: " + billingResult.getDebugMessage());
                retryBundleProductDetails = null;
                isRetryBundleAvailable = false;
                publishRetryBundleAvailability();
                return;
            }

            retryBundleProductDetails = null;
            List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
            for (ProductDetails productDetails : productDetailsList) {
                if (RETRY_BUNDLE_PRODUCT_ID.equals(productDetails.getProductId())) {
                    retryBundleProductDetails = productDetails;
                    break;
                }
            }
            isRetryBundleAvailable = retryBundleProductDetails != null;
            publishRetryBundleAvailability();
        });
    }

    private void queryUnconsumedRetryPurchases() {
        if (!isPlayChannel() || billingClient == null || !isBillingReady) {
            return;
        }

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build();
        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                Log.w(IAP_TAG, "Retry purchase query failed: " + billingResult.getDebugMessage());
                return;
            }

            for (Purchase purchase : purchases) {
                processRetryBundlePurchase(purchase);
            }
        });
    }

    private void purchaseRetryBundle() {
        if (!isPlayChannel()) {
            notifyRetryPurchaseMessage("Purchase unavailable.");
            return;
        }
        if (billingClient == null || !isBillingReady) {
            notifyRetryPurchaseMessage("Purchase unavailable.");
            startBillingConnection();
            return;
        }
        if (retryBundleProductDetails == null) {
            notifyRetryPurchaseMessage("Purchase unavailable.");
            queryRetryBundleProductDetails();
            return;
        }

        BillingFlowParams.ProductDetailsParams.Builder productDetailsParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(retryBundleProductDetails);
        List<ProductDetails.OneTimePurchaseOfferDetails> offerDetails =
            retryBundleProductDetails.getOneTimePurchaseOfferDetailsList();
        if (offerDetails != null && !offerDetails.isEmpty()) {
            productDetailsParams.setOfferToken(offerDetails.get(0).getOfferToken());
        }

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(productDetailsParams.build()))
            .build();
        BillingResult billingResult = billingClient.launchBillingFlow(this, billingFlowParams);
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            Log.w(IAP_TAG, "Retry bundle billing flow failed: " + billingResult.getDebugMessage());
            notifyRetryPurchaseMessage("Purchase unavailable.");
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        int responseCode = billingResult.getResponseCode();
        if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                processRetryBundlePurchase(purchase);
            }
            return;
        }
        if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            notifyRetryPurchaseMessage("Purchase canceled.");
            return;
        }

        Log.w(IAP_TAG, "Retry purchase failed: " + billingResult.getDebugMessage());
        notifyRetryPurchaseMessage("Purchase unavailable.");
    }

    private void processRetryBundlePurchase(Purchase purchase) {
        if (purchase == null || !purchase.getProducts().contains(RETRY_BUNDLE_PRODUCT_ID)) {
            return;
        }
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            notifyRetryPurchaseMessage("Purchase pending.");
            return;
        }
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            return;
        }

        String purchaseToken = purchase.getPurchaseToken();
        if (consumingPurchaseTokens.contains(purchaseToken)) {
            return;
        }
        consumingPurchaseTokens.add(purchaseToken);

        ConsumeParams consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build();
        billingClient.consumeAsync(consumeParams, (billingResult, consumedToken) -> {
            consumingPurchaseTokens.remove(consumedToken);
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                Log.w(IAP_TAG, "Retry bundle consume failed: " + billingResult.getDebugMessage());
                notifyRetryPurchaseMessage("Purchase pending. Try again soon.");
                return;
            }

            int grant = RETRY_BUNDLE_GRANT * Math.max(1, purchase.getQuantity());
            evaluateJavascriptSafely(
                "window.GravityRunnerRewards && window.GravityRunnerRewards.grantPurchasedRetries(" + grant + ")"
            );
            notifyRetryPurchaseMessage("Retry credits added.");
        });
    }

    private void publishRetryBundleAvailability() {
        evaluateJavascriptSafely(
            "window.GravityRunnerRewards && window.GravityRunnerRewards.setRetryPurchaseAvailable(" + isRetryBundleAvailable + ")"
        );
    }

    private void notifyRetryPurchaseMessage(String message) {
        evaluateJavascriptSafely(
            "window.GravityRunnerRewards && window.GravityRunnerRewards.retryPurchaseMessage(" + jsString(message) + ")"
        );
    }

    private String jsString(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", "\\n") + "'";
    }

    private void showRewardedAd(String rewardJavascript, String unavailableJavascript) {
        if (shouldUseAdMobAds() && rewardedAd != null) {
            Log.i(TAG, "Rewarded provider selected: AdMob");
            showAdMobRewardedAd(rewardJavascript, unavailableJavascript);
            return;
        }
        if (shouldUseUnityAds() && isUnityRewardedLoaded) {
            Log.i(TAG, "Rewarded provider selected: Unity Ads fallback");
            showUnityRewardedAd(rewardJavascript, unavailableJavascript);
            return;
        }

        Log.i(TAG, "Rewarded unavailable for configured providers; notifying game UI");
        evaluateJavascriptSafely(unavailableJavascript);
        requestRewardedAdLoadsAfterUnavailableShow();
    }

    private void requestRewardedAdLoadsAfterUnavailableShow() {
        long now = System.currentTimeMillis();
        if (now - lastRewardedShowLoadRequestAt < MIN_REWARDED_SHOW_LOAD_REQUEST_INTERVAL_MS) {
            Log.d(TAG, "Rewarded not loaded; load request suppressed");
            return;
        }

        Log.d(TAG, "Rewarded not loaded; requesting provider loads");
        lastRewardedShowLoadRequestAt = now;
        loadRewardedAd();
        loadUnityRewardedAd();
    }

    private void showAdMobRewardedAd(String rewardJavascript, String unavailableJavascript) {
        RewardedAd adToShow = rewardedAd;
        rewardedAd = null;
        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "AdMob rewarded dismissed");
                loadRewardedAd();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                Log.i(TAG, "AdMob rewarded showed");
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                Log.w(TAG, "AdMob rewarded failed to show: " + adError.getMessage());
                evaluateJavascriptSafely(unavailableJavascript);
                loadRewardedAd();
                loadUnityRewardedAd();
            }
        });
        adToShow.show(this, rewardItem -> {
            Log.i(TAG, "AdMob rewarded completed; granting reward");
            evaluateJavascriptSafely(rewardJavascript);
        });
    }

    private void showUnityRewardedAd(String rewardJavascript, String unavailableJavascript) {
        if (!isUnityRewardedLoaded) {
            Log.d(TAG, "Unity Ads rewarded not loaded at show time");
            evaluateJavascriptSafely(unavailableJavascript);
            loadUnityRewardedAd();
            return;
        }

        isUnityRewardedLoaded = false;
        UnityAds.show(this, UNITY_REWARDED_PLACEMENT, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
            @Override
            public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                Log.w(TAG, "Unity Ads rewarded failed to show: " + placementId + " [" + error + "] " + message);
                evaluateJavascriptSafely(unavailableJavascript);
                loadUnityRewardedAd();
            }

            @Override
            public void onUnityAdsShowStart(String placementId) {
                Log.i(TAG, "Unity Ads rewarded showed: " + placementId);
            }

            @Override
            public void onUnityAdsShowClick(String placementId) {
                Log.d(TAG, "Unity Ads rewarded clicked: " + placementId);
            }

            @Override
            public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                Log.i(TAG, "Unity Ads rewarded completed: " + placementId + " state=" + state);
                if (UnityAds.UnityAdsShowCompletionState.COMPLETED.equals(state)) {
                    Log.i(TAG, "Unity Ads rewarded completion accepted; granting reward");
                    evaluateJavascriptSafely(rewardJavascript);
                } else {
                    Log.i(TAG, "Unity Ads rewarded not completed; no reward granted");
                }
                loadUnityRewardedAd();
            }
        });
    }

    private boolean showUnityInterstitialAd() {
        if (!isUnityInterstitialLoaded) {
            Log.d(TAG, "Unity Ads interstitial not loaded at show time");
            loadUnityInterstitialAd();
            return false;
        }

        isUnityInterstitialLoaded = false;
        UnityAds.show(this, UNITY_INTERSTITIAL_PLACEMENT, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
            @Override
            public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                Log.w(TAG, "Unity Ads interstitial failed to show: " + placementId + " [" + error + "] " + message);
                loadUnityInterstitialAd();
            }

            @Override
            public void onUnityAdsShowStart(String placementId) {
                Log.i(TAG, "Unity Ads interstitial showed: " + placementId);
                evaluateJavascriptSafely("window.GravityRunnerRewards && window.GravityRunnerRewards.markInterstitialShown()");
            }

            @Override
            public void onUnityAdsShowClick(String placementId) {
                Log.d(TAG, "Unity Ads interstitial clicked: " + placementId);
            }

            @Override
            public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                Log.d(TAG, "Unity Ads interstitial completed: " + placementId + " state=" + state);
                loadUnityInterstitialAd();
            }
        });
        return true;
    }

    private void evaluateJavascriptSafely(String javascript) {
        WebView webView = getBridge().getWebView();
        if (webView == null) {
            Log.w(TAG, "WebView unavailable for JavaScript callback");
            return;
        }

        webView.post(() -> webView.evaluateJavascript(javascript, null));
    }

    private void purchaseRemoveAds() {
        Log.i(IAP_TAG, "Remove Ads purchase unavailable for " + BuildConfig.DISTRIBUTION_CHANNEL + " channel");
        showIapMessage("Purchase unavailable");
    }

    private void restorePurchases() {
        Log.i(IAP_TAG, "Restore unavailable for " + BuildConfig.DISTRIBUTION_CHANNEL + " channel");
        showIapMessage("No purchase found");
    }

    private void purchaseAptoideRemoveAds30Days() {
        purchaseRemoveAds();
    }

    private void purchaseAptoideRemoveAdsLifetime() {
        purchaseRemoveAds();
    }

    private void restoreAptoidePurchases() {
        restorePurchases();
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
        public void logRetryOfferEvent(String eventName, String payloadJson) {
            Log.d(TAG, "Retry offer " + eventName + ": " + payloadJson);
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
            if (isDemoChannel()) {
                Log.d(TAG, "Interstitial suppressed for demo channel");
                return;
            }
            runOnUiThread(() -> {
                if (shouldUseAdMobAds() && interstitialAd != null) {
                    Log.i(TAG, "Interstitial provider selected: AdMob");
                    interstitialAd.show(MainActivity.this);
                    return;
                }
                if (shouldUseUnityAds() && showUnityInterstitialAd()) {
                    Log.i(TAG, "Interstitial provider selected: Unity Ads fallback");
                    return;
                }

                Log.d(TAG, "Interstitial not loaded; requesting configured provider loads");
                if (shouldUseAdMobAds()) {
                    loadInterstitialAd();
                }
                if (shouldUseUnityAds()) {
                    loadUnityInterstitialAd();
                }
                Log.i(TAG, "Interstitial unavailable; game continues without ad");
            });
        }

        @JavascriptInterface
        public void showRewardedContinueAd() {
            if (isDemoChannel()) {
                Log.d(TAG, "Rewarded continue suppressed for demo channel");
                return;
            }
            runOnUiThread(() -> showRewardedAd(
                "window.GravityRunnerRewards && window.GravityRunnerRewards.grantContinue()",
                "window.GravityRunnerRewards && window.GravityRunnerRewards.rewardedAdUnavailable()"
            ));
        }

        @JavascriptInterface
        public void showRewardedRetryAd() {
            if (isDemoChannel()) {
                Log.d(TAG, "Rewarded retry suppressed for demo channel");
                return;
            }
            runOnUiThread(() -> showRewardedAd(
                "window.GravityRunnerRewards && window.GravityRunnerRewards.grantRetries()",
                "window.GravityRunnerRewards && window.GravityRunnerRewards.rewardedAdUnavailable()"
            ));
        }

        @JavascriptInterface
        public void showRewardedRetryOfferAd() {
            if (isDemoChannel()) {
                Log.d(TAG, "Rewarded retry offer suppressed for demo channel");
                return;
            }
            runOnUiThread(() -> showRewardedAd(
                "window.GravityRunnerRewards && window.GravityRunnerRewards.grantRetryOfferRetries()",
                "window.GravityRunnerRewards && window.GravityRunnerRewards.rewardedAdUnavailable()"
            ));
        }

        @JavascriptInterface
        public void requestRetryBundleStatus() {
            runOnUiThread(() -> {
                if (!isPlayChannel()) {
                    isRetryBundleAvailable = false;
                    publishRetryBundleAvailability();
                    return;
                }
                if (billingClient == null || !isBillingReady) {
                    startBillingConnection();
                    return;
                }
                queryRetryBundleProductDetails();
            });
        }

        @JavascriptInterface
        public void purchaseRetryBundle() {
            runOnUiThread(MainActivity.this::purchaseRetryBundle);
        }

        @JavascriptInterface
        public void showBannerAd() {
            if (isDemoChannel()) {
                Log.d(TAG, "Banner suppressed for demo channel");
                return;
            }
            if (!shouldUseAdMobAds()) {
                Log.i(TAG, "Banner suppressed because AdMob is bypassed; Unity fullscreen fallback remains available");
                return;
            }
            runOnUiThread(() -> {
                if (bannerContainer == null) {
                    setupBannerContainer();
                }
                if (bannerAdView == null) {
                    Log.i(TAG, "Banner provider selected: AdMob");
                    bannerAdView = new AdView(MainActivity.this);
                    bannerAdView.setAdUnitId(BANNER_AD_UNIT_ID);
                    bannerAdView.setAdSize(AdSize.BANNER);
                    bannerAdView.setAdListener(new AdListener() {
                        @Override
                        public void onAdLoaded() {
                            bannerLoadFailures = 0;
                            Log.d(TAG, "AdMob banner loaded");
                        }

                        @Override
                        public void onAdFailedToLoad(LoadAdError loadAdError) {
                            bannerLoadFailures += 1;
                            Log.w(TAG, "AdMob banner failed to load: " + describeLoadAdError(loadAdError));
                            scheduleAdLoadRetry("AdMob banner", bannerLoadFailures, MainActivity.this::loadBannerAd);
                        }
                    });
                    FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    );
                    adParams.gravity = Gravity.CENTER_HORIZONTAL;
                    bannerContainer.addView(bannerAdView, adParams);
                    loadBannerAd();
                }
                bannerContainer.setVisibility(View.VISIBLE);
            });
        }

        @JavascriptInterface
        public void hideBannerAd() {
            if (isDemoChannel()) {
                return;
            }
            runOnUiThread(() -> {
                if (bannerContainer != null) {
                    bannerContainer.setVisibility(View.GONE);
                }
            });
        }
    }
}
