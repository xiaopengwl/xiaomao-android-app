var rule = {
    title: '高清仓库',
    host: 'http://gqck32.cc',
    homeUrl: 'http://gqck32.cc/gqck.html',
    url: '/vodtype/fyclass-fypage/',
    detailUrl: 'vod_id',
    playUrl: 'vod_id',
    searchUrl: '/vodsearch/-------------.html?wd=**',
    headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Referer': 'http://gqck32.cc'
    },

    class_name: '日韩AV&国产系列&欧美&动漫&无码中文&有码中文&日本无码&日本有码&吃瓜爆料&欧美高清',
    class_url: '1&2&3&4&8&9&10&7&25&21',

    推荐: 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
    一级: 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
    搜索: 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',

    二级: {
        title: 'h3.title:eq(1)&&Text',
        img: '',
        desc: '',
        content: '',
        director: '',
        actor: '',
        area: '',
        year: '',
        tabs: '',
        lists: '.stui-player__video',
        tab_text: '',
        list_text: '正文',
        list_url: 'body&&Text'
    },

    过滤: '',

    play_parse: true,
    sniffer: 0,

    lazy: $js.toString(() => {
        let txt = input || '';
        let regex = /"url":"(https?:[^"]+)"/i;
        let mat = txt.match(regex);
        if (mat && mat[1]) {
            let url = mat[1].replace(/\\\//g, '/');
            input = {
                jx: 0,
                parse: 0,
                url: url,
                header: rule.headers
            };
        } else {
            input = "";
        }
    })
};
