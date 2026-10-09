package com.imran.clothstore.data.memo

/**
 * Google Apps Script (ছবির স্টোরেজ) সংযোগের তথ্য — apps-script/Code.gs ডেপ্লয়ের পর এখানে বসান।
 * SCRIPT_KEY অবশ্যই Code.gs-এর SECRET-এর সাথে হুবহু এক হতে হবে।
 */
object MemoConfig {
    const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycbyL9gu_ZunP_ZwTXSDZBFQuFBY-SLkk2WDwCiKdBdDqR_awYBJ9mztqm9FLdytxY84q/exec"
    const val SCRIPT_KEY = "CHANGE_ME"

    val isConfigured: Boolean
        get() = SCRIPT_URL.startsWith("https://script.google.com/") && SCRIPT_KEY != "CHANGE_ME"
}
