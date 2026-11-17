(function installLoginInputCompat(win) {
  'use strict';
  if (win.__qauLoginInputCompat) return;
  win.__qauLoginInputCompat = true;
  var doc = win.document;
  // Values live only in this document, never in Android, logs or persistent storage.
  var states = new WeakMap();
  var editableTypes = ['text', 'password', 'tel', 'email', 'search', 'url'];
  function editable(el) {
    return el && el.tagName === 'INPUT' && editableTypes.indexOf(el.type || 'text') >= 0 &&
      !el.disabled && !el.readOnly && typeof el.selectionStart === 'number';
  }
  function remember(el) {
    var s = states.get(el);
    if (!s) {
      s = { composing: false, selecting: false, lastEdit: -Infinity, pending: null };
      states.set(el, s);
    }
    s.value = el.value;
    s.start = el.selectionStart;
    s.end = el.selectionEnd;
    return s;
  }
  function caret(el, start, end) {
    // Do not change focus: this must not reopen the keyboard or cancel composition.
    try { el.setSelectionRange(start, end); } catch (_) { }
  }
  function restoreZeroReset(el, s) {
    if (!s.composing && !s.selecting && el.value === s.value &&
        el.selectionStart === 0 && el.selectionEnd === 0 && s.end > 0) {
      caret(el, s.start, s.end);
    }
  }
  function finishEdit(el, s, start, end) {
    caret(el, start, end);
    remember(el);
    s.lastEdit = Date.now();
    // Some WebViews report the stale selection after the input event, on the next frame.
    var value = el.value;
    win.requestAnimationFrame(function () {
      if (doc.activeElement === el && el.value === value && states.get(el) === s) restoreZeroReset(el, s);
    });
  }
  doc.addEventListener('focusin', function (e) {
    if (editable(e.target)) remember(e.target);
  }, true);
  doc.addEventListener('focusout', function (e) { states.delete(e.target); }, true);
  doc.addEventListener('pointerdown', function (e) {
    if (editable(e.target)) remember(e.target).selecting = true;
  }, true);
  doc.addEventListener('pointerup', function (e) {
    if (!editable(e.target)) return;
    var el = e.target;
    win.setTimeout(function () {
      if (doc.activeElement === el) remember(el).selecting = false;
    }, 0);
  }, true);
  doc.addEventListener('pointercancel', function (e) {
    if (editable(e.target)) remember(e.target).selecting = false;
  }, true);
  function navigationKey(e) {
    return /^(Arrow|Home|End)/.test(e.key || '') ||
      ((e.ctrlKey || e.metaKey) && (e.key || '').toLowerCase() === 'a');
  }
  doc.addEventListener('keydown', function (e) {
    if (editable(e.target) && navigationKey(e)) remember(e.target).selecting = true;
  }, true);
  doc.addEventListener('keyup', function (e) {
    if (editable(e.target) && navigationKey(e)) remember(e.target).selecting = false;
  }, true);
  doc.addEventListener('selectionchange', function () {
    var el = doc.activeElement;
    if (!editable(el)) return;
    var s = states.get(el);
    if (!s || s.composing) return;
    // Only suppress the known zero-reset immediately after an edit. Real selection
    // changes (including selecting all and dragging handles) remain native.
    if (!s.selecting && Date.now() - s.lastEdit < 150 && el.value === s.value &&
        el.selectionStart === 0 && el.selectionEnd === 0 && s.end > 0) {
      restoreZeroReset(el, s);
    } else remember(el);
  });
  doc.addEventListener('beforeinput', function (e) {
    var el = e.target;
    if (!editable(el)) return;
    var s = states.get(el) || remember(el);
    if (s.composing || e.isComposing) return;
    if (s.value !== el.value) s = remember(el); // autofill or page-controlled changes
    restoreZeroReset(el, s);
    s.pending = { value: el.value, start: el.selectionStart, end: el.selectionEnd };
  }, true);
  doc.addEventListener('input', function (e) {
    var el = e.target;
    if (!editable(el)) return;
    var s = states.get(el) || remember(el);
    if (s.composing || e.isComposing) return;
    var p = s.pending;
    s.pending = null;
    if (!p) { remember(el); return; }
    var type = e.inputType || '';
    var value = el.value;
    if (type.indexOf('insert') === 0) {
      var data = typeof e.data === 'string' ? e.data : null;
      // Paste may omit InputEvent.data. Infer only an exact edit or the known
      // prefix-insertion failure, never arbitrary page-controlled formatting.
      if (data === null) {
        var count = value.length - p.value.length + (p.end - p.start);
        var prefix = p.value.slice(0, p.start), suffix = p.value.slice(p.end);
        if (count >= 0 && value.slice(0, prefix.length) === prefix &&
            value.slice(p.start + count) === suffix) {
          data = value.slice(p.start, p.start + count);
        } else if (value.length > p.value.length && value.slice(value.length - p.value.length) === p.value) {
          data = value.slice(0, value.length - p.value.length);
        }
      }
      if (data !== null) {
        var expected = p.value.slice(0, p.start) + data + p.value.slice(p.end);
        // Repair only a proven insertion at zero, not arbitrary page formatting.
        if (p.start > 0 && value === data + p.value && value !== expected) {
          el.value = expected;
          value = expected;
        }
        if (value === expected) {
          finishEdit(el, s, p.start + data.length, p.start + data.length);
          return;
        }
      }
    } else if (type.indexOf('delete') === 0 && value.length <= p.value.length) {
      // Native deletion keeps its semantics; restore only a collapsed stale caret.
      var removed = p.value.length - value.length;
      var pos = p.start !== p.end || type.indexOf('Forward') >= 0 ? p.start : Math.max(0, p.start - removed);
      var expectedDelete = p.value.slice(0, pos) + p.value.slice(pos + removed);
      if (value === expectedDelete) {
        finishEdit(el, s, pos, pos);
        return;
      }
    }
    remember(el); // undo/redo, replacement and unknown input types stay native
  }, true);
  doc.addEventListener('compositionstart', function (e) {
    if (!editable(e.target)) return;
    var s = states.get(e.target) || remember(e.target);
    restoreZeroReset(e.target, s);
    s.composing = true;
    s.pending = { value: e.target.value, start: e.target.selectionStart, end: e.target.selectionEnd };
  }, true);
  doc.addEventListener('compositionend', function (e) {
    var el = e.target;
    var s = states.get(el);
    if (!s) return;
    s.composing = false;
    var p = s.pending;
    s.pending = null;
    if (p && typeof e.data === 'string') {
      var expected = p.value.slice(0, p.start) + e.data + p.value.slice(p.end);
      if (p.start > 0 && el.value === e.data + p.value && el.value !== expected) el.value = expected;
      if (el.value === expected) {
        finishEdit(el, s, p.start + e.data.length, p.start + e.data.length);
        return;
      }
    }
    remember(el);
  }, true);
  function frame(el) {
    try {
      if (el.contentWindow && el.contentDocument) installLoginInputCompat(el.contentWindow);
    } catch (_) { /* Cross-origin frames retain their own native input handling. */ }
  }
  doc.addEventListener('load', function (e) { if (e.target.tagName === 'IFRAME') frame(e.target); }, true);
  Array.prototype.forEach.call(doc.querySelectorAll('iframe'), frame);
})(window);
