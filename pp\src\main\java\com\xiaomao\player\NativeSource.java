package com.xiaomao.player;

public class NativeSource {
    public final String title;
    public final String host;
    public final String raw;

    public NativeSource(String title, String host, String raw) {
        this.title = title == null || title.trim().isEmpty() ? "小猫片源" : title.trim();
        this.host = host == null ? "" : host.trim();
        this.raw = raw == null ? "" : raw;
    }
}
