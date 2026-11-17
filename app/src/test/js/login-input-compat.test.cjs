const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/assets/web/login-input-compat.js'), 'utf8');

function fixture(type = 'text') {
  const listeners = {};
  const frames = [];
  const timers = [];
  const doc = {
    addEventListener(name, fn) { (listeners[name] ||= []).push(fn); },
    querySelectorAll() { return []; },
  };
  const win = { document: doc, requestAnimationFrame(fn) { frames.push(fn); }, setTimeout(fn) { timers.push(fn); } };
  const el = {
    tagName: 'INPUT', type, value: '', selectionStart: 0, selectionEnd: 0,
    setSelectionRange(start, end) { this.selectionStart = start; this.selectionEnd = end; },
  };
  function fire(name, data = {}) {
    for (const fn of listeners[name] || []) fn({ target: el, ...data });
  }
  vm.runInNewContext(source, { window: win, WeakMap, Date, Array });
  doc.activeElement = el;
  fire('focusin');
  function insert(data, { resetBefore = false, insertAtZero = false, resetAfter = false, inputType = 'insertText', eventData = data } = {}) {
    if (resetBefore) el.setSelectionRange(0, 0);
    fire('beforeinput', { inputType, data: eventData });
    if (insertAtZero) el.setSelectionRange(0, 0); // renderer ignores beforeinput's restored caret
    const start = el.selectionStart;
    el.value = el.value.slice(0, start) + data + el.value.slice(el.selectionEnd);
    el.setSelectionRange(start + data.length, start + data.length);
    if (resetAfter) el.setSelectionRange(0, 0);
    fire('input', { inputType, data: eventData });
  }
  function select(start, end = start) {
    fire('pointerdown');
    el.setSelectionRange(start, end);
    fire('selectionchange');
    fire('pointerup');
    while (timers.length) timers.shift()();
  }
  return { el, fire, insert, select, frames, win, listeners };
}

for (const type of ['text', 'password', 'tel']) {
  test(`${type}: repeated zero resets keep 12345 in order`, () => {
    const f = fixture(type);
    for (const digit of '12345') f.insert(digit, { resetBefore: true, insertAtZero: true, resetAfter: true });
    assert.equal(f.el.value, '12345');
    assert.equal(f.el.selectionStart, 5);
  });
}
test('normal typing and insertion in the middle', () => {
  const f = fixture(); f.insert('12345'); f.select(2); f.insert('X');
  assert.equal(f.el.value, '12X345'); assert.equal(f.el.selectionStart, 3);
});
test('intentional insertion at the beginning is not forced to the end', () => {
  const f = fixture(); f.insert('2345'); f.select(0); f.insert('1');
  assert.equal(f.el.value, '12345'); assert.equal(f.el.selectionStart, 1);
});
test('Home key can intentionally move the caret to zero immediately after typing', () => {
  const f = fixture(); f.insert('12345');
  f.fire('keydown', { key: 'Home' });
  f.el.setSelectionRange(0, 0); f.fire('selectionchange');
  f.fire('keyup', { key: 'Home' }); f.insert('A');
  assert.equal(f.el.value, 'A12345');
});
test('selection replacement', () => {
  const f = fixture(); f.insert('12345'); f.select(1, 4); f.insert('AB');
  assert.equal(f.el.value, '1AB5'); assert.equal(f.el.selectionStart, 3);
});
test('backspace after a zero reset restores the intended range', () => {
  const f = fixture(); f.insert('12345'); f.el.setSelectionRange(0, 0);
  f.fire('beforeinput', { inputType: 'deleteContentBackward' });
  const pos = f.el.selectionStart - 1;
  f.el.value = f.el.value.slice(0, pos) + f.el.value.slice(pos + 1);
  f.el.setSelectionRange(0, 0);
  f.fire('input', { inputType: 'deleteContentBackward' });
  assert.equal(f.el.value, '1234'); assert.equal(f.el.selectionStart, 4);
});
test('delete forward and selected deletion keep their native positions', () => {
  const f = fixture(); f.insert('12345'); f.select(1);
  f.fire('beforeinput', { inputType: 'deleteContentForward' });
  f.el.value = '1345'; f.fire('input', { inputType: 'deleteContentForward' });
  assert.equal(f.el.selectionStart, 1);
  f.select(1, 3); f.fire('beforeinput', { inputType: 'deleteContentBackward' });
  f.el.value = '15'; f.fire('input', { inputType: 'deleteContentBackward' });
  assert.equal(f.el.selectionStart, 1);
});
test('paste without event data repairs a proven prefix insertion', () => {
  const f = fixture(); f.insert('12');
  f.insert('345', { resetBefore: true, insertAtZero: true, eventData: null, inputType: 'insertFromPaste' });
  assert.equal(f.el.value, '12345'); assert.equal(f.el.selectionStart, 5);
});
test('paste without event data restores a middle selection after replacement', () => {
  const f = fixture(); f.insert('12345'); f.select(1, 4);
  f.insert('AB', { eventData: null, resetAfter: true, inputType: 'insertFromPaste' });
  assert.equal(f.el.value, '1AB5'); assert.equal(f.el.selectionStart, 3);
});
test('Chinese composition is untouched until commit', () => {
  const f = fixture(); f.insert('A'); f.fire('compositionstart');
  f.el.value = 'Ani'; f.el.setSelectionRange(1, 3);
  f.fire('input', { inputType: 'insertCompositionText', data: 'ni', isComposing: true });
  assert.equal(f.el.value, 'Ani'); assert.equal(f.el.selectionStart, 1);
  f.el.value = 'A你'; f.el.setSelectionRange(0, 0);
  f.fire('compositionend', { data: '你' });
  assert.equal(f.el.value, 'A你'); assert.equal(f.el.selectionStart, 2);
});
test('a composition inserted at zero is repaired only when proven', () => {
  const f = fixture(); f.insert('A'); f.fire('compositionstart');
  f.el.value = '你A'; f.fire('compositionend', { data: '你' });
  assert.equal(f.el.value, 'A你'); assert.equal(f.el.selectionStart, 2);
});
test('late renderer zero reset is corrected without changing text', () => {
  const f = fixture(); f.insert('12345'); f.el.setSelectionRange(0, 0);
  f.fire('selectionchange'); assert.equal(f.el.selectionStart, 5);
  f.el.setSelectionRange(0, 0); while (f.frames.length) f.frames.shift()();
  assert.equal(f.el.value, '12345'); assert.equal(f.el.selectionStart, 5);
});
test('autofill, undo and page formatters are not rewritten', () => {
  const f = fixture(); f.insert('12'); f.el.value = 'ABC'; f.el.setSelectionRange(1, 1);
  f.insert('X'); assert.equal(f.el.value, 'AXBC');
  f.fire('beforeinput', { inputType: 'historyUndo' });
  f.el.value = 'ABC'; f.el.setSelectionRange(1, 1);
  f.fire('input', { inputType: 'historyUndo' });
  assert.equal(f.el.value, 'ABC'); assert.equal(f.el.selectionStart, 1);
});
test('reinstallation does not duplicate listeners and blur clears stale state', () => {
  const f = fixture(); const count = f.listeners.input.length;
  vm.runInNewContext(source, { window: f.win, WeakMap, Date, Array });
  assert.equal(f.listeners.input.length, count);
  f.insert('12'); f.fire('focusout'); f.el.setSelectionRange(0, 0); f.fire('focusin'); f.insert('A');
  assert.equal(f.el.value, 'A12');
});
