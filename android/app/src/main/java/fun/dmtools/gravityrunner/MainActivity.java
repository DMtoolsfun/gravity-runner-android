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

public class MainActivity extends BridgeActivity {
    private static final String TAG = "GravityRunnerAds";
    private static final String IAP_TAG = "GravityRunnerIAP";
    private static final String REMOVE_ADS_PRODUCT_ID = "remove_ads";
    private static final String SAMSUNG_IAP_MISSING_MESSAGE = "Samsung IAP SDK not installed yet";
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

    private void showPlaceholderMessage(String message) {
        Log.d(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void checkRemoveAdsOwnershipOnStartup() {
        Log.i(IAP_TAG, SAMSUNG_IAP_MISSING_MESSAGE + "; cannot restore " + REMOVE_ADS_PRODUCT_ID + " ownership on startup");
    }

    private void showSamsungIapMissingMessage(String action) {
        Log.w(IAP_TAG, SAMSUNG_IAP_MISSING_MESSAGE + "; " + action + " unavailable for product " + REMOVE_ADS_PRODUCT_ID);
        showPlaceholderMessage(SAMSUNG_IAP_MISSING_MESSAGE);
    }

    private class GravityRunnerNativeBridge {
        @JavascriptInterface
        public void purchaseRemoveAds() {
            runOnUiThread(() -> showSamsungIapMissingMessage("purchase"));
        }

        @JavascriptInterface
        public void restorePurchases() {
            runOnUiThread(() -> showSamsungIapMissingMessage("restore"));
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
