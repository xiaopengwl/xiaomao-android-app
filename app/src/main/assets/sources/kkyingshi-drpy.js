var rule = {
  title: '可可影视',
  host: 'https://www.kkys01.com',
  homeUrl: '/show/1------.html',
  url: 'fyclass',
  searchUrl: '/search?k=**&page=fypage&t=',
  searchable: 2,
  quickSearch: 0,
  filterable: 0,
  headers: {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36',
    'Referer': 'https://www.kkys01.com/'
  },
  class_name: '\u7535\u5f71&\u8fde\u7eed\u5267&\u52a8\u6f2b&\u7efc\u827a\u7eaa\u5f55&\u77ed\u5267',
  class_url: '/show/1------.html&/show/2------.html&/show/3------.html&/show/4------.html&/show/6------.html',
  play_parse: true,
  limit: 12,
  "\u9884\u5904\u7406": $js.toString(() => {
    let landing = request(rule.host, { headers: rule.headers }) || '';
    let hash = (landing.match(/a0_0x2a54\s*=\s*\['([^']+)'/) || [])[1] || '';
    if (hash && hash !== getItem('kkys_hash')) {
      setItem('kkys_cookie', '');
      setItem('kkys_hash', '');
      const idx = parseInt('0x' + hash.charAt(0), 16);
      for (let i = 0; i < 1000000; i += 1) {
        const value = hash + i;
        const sha1 = CryptoJS.SHA1(value).toString(CryptoJS.enc.Latin1);
        if (sha1.charCodeAt(idx) === 0xb0 && sha1.charCodeAt(idx + 1) === 0x0b) {
          const cookie = 'cdndefend_js_cookie=' + value;
          setItem('kkys_hash', hash);
          setItem('kkys_cookie', cookie);
          rule.headers.cookie = cookie;
          break;
        }
      }
    } else {
      const cookie = getItem('kkys_cookie');
      if (cookie) {
        rule.headers.cookie = cookie;
      }
    }
    const html = request(rule.host, { headers: rule.headers }) || '';
    const token = (html.match(/<input[^>]*name="t"[^>]*value="([^"]*)"/i) || [])[1] || '';
    if (token) {
      rule.searchUrl = '/search?k=**&page=fypage&t=' + encodeURIComponent(token);
    }
  }),
  lazy: $js.toString(() => {
    let html = document.html || request(input, { headers: rule.headers }) || '';
    let encrypted = (html.match(/whatTMDwhatTMDPPPP\s*=\s*'([^']+)'/) || [])[1] || '';
    let kurl = '';
    if (encrypted) {
      const key = CryptoJS.enc.Utf8.parse('Isu7fOAvI6!&IKpAbVdhf&^F');
      const dataObj = {
        ciphertext: CryptoJS.enc.Base64.parse(encrypted),
      };
      const decrypted = CryptoJS.AES.decrypt(dataObj, key, {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7,
      });
      kurl = decrypted.toString(CryptoJS.enc.Utf8);
    }
    if (!kurl) {
      kurl = (html.match(/src:\s*"([^"]+)"/) || [])[1] || '';
    }
    input = {
      jx: 0,
      parse: kurl ? 0 : 1,
      url: kurl || input,
      header: rule.headers,
    };
  }),
  "\u63a8\u8350": '.module-item;.v-item-title:eq(1)&&Text;img:eq(-1)&&data-original;.v-item-bottom span&&Text;a&&href',
  "\u4e00\u7ea7": '.module-item;.v-item-title:eq(1)&&Text;img:eq(-1)&&data-original;.v-item-bottom span&&Text;a&&href',
  "\u4e8c\u7ea7": $js.toString(() => {
    let html = request(input, { headers: rule.headers }) || '';
    let title = pdfh(html, '.detail-title&&strong:eq(1)&&Text') || pdfh(html, 'title&&Text').split('-')[0];
    let pic = pd(html, '.detail-pic img:eq(-1)&&data-original', HOST) || pd(html, '.detail-pic img:eq(-1)&&src', HOST);
    let content = pdfh(html, '.detail-desc&&Text') || pdfh(html, '.detail-desc p&&Text');
    let desc = [];
    [
      '.detail-tags-item:eq(0)&&Text',
      '.detail-tags-item:eq(1)&&Text',
      '.detail-info-row-main:eq(0)&&Text',
      '.detail-info-row-main:eq(1)&&Text',
      '.detail-info-row-main:eq(2)&&Text',
      '.detail-info-row-main:eq(3)&&Text'
    ].forEach((expr) => {
      const value = pdfh(html, expr);
      if (value) {
        desc.push(value);
      }
    });
    let groups = [];
    let sourceNodes = pdfa(html, '.detail-play-box .source-list-box-main .source-item');
    let episodeNodes = pdfa(html, '.detail-play-box .episode-list-box-main .episode-list');
    if (sourceNodes.length && episodeNodes.length) {
      for (let i = 0; i < episodeNodes.length; i += 1) {
        let groupName = pdfh(sourceNodes[i] || sourceNodes[0], 'Text') || ('绾胯矾' + (i + 1));
        let items = pdfa(episodeNodes[i], 'a').map((it, idx) => {
          return {
            name: pdfh(it, 'Text') || ('鎾斁' + (idx + 1)),
            url: pd(it, 'a&&href', HOST),
          };
        }).filter((it) => it.url);
        if (items.length) {
          groups.push({ name: groupName, items: items });
        }
      }
    }
    if (!groups.length) {
      let matches = html.match(/\/play\/\d+-\d+-\d+\.html/g) || [];
      let seen = {};
      let items = [];
      matches.forEach((path, idx) => {
        let url = pd(path, '', HOST);
        if (!url || seen[url]) {
          return;
        }
        seen[url] = 1;
        items.push({
          name: '鎾斁' + (idx + 1),
          url: url,
        });
      });
      if (!items.length) {
        let detailId = (input.match(/\/detail\/(\d+)\.html/) || [])[1] || '';
        if (detailId) {
          items.push({
            name: '绔嬪嵆鎾斁',
            url: HOST + '/play/' + detailId + '-1-1.html',
          });
        }
      }
      if (items.length) {
        groups.push({ name: '鍙彲褰辫', items: items });
      }
    }
    VOD = {
      vod_id: input,
      vod_name: title || '璇︽儏',
      vod_pic: pic,
      vod_remarks: desc.join(' | '),
      vod_content: content,
      playGroups: groups,
    };
  }),
  "\u641c\u7d22": '.search-result-item;.search-result-item-header&&Text;.search-result-item-pic img:eq(0)&&data-original;.search-result-item-main&&Text;a&&href',
};
