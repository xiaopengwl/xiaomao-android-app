var rule = {
  title: '可可影视',
  host: 'https://www.kkys01.com',
  url: '/show/fyclass-fyfilter-fypage.html',
  filter_url: '{{fl.class}}-{{fl.area}}-{{fl.lang}}-{{fl.year}}-{{fl.by}}',
  searchUrl: '/search?k=**&page=fypage&t=',
  searchable: 2,
  quickSearch: 0,
  filterable: 1,
  headers: {
    'User-Agent': 'MOBILE_UA',
    'Referer': HOST + '/',
  },
  class_parse: '#nav-swiper&&.nav-swiper-slide;a&&Text;a&&href;/(\\w+).html',
  cate_exclude: 'Netflix|今日更新|专题列表|排行榜',
  tab_exclude: '可可影视提供',
  tab_remove: ['4K(高峰不卡)'],
  play_parse: true,
  limit: 9,
  lazy: $js.toString(() => {
    let kurl = input;
    let khtml = request(kurl);
    if (/dujia/.test(khtml)) {
      kurl = khtml.split("PPPP = '")[1].split("';")[0];
      const key = CryptoJS.enc.Utf8.parse("Isu7fOAvI6!&IKpAbVdhf&^F");
      const dataObj = {
        ciphertext: CryptoJS.enc.Base64.parse(kurl),
      };
      const decrypted = CryptoJS.AES.decrypt(dataObj, key, {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7,
      });
      kurl = decrypted.toString(CryptoJS.enc.Utf8);
    } else {
      kurl = khtml.split('src: "')[1].split('",')[0];
    }
    input = {
      jx: 0,
      parse: 0,
      url: kurl,
      header: rule.headers,
    };
  }),
  预处理: $js.toString(() => {
    let hash = request(rule.host, {headers: rule.headers})?.match(/a0_0x2a54\s*=\s*\['([^']+)'/)?.[1]?.trim() ?? "";
    if (hash && hash !== getItem("myhash")) {
      setItem("mycookie", "");
      setItem("myhash", "");
      let idx = parseInt("0x" + hash[0], 16);
      for (let i = 0; i < 1000000; i++) {
        let input = hash + i;
        let sha1 = CryptoJS.SHA1(input).toString(CryptoJS.enc.Latin1);
        if (sha1.charCodeAt(idx) === 0xb0 && sha1.charCodeAt(idx + 1) === 0x0b) {
          let cookie = `cdndefend_js_cookie=${input}`;
          setItem("myhash", hash);
          setItem("mycookie", cookie);
          rule.headers["cookie"] = cookie;
          break;
        }
      }
    } else if (getItem("mycookie")) {
      rule.headers["cookie"] = getItem("mycookie");
    }
    let khtml = fetch(rule.host, {headers: rule.headers});
    let tValue = khtml.match(/<input[^>]*name="t"[^>]*value="([^"]*)"/i);
    if (tValue && tValue[1]) {
      rule.searchUrl = rule.searchUrl + encodeURIComponent(tValue[1]);
    }
    let scripts = pdfa(khtml, "script");
    let img_script = scripts.find((it) => pdfh(it, "script&&src").includes("rdul.js"));
    if (img_script) {
      let img_url = img_script.match(/src="(.*?)"/)[1];
      let img_html = fetch(img_url);
      rule.img_host = img_html.match(/'(.*?)'/)[1];
      rule.图片替换 = rule.host + "=>" + rule.img_host;
    }
  }),
  推荐: '.module-item;.v-item-title:eq(1)&&Text;img:eq(-1)&&data-original;span:eq(-1)&&Text;a&&href',
  一级: '.module-item;.v-item-title:eq(1)&&Text;img:eq(-1)&&data-original;span:eq(-1)&&Text;a&&href',
  二级: {
    title: '.detail-title&&strong:eq(1)&&Text;.detail-tags&&Text',
    img: '.detail-pic&&img&&data-original',
    desc: '.detail-info-row-main:eq(-2)&&Text;.detail-tags-item:eq(0)&&Text;.detail-tags-item:eq(1)&&Text;.detail-info-row-main:eq(1)&&Text;.detail-info-row-main:eq(0)&&Text',
    content: '.detail-desc&&Text',
    tabs: '.source-item',
    tab_text: 'span:eq(-1)&&Text',
    lists: '.episode-list:eq(#id)&&a',
    list_text: 'body&&Text',
    list_url: 'a&&href',
  },
  搜索: '.search-result-item;img&&alt;img&&data-original;.search-result-item-header&&Text;a&&href',
};
