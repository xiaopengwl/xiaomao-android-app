package com.xiaomao.player;

import android.content.Context;

import xyz.doikki.videoplayer.player.PlayerFactory;

public class XiaomaoDkExoPlayerFactory extends PlayerFactory<XiaomaoDkExoPlayer> {
    public static XiaomaoDkExoPlayerFactory create() {
        return new XiaomaoDkExoPlayerFactory();
    }

    @Override
    public XiaomaoDkExoPlayer createPlayer(Context context) {
        return new XiaomaoDkExoPlayer(context);
    }
}
