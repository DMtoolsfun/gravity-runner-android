package fun.dmtools.gravityrunner;

import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

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

public class MainActivity extends BridgeActivity {
    private static final String TAG = "GravityRunnerAds";
    private static final String IAP_TAG = "GravityRunnerIAP";
    private static final String REMOVE_ADS_PRODUCT_ID = "remove_ads";
    private static final String BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    private static final int BANNER_NAV_FALLBACK_MARGIN_DP = 64;
    private static final int BANNER_NAV_EXTRA_MARGIN_DP = 12;

    private FrameLayout bannerContainer;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private boolean isLoadingInterstitial;
    private boolean isLoadingRewarded;
    private IapHelper samsungIapHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MobileAds.initialize(this, initializationStatus -> Log.d(TAG, "Mobile Ads initialized"));
        setupBannerContainer();
        getBridge().getWebView().addJavascriptInterface(new GravityRunnerNativeBridge(), "GravityRunnerNative");
        loadInterstitialAd();
        loadRewardedAd();
        checkRemoveAdsOwnershipOnStartup();
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

    private IapHelper getSamsungIapHelper() {
        if (samsungIapHelper == null) {
            samsungIapHelper = IapHelper.getInstance(this);
            samsungIapHelper.setOperationMode(HelperDefine.OperationMode.OPERATION_MODE_TEST);
            samsungIapHelper.setShowErrorDialog(false);
            Log.i(IAP_TAG, "Samsung IAP SDK " + samsungIapHelper.getVersionName() + " initialized in test mode");
        }

        return samsungIapHelper;
    }

    private void checkRemoveAdsOwnershipOnStartup() {
        queryRemoveAdsOwnership("startup");
    }

    private void purchaseRemoveAds() {
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private class GravityRunnerNativeBridge {
        @JavascriptInterface
        public void purchaseRemoveAds() {
            runOnUiThread(MainActivity.this::purchaseRemoveAds);
        }

        @JavascriptInterface
        public void restorePurchases() {
            runOnUiThread(MainActivity.this::restorePurchases);
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
