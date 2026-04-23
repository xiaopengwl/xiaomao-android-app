var rule = {
title: '映像星球',
host: 'https://www.yxxq34.cc',
url: '/top/fyclass-----------.html?page=fypage',
detailUrl: '/html/fvid.html',
playUrl: '/play/fvid-fysid-fyid.html',
searchUrl: '/search/-------------.html?wd=**',
headers: {
'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
},
class_name: '电影&电视剧&综艺&动漫&纪录片&短剧&伦理片',
class_url: '1&2&3&4&5&6&7',
tabs: 'div.module-tab-item.tab-item',
tab_text: '.module-tab-item span&&Text',
二级: {
title: 'h1&&Text',
img: '.module-info-poster .module-item-pic img&&data-original',
desc: '.module-info-item:has(> span.module-info-item-title:contains("导演")) .module-info-item-content&&Text',
content: 'div.module-info-introduction-content p&&Text',
director: '.module-info-item:has(> span.module-info-item-title:contains("导演")) .module-info-item-content&&Text',
actor: '.module-info-item:has(> span.module-info-item-title:contains("主演")) .module-info-item-content&&Text',
remarks: '.module-info-item:has(> span.module-info-item-title:contains("备注")) .module-info-item-content&&Text',
area: 'div.module-info-tag-link:eq(1) a&&Text',
year: 'div.module-info-tag-link:eq(0) a&&Text',
tabs: 'div.module-tab-item.tab-item',
lists: 'div.module-list:eq(#id) .module-play-list-link',
tab_text: '.module-tab-item span&&Text',
list_text: 'span&&Text',
list_url: 'a&&href'
},
推荐: 'a.module-poster-item.module-item;a&&title;div.module-item-pic img&&data-original;div.module-item-note&&Text;a&&href',
一级: 'a.module-poster-item.module-item;a&&title;div.module-item-pic img&&data-original;div.module-item-note&&Text;a&&href',
搜索: 'a.module-poster-item.module-item;a&&title;div.module-item-pic img&&data-original;div.module-item-note&&Text;a&&href',
过滤: 'a.module-poster-item.mx-pc-link,a.module-poster-item.mx-mb-link',
play_parse: true,
timeout: 30000,
sniffer: 1,
lazy: $js.toString(() => {
let html = request(input).toLowerCase();
let u = html.indexOf('%6d%33%75%38');
if (u > 0) {
let start = html.lastIndexOf('"', u);
let end = html.indexOf('"', u);
let encoded = html.substring(start + 1, end);
let url = decodeURIComponent(encoded);
input = {jx: 0, parse: 0, url: url};
} else {
input = '';
}
})
};
