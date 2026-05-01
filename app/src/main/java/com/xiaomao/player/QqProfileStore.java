package com.xiaomao.player;

import android.content.Context;
import android.content.SharedPreferences;

public final class QqProfileStore {
    private static final String PREFS = "xiaomao_qq_profile";
    private static final String KEY_QQ = "qq";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_SIGNATURE = "signature";

    public static final class Profile {
        public final String qq;
        public final String avatarUrl;
        public final String nickname;
        public final String signature;

        Profile(String qq, String avatarUrl, String nickname, String signature) {
            this.qq = safe(qq);
            this.avatarUrl = safe(avatarUrl);
            this.nickname = safe(nickname);
            this.signature = safe(signature);
        }

        public boolean isEmpty() {
            return qq.isEmpty() && avatarUrl.isEmpty() && nickname.isEmpty() && signature.isEmpty();
        }
    }

    private QqProfileStore() {
    }

    public static Profile load(Context context) {
        SharedPreferences preferences = prefs(context);
        return new Profile(
                preferences.getString(KEY_QQ, ""),
                preferences.getString(KEY_AVATAR, ""),
                preferences.getString(KEY_NICKNAME, ""),
                preferences.getString(KEY_SIGNATURE, "")
        );
    }

    public static void save(Context context, Profile profile) {
        if (context == null || profile == null) {
            return;
        }
        prefs(context).edit()
                .putString(KEY_QQ, profile.qq)
                .putString(KEY_AVATAR, profile.avatarUrl)
                .putString(KEY_NICKNAME, profile.nickname)
                .putString(KEY_SIGNATURE, profile.signature)
                .apply();
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
