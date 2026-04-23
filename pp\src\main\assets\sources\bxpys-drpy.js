var rule = {
    title: '百思派电影网',
    host: 'https://www.bestpipe.cn',
    url: '/vodtype/fyclass-fypage.html',
    searchUrl: '/vodsearch/-------------.html;post',
    searchable: 2,
    quickSearch: 0,
    filterable: 1,
    filter_url: '/vodshow/fyclass-fycate-fyarea-fyyear-fyby-----------.html',
    headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36',
        'Referer': 'http://127.0.0.1:9978'
    },
    timeout: 15000,
    class_name: '电影&剧集&短剧&动漫&综艺',
    class_url: '20&21&24&22&23',
    play_parse: true,
    sniffer: 0,
    lazy: '',
    play_headers: {
        'Referer': 'https://www.bestpipe.cn/',
        'User-Agent': 'Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36'
    },
    "推荐": '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href',
    "一级": '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href',
    "二级": {
        title: '.stui-content__detail h1.title&&Text',
        img: '.stui-content__thumb a.pic img&&data-original',
        desc: '.stui-content__detail p.data--span',
        content: '.detail-sketch,.detail-content--span',
        tabs: '.nav-tabs li',
        lists: '.stui-content__playlist:eq(#id)&&>li',
        tab_text: 'a&&Text',
        list_text: 'a&&Text',
        list_url: 'a&&href'
    },
    "搜索": '.stui-vodlist__box;a&&title;a&&data-original;.pic-text&&Text;a&&href'
};
