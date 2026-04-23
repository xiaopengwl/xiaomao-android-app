var __xmRuleKeys = {
  preprocess: ["\u9884\u5904\u7406"],
  recommend: ["\u63a8\u8350"],
  first: ["\u4e00\u7ea7"],
  second: ["\u4e8c\u7ea7"],
  search: ["\u641c\u7d22"]
};

function __xmSafeArray(value) {
  return Array.isArray(value) ? value : [];
}

function __xmNormalizeText(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function __xmExtractStyleUrl(style) {
  var match = String(style || "").match(/url\((['"]?)(.*?)\1\)/i);
  return match ? match[2] : "";
}

function __xmPickSrcsetUrl(value) {
  var text = String(value || "").trim();
  if (!text) {
    return "";
  }
  return text.split(",")[0].trim().split(/\s+/)[0] || text;
}

function __xmAbsoluteUrl(url, host) {
  var nextUrl = String(url || "").trim()
    .replace(/&amp;/g, "&")
    .replace(/\\u002f/gi, "/")
    .replace(/\\\//g, "/");
  nextUrl = __xmExtractStyleUrl(nextUrl) || nextUrl;
  if (!nextUrl) {
    return "";
  }
  if (/^data:image\//i.test(nextUrl)) {
    return nextUrl;
  }
  if (/^https?:\/\//i.test(nextUrl)) {
    return nextUrl;
  }
  if (nextUrl.indexOf("//") === 0) {
    return "https:" + nextUrl;
  }
  if (!host) {
    return nextUrl;
  }
  var base = host.endsWith("/") ? host.slice(0, -1) : host;
  if (nextUrl.startsWith("/")) {
    return base + nextUrl;
  }
  return base + "/" + nextUrl;
}

function __xmNormalizePosterUrl(value, host) {
  var url = String(value || "").trim();
  if (!url) {
    return "";
  }
  url = __xmExtractStyleUrl(url) || url;
  url = __xmPickSrcsetUrl(url)
    .replace(/&amp;/g, "&")
    .replace(/\\u002f/gi, "/")
    .replace(/\\\//g, "/");
  if (/^(?:javascript:|blob:)/i.test(url)) {
    return "";
  }
  if (/^data:image\//i.test(url)) {
    return url;
  }
  return __xmAbsoluteUrl(url, host || "");
}

function __xmGetRuleValue(ruleObject, keys) {
  for (var i = 0; i < keys.length; i += 1) {
    if (Object.prototype.hasOwnProperty.call(ruleObject, keys[i])) {
      return ruleObject[keys[i]];
    }
  }
  return undefined;
}

function __xmRunPreprocess() {
  var preprocess = __xmGetRuleValue(rule, __xmRuleKeys.preprocess);
  if (typeof preprocess === "string" && preprocess.indexOf("js:") === 0) {
    var code = String(preprocess).substring(3);
    eval(code);
  }
}

function __xmBuildRecommendUrl(ruleObject, page) {
  var url = String(ruleObject.homeUrl || ruleObject.home_url || ruleObject.host || "");
  return __xmAbsoluteUrl(url.replace(/fypage/g, String(page || 1)), ruleObject.host || "");
}

function __xmBuildCategoryUrl(ruleObject, classId, page) {
  var url = String(ruleObject.url || "");
  url = url.replace(/fyclass/g, classId || "");
  url = url.replace(/fypage/g, String(page || 1));
  url = url.replace(/fyfilter/g, "");
  return __xmAbsoluteUrl(url, ruleObject.host || "");
}

function __xmBuildSearchUrl(ruleObject, keyword, page) {
  var url = String(ruleObject.searchUrl || "");
  url = url.replace(/\*\*/g, encodeURIComponent(keyword || ""));
  url = url.replace(/fypage/g, String(page || 1));
  return __xmAbsoluteUrl(url, ruleObject.host || "");
}

function __xmParseHtml(html) {
  return new DOMParser().parseFromString(html || "", "text/html");
}

function __xmParseHtmlFragment(html) {
  var wrapper = document.createElement("div");
  wrapper.innerHTML = html || "";
  return wrapper;
}

function __xmNormalizeRoot(root) {
  if (root instanceof Document || root instanceof Element) {
    return root;
  }
  if (typeof root === "string") {
    return __xmParseHtmlFragment(root);
  }
  return document;
}

function __xmCanMatchSelector(node, selector) {
  if (!(node instanceof Element)) {
    return false;
  }
  try {
    node.matches(selector);
    return true;
  } catch (error) {
    return false;
  }
}

function __xmQueryAllWithSelf(root, selector) {
  var normalizedRoot = __xmNormalizeRoot(root);
  var normalizedSelector = String(selector || "").trim().replace(/^>\s*/, ":scope > ");
  var results = [];
  var seen = [];

  function pushNode(node) {
    if (!node || seen.indexOf(node) >= 0) {
      return;
    }
    seen.push(node);
    results.push(node);
  }

  try {
    if (normalizedRoot instanceof Element
      && __xmCanMatchSelector(normalizedRoot, normalizedSelector)
      && normalizedRoot.matches(normalizedSelector)) {
      pushNode(normalizedRoot);
    }
  } catch (error) {
    if (normalizedRoot instanceof Element
      && __xmCanMatchSelector(normalizedRoot, selector)
      && normalizedRoot.matches(selector)) {
      pushNode(normalizedRoot);
    }
  }

  try {
    Array.prototype.slice.call(normalizedRoot.querySelectorAll(normalizedSelector)).forEach(pushNode);
  } catch (error2) {
    try {
      Array.prototype.slice.call(normalizedRoot.querySelectorAll(selector)).forEach(pushNode);
    } catch (ignored) {
      return results;
    }
  }
  return results;
}

function __xmStripPseudoSelector(selector, pseudoPrefix) {
  var index = selector.indexOf(pseudoPrefix);
  if (index < 0) {
    return null;
  }
  var depth = 1;
  var cursor = index + pseudoPrefix.length;
  while (cursor < selector.length && depth > 0) {
    var ch = selector.charAt(cursor);
    if (ch === "(") {
      depth += 1;
    } else if (ch === ")") {
      depth -= 1;
    }
    cursor += 1;
  }
  if (depth !== 0) {
    return null;
  }
  return {
    selector: (selector.slice(0, index) + selector.slice(cursor)).trim(),
    content: selector.slice(index + pseudoPrefix.length, cursor - 1)
  };
}

function __xmUnquotePseudoContent(value) {
  var text = String(value || "").trim();
  if ((text.startsWith('"') && text.endsWith('"')) || (text.startsWith("'") && text.endsWith("'"))) {
    return text.slice(1, -1);
  }
  return text;
}

function __xmParseSelectorFeatures(selector) {
  var working = String(selector || "").trim();
  var containsTexts = [];
  var hasSelectors = [];
  var token;
  while (true) {
    token = __xmStripPseudoSelector(working, ":has(");
    if (!token) {
      break;
    }
    hasSelectors.push(token.content.trim());
    working = token.selector;
  }
  while (true) {
    token = __xmStripPseudoSelector(working, ":contains(");
    if (!token) {
      break;
    }
    containsTexts.push(__xmUnquotePseudoContent(token.content));
    working = token.selector;
  }
  working = working.replace(/\s+/g, " ").trim();
  return {
    selector: working || "*",
    containsTexts: containsTexts.filter(Boolean),
    hasSelectors: hasSelectors.filter(Boolean)
  };
}

function __xmFilterSelectorMatches(nodes, features) {
  return nodes.filter(function (node) {
    if (!(node instanceof Element)) {
      return false;
    }
    if (features.containsTexts.length) {
      var text = __xmNormalizeText(node.textContent || "");
      for (var i = 0; i < features.containsTexts.length; i += 1) {
        if (text.indexOf(features.containsTexts[i]) < 0) {
          return false;
        }
      }
    }
    if (features.hasSelectors.length) {
      for (var j = 0; j < features.hasSelectors.length; j += 1) {
        if (__xmSelectEnhanced(node, features.hasSelectors[j]).length === 0) {
          return false;
        }
      }
    }
    return true;
  });
}

function __xmSelectOneStep(root, selector) {
  var normalizedRoot = __xmNormalizeRoot(root);
  var rawSelector = String(selector || "").trim();
  if (!rawSelector) {
    return [];
  }
  var nestedEqMatch = rawSelector.match(/^(.*?):eq\((-?\d+)\)(.*)$/);
  if (nestedEqMatch && nestedEqMatch[3]) {
    var baseNodes = __xmSelectOneStep(normalizedRoot, nestedEqMatch[1] + ":eq(" + nestedEqMatch[2] + ")");
    var nestedResults = [];
    for (var n = 0; n < baseNodes.length; n += 1) {
      nestedResults = nestedResults.concat(__xmSelectOneStep(baseNodes[n], nestedEqMatch[3].trim()));
    }
    return nestedResults;
  }
  var eqMatch = rawSelector.match(/^(.*?):eq\((-?\d+)\)$/);
  if (eqMatch) {
    var candidates = __xmQueryAllWithSelf(normalizedRoot, eqMatch[1] || "*");
    var index = Number(eqMatch[2]);
    var finalIndex = index < 0 ? candidates.length + index : index;
    return candidates[finalIndex] ? [candidates[finalIndex]] : [];
  }
  if (rawSelector === "body") {
    return [normalizedRoot.body || normalizedRoot];
  }
  var features = __xmParseSelectorFeatures(rawSelector);
  return __xmFilterSelectorMatches(__xmQueryAllWithSelf(normalizedRoot, features.selector), features);
}

function __xmSelectEnhanced(root, selector) {
  return String(selector || "").split("&&").reduce(function (nodes, step) {
    var next = [];
    for (var i = 0; i < nodes.length; i += 1) {
      next = next.concat(__xmSelectOneStep(nodes[i], step));
    }
    return next;
  }, [__xmNormalizeRoot(root)]);
}

function __xmIsAttributeToken(token) {
  return token === "Text" || /^[a-zA-Z0-9:_-]+$/.test(token);
}

function __xmExtractNodeAttribute(node, attr) {
  if (!node || !node.getAttribute) {
    return "";
  }
  var direct = node.getAttribute(attr);
  if (direct) {
    return direct;
  }
  if (attr === "src") {
    var lazyAttrs = ["data-src", "data-original", "data-lazy", "data-lazy-src", "data-url", "data-cover", "lay-src", "srcset"];
    for (var i = 0; i < lazyAttrs.length; i += 1) {
      var value = node.getAttribute(lazyAttrs[i]);
      if (value) {
        return __xmPickSrcsetUrl(value);
      }
    }
    var styleUrl = __xmExtractStyleUrl(node.getAttribute("style"));
    if (styleUrl) {
      return styleUrl;
    }
  }
  if (attr === "href") {
    return node.getAttribute("data-href") || node.getAttribute("data-url") || "";
  }
  return "";
}

function __xmExtractByRule(root, expr, host) {
  if (!expr) {
    return "";
  }
  var parts = String(expr).split("&&");
  var attr = "Text";
  var segments = parts.slice();
  if (segments.length > 1 && __xmIsAttributeToken(segments[segments.length - 1])) {
    attr = segments.pop();
  }
  var current = [__xmNormalizeRoot(root)];
  segments.forEach(function (selector) {
    var next = [];
    for (var i = 0; i < current.length; i += 1) {
      next = next.concat(__xmSelectOneStep(current[i], selector));
    }
    current = next;
  });
  var first = current[0];
  if (!first) {
    return "";
  }
  if (attr === "Text") {
    return __xmNormalizeText(first.textContent || "");
  }
  var value = __xmExtractNodeAttribute(first, attr);
  if (attr === "href" || attr === "src" || attr.indexOf("data-") === 0) {
    return __xmAbsoluteUrl(value || "", host);
  }
  return value || "";
}

function __xmExtractMulti(expr, root, host) {
  return String(expr || "").split(";").map(function (part) {
    return __xmExtractByRule(root, part, host);
  }).filter(Boolean);
}

function __xmParseCategoriesByClassParse(classParse, html, host) {
  var parts = String(classParse || "").split(";");
  if (parts.length < 4) {
    return [];
  }
  return __xmSelectEnhanced(__xmParseHtml(html), parts[0]).map(function (node, index) {
    var name = __xmExtractByRule(node, parts[1], host);
    var href = __xmExtractByRule(node, parts[2], host);
    var url = href;
    if (parts[3]) {
      var match = href.match(new RegExp(parts[3]));
      if (match) {
        url = match[1] || match[0];
      }
    }
    return {
      id: "class-" + index,
      name: name,
      url: url
    };
  }).filter(function (item) {
    return item.name && item.url;
  });
}

function __xmParseListBySelector(ruleValue, html, host) {
  if (!ruleValue) {
    return [];
  }
  var parts = String(ruleValue).split(";");
  if (parts.length < 5) {
    return [];
  }
  return __xmSelectEnhanced(__xmParseHtml(html), parts[0]).map(function (node, index) {
    var title = __xmExtractByRule(node, parts[1], host);
    var img = __xmAbsoluteUrl(__xmExtractByRule(node, parts[2], host), host);
    var desc = __xmExtractByRule(node, parts[3], host);
    var url = __xmAbsoluteUrl(__xmExtractByRule(node, parts[4], host), host);
    return {
      id: url || ("item-" + index),
      vod_id: url,
      vod_name: title,
      vod_pic: img,
      vod_remarks: desc,
      title: title,
      img: img,
      desc: desc,
      url: url
    };
  }).filter(function (item) {
    return item.vod_name || item.url;
  });
}

function __xmParseDetailObject(config, html, host, detailUrl) {
  var root = __xmParseHtml(html);
  var detail = {
    vod_id: detailUrl,
    vod_name: __xmExtractMulti(config.title, root, host).join(" "),
    vod_pic: __xmAbsoluteUrl(__xmExtractByRule(root, config.img, host), host),
    vod_remarks: __xmExtractMulti(config.desc, root, host).join(" "),
    vod_content: __xmExtractByRule(root, config.content, host),
    playGroups: []
  };

  if (config.tabs && config.lists) {
    __xmSelectEnhanced(root, config.tabs).forEach(function (tabNode, index) {
      var tabName = __xmExtractByRule(tabNode, config.tab_text || "body&&Text", host) || ("线路 " + (index + 1));
      var selector = String(config.lists).replace(/#id/g, String(index));
      var items = __xmSelectEnhanced(root, selector).map(function (episodeNode, episodeIndex) {
        return {
          name: __xmExtractByRule(episodeNode, config.list_text || "body&&Text", host) || ("播放 " + (episodeIndex + 1)),
          url: __xmAbsoluteUrl(__xmExtractByRule(episodeNode, config.list_url || "a&&href", host), host)
        };
      }).filter(function (item) {
        return item.url;
      });
      if (items.length) {
        detail.playGroups.push({ name: tabName, items: items });
      }
    });
  }

  if (!detail.playGroups.length && config.lists) {
    var items = __xmSelectEnhanced(root, config.lists).map(function (episodeNode, episodeIndex) {
      return {
        name: __xmExtractByRule(episodeNode, config.list_text || "body&&Text", host) || ("播放 " + (episodeIndex + 1)),
        url: __xmAbsoluteUrl(__xmExtractByRule(episodeNode, config.list_url || "a&&href", host), host)
      };
    }).filter(function (item) {
      return item.url;
    });
    if (items.length) {
      detail.playGroups.push({
        name: config.tab_text_default || config.list_group_name || "默认线路",
        items: items
      });
    }
  }

  return detail;
}

function __xmNormalizeItems(items, host) {
  return __xmSafeArray(items).map(function (item, index) {
    return {
      id: item.vod_id || item.url || ("item-" + index),
      vod_id: item.vod_id || item.url || "",
      vod_name: item.vod_name || item.title || ("未命名 " + (index + 1)),
      vod_pic: __xmNormalizePosterUrl(item.vod_pic || item.img || item.pic || item.cover || "", host),
      vod_remarks: item.vod_remarks || item.desc || "",
      title: item.title || item.vod_name || "",
      img: __xmNormalizePosterUrl(item.img || item.vod_pic || item.pic || item.cover || "", host),
      desc: item.desc || item.vod_remarks || "",
      url: item.url || item.vod_id || ""
    };
  });
}

function __xmNormalizeDetail(payload, host, fallbackTitle, fallbackPic, fallbackId) {
  var detail = payload || {};
  var playGroups = __xmSafeArray(detail.playGroups);
  if (!playGroups.length && detail.vod_play_url) {
    var froms = String(detail.vod_play_from || "默认线路").split("$$$");
    var groups = String(detail.vod_play_url).split("$$$");
    playGroups = groups.map(function (groupText, groupIndex) {
      return {
        name: froms[groupIndex] || ("线路 " + (groupIndex + 1)),
        items: String(groupText).split("#").filter(Boolean).map(function (entry, itemIndex) {
          var parts = entry.split("$");
          return {
            name: parts[0] || ("播放 " + (itemIndex + 1)),
            url: parts.slice(1).join("$")
          };
        })
      };
    });
  }
  return {
    vod_id: detail.vod_id || fallbackId || "",
    vod_name: detail.vod_name || detail.title || fallbackTitle || "未命名",
    vod_pic: __xmNormalizePosterUrl(detail.vod_pic || detail.img || detail.pic || detail.cover || fallbackPic || "", host),
    vod_remarks: detail.vod_remarks || detail.desc || "",
    vod_content: detail.vod_content || detail.content || "",
    playGroups: playGroups
  };
}

function __xmPickListResult() {
  for (var i = 0; i < arguments.length; i += 1) {
    var value = arguments[i];
    if (Array.isArray(value)) {
      return value;
    }
    if (value && typeof value === "object") {
      if (Array.isArray(value.list)) {
        return value.list;
      }
      if (Array.isArray(value.items)) {
        return value.items;
      }
      if (Array.isArray(value.data)) {
        return value.data;
      }
    }
  }
  return [];
}

function __xmPickDetailResult() {
  for (var i = 0; i < arguments.length; i += 1) {
    var value = arguments[i];
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return value;
    }
  }
  return {};
}
