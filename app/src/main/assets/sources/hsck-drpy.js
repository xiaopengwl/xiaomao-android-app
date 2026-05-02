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

    class_name: '\u65e5\u97e9AV&\u56fd\u4ea7\u7cfb\u5217&\u6b27\u7f8e&\u52a8\u6f2b&\u65e0\u7801\u4e2d\u6587&\u6709\u7801\u4e2d\u6587&\u65e5\u672c\u65e0\u7801&\u65e5\u672c\u6709\u7801&\u5403\u74dc\u7206\u6599&\u6b27\u7f8e\u9ad8\u6e05',
    class_url: '1&2&3&4&8&9&10&7&25&21',

    "\u63a8\u8350": 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
    "\u4e00\u7ea7": 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',
    "\u641c\u7d22": 'a.stui-vodlist__thumb;a&&title;a.stui-vodlist__thumb&&data-original;.pic-text&&Text;a.stui-vodlist__thumb&&href',

    "\u4e8c\u7ea7": {
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
        list_text: '姝ｆ枃',
        list_url: 'body&&Text'
    },

    杩囨护: '',

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
