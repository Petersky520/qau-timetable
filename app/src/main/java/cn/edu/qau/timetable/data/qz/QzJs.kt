package cn.edu.qau.timetable.data.qz

/**
 * 注入到 WebView 的 JS。
 *
 * 注意：这里刻意不用 jQuery / 模板字符串 / `$`，因为在 Kotlin 原始字符串里
 * `$` 会触发插值。`\u0001` 保持字面量，交给 JS 自己转义 —— 这正是我们想要的
 * 语义分隔符（见 [QzTableParser.TITLE_SEP]）。
 */
object QzJs {

    /** 定义 window.__qauExtract(kind)。 */
    val EXTRACT_FN: String = """
(function () {
  if (window.__qauExtract) { return; }

  function norm(s) {
    return (s || '').replace(/\u00a0/g, ' ').replace(/[ \t\u3000]+/g, ' ').trim();
  }

  function hasTitle(node) {
    if (!node.getElementsByTagName) { return false; }
    var els = node.getElementsByTagName('*');
    for (var i = 0; i < els.length; i++) {
      var t = els[i].getAttribute && els[i].getAttribute('title');
      if (t) { return true; }
    }
    return false;
  }

  // 把单元格内容拆成若干「行」；带 title 的元素编码成 title + \u0001 + text
  function cellText(td) {
    if (!hasTitle(td)) {
      var raw = (td.innerText !== undefined && td.innerText !== null) ? td.innerText : (td.textContent || '');
      var lines = raw.replace(/\r/g, '\n').split('\n');
      var out = [];
      for (var i = 0; i < lines.length; i++) {
        var t = norm(lines[i]);
        if (t) { out.push(t); }
      }
      return out.join('\n');
    }

    var acc = [];
    function walk(node) {
      for (var i = 0; i < node.childNodes.length; i++) {
        var n = node.childNodes[i];
        if (n.nodeType === 3) {
          var t = norm(n.nodeValue);
          if (t) { acc.push(t); }
        } else if (n.nodeType === 1) {
          var tag = n.tagName ? n.tagName.toLowerCase() : '';
          if (tag === 'br') { continue; }
          var title = n.getAttribute ? n.getAttribute('title') : null;
          var txt = norm(n.innerText !== undefined && n.innerText !== null ? n.innerText : (n.textContent || ''));
          if (!txt && n.children && n.children.length) { walk(n); continue; }
          if (title && txt) {
            acc.push(title + '\u0001' + txt);
          } else if (n.children && n.children.length) {
            walk(n);
          } else if (txt) {
            acc.push(txt);
          }
        }
      }
    }
    walk(td);

    var res = [];
    for (var i = 0; i < acc.length; i++) {
      if (acc[i] !== res[res.length - 1]) { res.push(acc[i]); }
    }
    return res.join('\n');
  }

  // 把 <table> 摊平成等长二维数组。
  //   rows  —— rowspan/colspan 用复制填充，保证每个位置都有文本；
  //   spans —— 只在锚点记录 rowspan（这一格纵向占几行），其余为 0。
  // "一门课占几小节"必须以 spans 为准，不能靠"相邻文本相同就合并"：
  // 强智如果在跨节的每一格里都写了节次（第1节 / 第2节），文本就不同，
  // 靠文本合并会把一门两节的课裂成两个单节。
  function flatten(table) {
    var grid = [];
    var spanGrid = [];
    var trs = table.rows;
    for (var r = 0; r < trs.length; r++) {
      if (!grid[r]) { grid[r] = []; }
      if (!spanGrid[r]) { spanGrid[r] = []; }
      var c = 0;
      var cells = trs[r].cells;
      for (var k = 0; k < cells.length; k++) {
        var cell = cells[k];
        while (grid[r][c] !== undefined) { c++; }
        var rs = cell.rowSpan || 1;
        var cs = cell.colSpan || 1;
        var text = cellText(cell);
        for (var i = 0; i < rs; i++) {
          var rr = r + i;
          if (!grid[rr]) { grid[rr] = []; }
          if (!spanGrid[rr]) { spanGrid[rr] = []; }
          for (var j = 0; j < cs; j++) {
            grid[rr][c + j] = text;
            spanGrid[rr][c + j] = (i === 0 && j === 0) ? rs : 0;
          }
        }
        c += cs;
      }
    }
    var width = 0;
    for (var r2 = 0; r2 < grid.length; r2++) {
      if (grid[r2] && grid[r2].length > width) { width = grid[r2].length; }
    }
    var rows = [];
    var spans = [];
    for (var r3 = 0; r3 < grid.length; r3++) {
      var row = [];
      var spanRow = [];
      var g = grid[r3] || [];
      var sg = spanGrid[r3] || [];
      for (var c2 = 0; c2 < width; c2++) {
        row.push(g[c2] === undefined ? '' : String(g[c2]));
        spanRow.push(sg[c2] === undefined ? 0 : sg[c2]);
      }
      rows.push(row);
      spans.push(spanRow);
    }
    return { rows: rows, spans: spans };
  }

  function dayScore(rows) {
    var keys = ['星期一','星期二','星期三','星期四','星期五','星期六','星期日','周一','周二','周三','周四','周五','周六','周日'];
    var n = 0;
    for (var r = 0; r < rows.length && r < 6; r++) {
      for (var c = 0; c < rows[r].length; c++) {
        for (var k = 0; k < keys.length; k++) {
          if (String(rows[r][c]).indexOf(keys[k]) >= 0) { n++; }
        }
      }
    }
    return n;
  }

  window.__qauExtract = function (kind) {
    try {
      var tables = document.getElementsByTagName('table');
      if (!tables.length) {
        return JSON.stringify({ ok: false, error: '页面上没有找到任何表格' });
      }
      var best = null, bestData = null, bestScore = -1;
      for (var i = 0; i < tables.length; i++) {
        var data = flatten(tables[i]);
        var rows = data.rows;
        if (rows.length < 2) { continue; }
        var score = rows.length;
        if (kind === 'kb') { score = dayScore(rows) * 1000 + rows.length; }
        if (score > bestScore) { bestScore = score; best = tables[i]; bestData = data; }
      }
      if (!bestData) {
        return JSON.stringify({ ok: false, error: '表格太小，无法解析' });
      }
      // 表头：第一行
      var headers = bestData.rows.length ? bestData.rows[0] : [];
      return JSON.stringify({
        ok: true,
        kind: kind,
        url: location.href,
        headers: headers,
        rows: bestData.rows,
        rowspans: bestData.spans
      });
    } catch (e) {
      return JSON.stringify({ ok: false, error: String(e) });
    }
  };
})();
""".trimIndent()

    /** 调用抽取函数。 */
    fun extractCall(kind: String): String =
        "(function(){ try { QauBridge.post(window.__qauExtract('" + kind + "')); } " +
            "catch(e) { QauBridge.post(JSON.stringify({ok:false,error:String(e)})); } })();"

    /** 在页面里找带关键词的链接，用于「自动点进课表/考试/成绩」。 */
    val FIND_LINKS_FN: String = """
(function () {
  if (window.__qauFindLinks) { return; }
  window.__qauFindLinks = function () {
    var out = [];
    var as = document.getElementsByTagName('a');
    for (var i = 0; i < as.length; i++) {
      var a = as[i];
      var text = (a.innerText || a.textContent || '').replace(/\s+/g, '');
      var href = a.getAttribute('href') || '';
      if (!text || text.length > 20) { continue; }
      if (!href || href.indexOf('javascript') === 0 || href === '#') { continue; }
      out.push({ text: text, href: a.href });
    }
    return JSON.stringify(out);
  };
})();
""".trimIndent()

    /** 判断是否已经登录（页面里出现退出/欢迎字样，或不再是登录页）。 */
    val LOGIN_STATE_FN: String = """
(function () {
  var body = (document.body && (document.body.innerText || document.body.textContent)) || '';
  var hasPwd = document.getElementsByTagName('input');
  var pwd = false;
  for (var i = 0; i < hasPwd.length; i++) {
    var t = hasPwd[i].getAttribute('type');
    if (t && t.toLowerCase() === 'password') { pwd = true; }
  }
  var loggedIn = (!pwd) && (body.indexOf('退出') >= 0 || body.indexOf('注销') >= 0 || body.indexOf('欢迎') >= 0 || body.length > 800);
  return JSON.stringify({ loggedIn: loggedIn, hasPassword: pwd, url: location.href, title: document.title });
})();
""".trimIndent()

    /**
     * 强制页面与表单控件为 LTR。
     *
     * 用于规避 Android WebView 的方向漂移：页面一旦被判成 RTL，
     * `admin123` 这种字母+数字混排会被 bidi 重排成 `123admin`，
     * 用户看到的就是"输入倒序"。青农大教务系统是中文站（LTR），
     * 强制 LTR 不会破坏它本来的排版。
     */
    val FORCE_LTR_FN: String = """
(function () {
  try {
    if (document.getElementById('__qau_ltr')) { return; }
    var s = document.createElement('style');
    s.id = '__qau_ltr';
    s.textContent =
      'html,body{direction:ltr !important;}' +
      'input,textarea{direction:ltr !important;unicode-bidi:plaintext !important;}';
    var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
    head.appendChild(s);
  } catch (e) { }
})();
""".trimIndent()

    /**
     * 诊断：把页面/输入框的真实方向**以及输入框里的真实值**回传。
     *
     * 只看 direction/unicode-bidi 是不够的 —— 必须看 value。
     * 两条完全不同的病因，修法也不同：
     *   值本身是反的      -> 输入法/InputConnection 的插入位置算错（编辑器侧）
     *   值是对的、显示反了 -> bidi 方向问题（渲染侧）
     * 学号和验证码不是机密，原样回传；密码只回传长度。
     */
    val DIAG_FN: String = """
(function () {
  try {
    function dir(el) { return el ? getComputedStyle(el).direction : ''; }
    function val(id) { var el = document.getElementById(id); return el ? String(el.value) : null; }
    function len(id) { var el = document.getElementById(id); return el ? String(el.value).length : -1; }
    var inputs = [];
    var els = document.querySelectorAll('input,textarea');
    for (var i = 0; i < els.length && i < 5; i++) {
      inputs.push({
        type: els[i].type || '',
        dir: dir(els[i]),
        bidi: getComputedStyle(els[i]).unicodeBidi
      });
    }
    QauBridge.post(JSON.stringify({
      ok: true,
      kind: 'diag',
      url: location.href,
      htmlDir: dir(document.documentElement),
      bodyDir: document.body ? dir(document.body) : '',
      docDir: document.dir || '',
      lang: document.documentElement.lang || '',
      navLang: navigator.language,
      ltrFixApplied: !!document.getElementById('__qau_ltr'),
      valUn: val('un'),
      valCode: val('code'),
      lenPd: len('pd'),
      inputs: inputs
    }));
  } catch (e) {
    QauBridge.post(JSON.stringify({ ok: false, error: String(e) }));
  }
})();
""".trimIndent()

    /** 导出当前页面 HTML（截断），用于排查解析和输入异常。 */
    val PAGE_HTML_FN: String = """
(function () {
  try {
    var html = document.documentElement ? document.documentElement.outerHTML : '';
    if (html.length > 20000) { html = html.substring(0, 20000) + '\n...[已截断]'; }
    QauBridge.post(JSON.stringify({
      ok: true, kind: 'html', url: location.href,
      title: document.title, html: html
    }));
  } catch (e) {
    QauBridge.post(JSON.stringify({ ok: false, error: String(e) }));
  }
})();
""".trimIndent()

    private fun cssQuote(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'")

    /** 供其它地方复用：把任意 CSS 文本注入页面。 */
    fun injectCss(css: String): String = """
(function () {
  try {
    var old = document.getElementById('__qau_custom');
    if (old) { old.parentNode.removeChild(old); }
    var s = document.createElement('style');
    s.id = '__qau_custom';
    s.textContent = '${cssQuote(css)}';
    (document.head || document.documentElement).appendChild(s);
  } catch (e) { }
})();
""".trimIndent()
}
