var rule = {
  title: '麻豆视频',
  host: 'https://www.madou8.top/enter',
  url: '/type/fyclass-fypage.html',
  searchUrl: '/search/-.html?wd=**',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.madou8.top/enter',
  },
  timeout: 15000,
  class_name: '楹昏眴瑙嗛&91鍒剁墖鍘?澶╃編浼犲獟&铚滄浼犲獟&鐨囧鍗庝汉&鏄熺┖浼犲獟&绮句笢褰变笟&涔愭挱浼犲獟&涔岄甫浼犲獟&鍏斿瓙鍏堢敓&鏉忓惂鍘熷垱&鐜╁伓濮愬&mini浼犲獟&澶ц薄浼犲獟&寮€蹇冮浼犲獟&PsychoPorn&绯栧績Vlog&钀濊帀绀?鎬ц鐣?鏃ユ湰鏃犵爜&鍥戒骇瑙嗛&娆х編楂樻竻&鎴愪汉鍔ㄦ极',
  class_url: '1&2&3&4&5&6&7&8&9&10&11&12&13&14&15&16&17&18&20&21&22&23&24',
  play_parse: false,
  sniffer: 0,
  lazy: 'js:let s = input.replace(/\\\\/g,"");let r = "";for(let i=0;i<s.length;i++){let c = s.charCodeAt(i); r += String.fromCharCode(c > 127 ? (c ^ 83) : c);} try { r = atob(r); } catch(e) {} input = { parse: 0, url: r };',
  play_headers: {
    'Referer': 'https://www.madou8.top/enter',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  },
  "\u63a8\u8350": '.stui-vodlist__box;a&&title;a&&data-original;;a&&href',
  "\u4e8c\u7ea7": {
    title: 'h1.title a&&Text',
    img: '.stui-player__video img&&src',
    desc: '',
    content: '',
    tabs: '',
    lists: '.stui-player__play&&.title'
  },
  "\u641c\u7d22": '.stui-vodlist__box;a&&title;a&&data-original;;a&&href',
};
