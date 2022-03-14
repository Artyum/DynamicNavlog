package com.artyum.dynamicnavlog

object Release {
    // adView
    // Key in activity_main.xml

    //const val GOOGLE_PLAY_PRODUCT_ID = "ads_remove_test2"
    //const val GOOGLE_PLAY_PRODUCT_ID = "ads_remove_test3"
    //const val GOOGLE_PLAY_PRODUCT_ID = "ads_remove_test4"
    //const val GOOGLE_PLAY_PRODUCT_ID = "ads_remove_test5"
    //const val GOOGLE_PLAY_PRODUCT_ID = "not_exists"

    // PROD
    const val GOOGLE_PLAY_PRODUCT_ID = "dynamic_navlog_pro"
}

// Release
val releaseOptions = ReleaseOptions(
    initializeAds = true,
    startBillingClient = true
)